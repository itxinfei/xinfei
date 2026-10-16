package com.xinfei.module.payment.task;

import com.xinfei.common.BaseContext;
import com.xinfei.module.payment.channel.PayResponse;
import com.xinfei.module.payment.channel.PaymentChannel;
import com.xinfei.module.payment.channel.PaymentChannelFactory;
import com.xinfei.module.payment.config.PaymentConfigProperties;
import com.xinfei.module.payment.model.PaymentOrder;
import com.xinfei.module.payment.service.PaymentOrderService;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 掉单补偿定时任务（P0 资金安全）：主动查单找回"渠道已扣款、本地仍 PENDING"的支付。
 * <p>
 * 场景：渠道异步回调永久丢失（网络抖动/服务重启窗口/回调地址故障）时，用户钱已扣但
 * 订单停留在待付款，30 分钟后被 {@link com.xinfei.schedule.task.OrderTimeoutTask} 取消——
 * 若本任务不先查出真相，这笔掉单在系统内永远不可见，只能靠客诉人工核对渠道后台。
 * </p>
 *
 * <p>策略（保守，只补成功、不判失败）：
 * <ul>
 *   <li>每 5 分钟扫描创建 3 分钟~24 小时内、仍为 PENDING 或 CLOSED 的支付单（3 分钟宽限期避免用户支付中途误查，
 *       24 小时外渠道预支付单已过期不再查；CLOSED 覆盖"取消联动关闭后迟到支付且回调又丢失"的双故障场景）</li>
 *   <li>渠道查单返回已支付 → 走 {@code handlePaymentSuccess} 补联动：
 *       订单待付款则 1→2；订单已被超时取消则其内部自动退款兜底（幂等 CAS，与迟到回调互不冲突）</li>
 *   <li>渠道未支付/已关闭/查询异常 → 不动本地状态，等下轮或超时取消链路处理</li>
 *   <li>mock-mode 跳过：模拟渠道无真实资金，不存在掉单</li>
 *   <li>多租户遍历 + Redis 分布式锁 fail-closed（锁不可用跳过本轮，与 RefundReconcileTask 同规）</li>
 * </ul>
 * </p>
 *
 * @author xinfei
 * @since 2026-10-16
 */
@Slf4j
@Component
public class PaymentReconcileTask {

    /** 分布式锁 Key */
    private static final String LOCK_KEY = "schedule:lock:payment-reconcile";
    /** 分布式锁 TTL：5 分钟，防止任务卡死导致锁永久持有 */
    private static final long LOCK_TTL_MS = 5 * 60 * 1000L;
    /** 执行间隔：5 分钟 */
    private static final long INTERVAL_MS = 5 * 60 * 1000L;
    /** 支付单创建后至少等待多久才查渠道（给用户正常支付留窗口，避免中途查询噪音） */
    private static final long GRACE_MINUTES = 3;
    /** 超过此年龄的 PENDING 支付单不再查（渠道预支付单已过期，不会再出现"迟到支付成功"以外的结果） */
    private static final long MAX_AGE_HOURS = 24;
    /** 单租户单轮最大查单条数（防止积压时打爆渠道限流） */
    private static final int BATCH_LIMIT = 50;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private PaymentChannelFactory paymentChannelFactory;

    @Autowired
    private PaymentConfigProperties paymentConfigProperties;

    @Autowired
    private TenantService tenantService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 扫描 PENDING 支付单并向渠道主动查单补偿。
     */
    @Scheduled(fixedDelay = INTERVAL_MS)
    public void scanPendingPayments() {
        if (paymentConfigProperties.isMockMode()) {
            log.debug("[掉单补偿] mock-mode 无真实资金，跳过");
            return;
        }
        String lockValue = tryLock();
        if (lockValue == null) {
            log.debug("[掉单补偿] 实例锁获取失败，跳过本次执行");
            return;
        }
        try {
            List<Tenant> tenants = tenantService.listActiveTenants();
            if (tenants == null || tenants.isEmpty()) {
                return;
            }
            for (Tenant tenant : tenants) {
                Long originalTenantId = BaseContext.getCurrentTenantId();
                BaseContext.setCurrentTenantId(tenant.getId());
                try {
                    int compensated = scanForTenant(tenant.getId());
                    if (compensated > 0) {
                        log.warn("[掉单补偿] 租户 {} 本轮补偿支付成功 {} 笔", tenant.getId(), compensated);
                    }
                } catch (Exception e) {
                    // 宽异常兜底：有意捕获 Exception，避免单个租户失败影响主流程
                    log.error("[掉单补偿] 租户 {} 扫描失败: {}", tenant.getId(), e.getMessage(), e);
                } finally {
                    if (originalTenantId != null) {
                        BaseContext.setCurrentTenantId(originalTenantId);
                    } else {
                        BaseContext.remove();
                    }
                }
            }
        } finally {
            unlock(lockValue);
        }
    }

    /**
     * 当前租户查单补偿。
     *
     * @param tenantId 租户ID（渠道构建用，不依赖 BaseContext）
     * @return 本轮补偿成功笔数
     */
    private int scanForTenant(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        // P1-7：CLOSED 一并扫描——业务单取消联动关闭支付单后，若用户在关闭前已在渠道端付款
        // 且迟到回调也丢失（双故障），仍靠主动查单找回，handlePaymentSuccess 的
        // CLOSED→SUCCESS 挽救路径落地后走已取消订单自动退款。
        List<PaymentOrder> pendings = paymentOrderService.lambdaQuery()
                .in(PaymentOrder::getStatus, PaymentOrder.STATUS_PENDING, PaymentOrder.STATUS_CLOSED)
                .le(PaymentOrder::getCreatedTime, now.minusMinutes(GRACE_MINUTES))
                .ge(PaymentOrder::getCreatedTime, now.minusHours(MAX_AGE_HOURS))
                .orderByAsc(PaymentOrder::getCreatedTime)
                .last("limit " + BATCH_LIMIT)
                .list();
        if (pendings == null || pendings.isEmpty()) {
            return 0;
        }
        int compensated = 0;
        for (PaymentOrder po : pendings) {
            try {
                PaymentChannel channel = paymentChannelFactory.getChannelForTenantNullable(tenantId, po.getChannel());
                if (channel == null) {
                    // 租户未配置/未启用该渠道：无法查单，保持 PENDING 并告警（配置修复后下轮自动恢复）
                    log.warn("[掉单补偿] 渠道不可用跳过查单: tenantId={}, tradeNo={}, channel={}",
                            tenantId, po.getTradeNo(), po.getChannel());
                    continue;
                }
                PayResponse result = channel.queryOrder(po.getTradeNo());
                if (result != null && result.isSuccess()) {
                    log.warn("[掉单补偿] 渠道已支付但本地仍 PENDING/CLOSED，补偿支付成功联动: tradeNo={}, amount={}, "
                                    + "channelTradeNo={}, createdTime={}",
                            po.getTradeNo(), po.getAmount(), result.getChannelTradeNo(), po.getCreatedTime());
                    // 幂等 CAS：与真实/伪造回调并发安全；订单已取消时其内部自动退款兜底
                    paymentOrderService.handlePaymentSuccess(po.getTradeNo(), result.getChannelTradeNo());
                    compensated++;
                }
                // 未支付/已关闭/查询失败：保守不动本地状态，等待用户支付完成回调或超时取消链路
            } catch (Exception e) {
                // 宽异常兜底：有意捕获 Exception，避免单笔查单失败（渠道网络异常等）影响整批
                log.error("[掉单补偿] 单笔查单失败: tenantId={}, tradeNo={}, err={}",
                        tenantId, po.getTradeNo(), e.getMessage(), e);
            }
        }
        return compensated;
    }

    private String tryLock() {
        if (redisTemplate == null) {
            log.warn("[掉单补偿] Redis 不可用，跳过本次执行（分布式锁获取失败）");
            return null;
        }
        try {
            String lockValue = UUID.randomUUID().toString();
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, lockValue, LOCK_TTL_MS, TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(ok) ? lockValue : null;
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("[掉单补偿] 获取分布式锁失败，跳过本次执行: {}", e.getMessage());
            return null;
        }
    }

    private void unlock(String lockValue) {
        if (redisTemplate == null || lockValue == null) {
            return;
        }
        try {
            String lua =
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(lua, Long.class),
                    Collections.singletonList(LOCK_KEY),
                    lockValue);
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("[掉单补偿] 释放分布式锁失败: {}", e.getMessage());
        }
    }
}
