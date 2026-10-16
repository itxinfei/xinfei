package com.xinfei.module.payment.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinfei.common.BaseContext;
import com.xinfei.enums.RefundStatus;
import com.xinfei.module.payment.channel.PaymentChannel;
import com.xinfei.module.payment.channel.PaymentChannelFactory;
import com.xinfei.module.payment.channel.RefundQueryCapable;
import com.xinfei.module.payment.channel.notify.RefundNotifyResult;
import com.xinfei.module.payment.config.PaymentConfigProperties;
import com.xinfei.module.payment.mapper.RefundRecordMapper;
import com.xinfei.module.payment.model.PaymentOrder;
import com.xinfei.module.payment.model.RefundRecord;
import com.xinfei.module.payment.service.PaymentOrderService;
import com.xinfei.module.payment.service.impl.RefundCallbackService;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 退款对账兜底定时任务
 * <p>
 * 场景一（[对账待办] 痕迹告警）：渠道退款成功但本地落库失败、或自动退款发起前异常
 * （RefundServiceImpl 降级写入独立事务的 PENDING 痕迹），需人工核对渠道后台后处理。
 * <p>
 * 场景二（PROCESSING 退款查单，P1-6）：微信退款受理返回 PROCESSING（钱在路上），
 * 终态本由退款异步通知确定；通知永久丢失时记录会无限停留在 PROCESSING，阻塞订单
 * 终态与剩余可退额判断。本任务主动向渠道查单，终态复用退款回调链路
 * （{@link RefundCallbackService#handleRefundNotify}，CAS 幂等，与迟到通知互不冲突）。
 * </p>
 *
 * <p>扫描策略：
 * <ul>
 *   <li>每 10 分钟执行一次（fixedDelay），两个场景各自独立分布式锁，fail-closed（锁获取失败跳过）</li>
 *   <li>[对账待办] 痕迹：不设年龄上限（P1-6 放开 72h 窗口——资金待办绝不静默过期），
 *       按创建时间从旧到新告警，单租户单轮限量</li>
 *   <li>PROCESSING 退款：只查创建 5 分钟~7 天内的记录（微信退款应在数日内终态化），
 *       超窗仍 PROCESSING 的按租户计数 error 告警转人工</li>
 *   <li>mock-mode 跳过查单：模拟渠道无真实资金，不存在 PROCESSING 悬置</li>
 *   <li>多租户遍历 + BaseContext 切换（finally 还原），与 PaymentReconcileTask 同规</li>
 * </ul>
 * </p>
 *
 * @author xinfei
 * @since 2026-09-03
 */
@Slf4j
@Component
public class RefundReconcileTask {

    /** [对账待办] 痕迹扫描分布式锁 Key */
    private static final String LOCK_KEY = "schedule:lock:refund-reconcile";
    /** PROCESSING 退款查单分布式锁 Key（与痕迹扫描互不阻塞） */
    private static final String PROCESS_LOCK_KEY = "schedule:lock:refund-processing";
    /** 分布式锁 TTL：10 分钟，防止任务卡死导致锁永久持有 */
    private static final long LOCK_TTL_MS = 10 * 60 * 1000L;
    /** 执行间隔：10 分钟 */
    private static final long INTERVAL_MS = 10 * 60 * 1000L;
    /** 单笔任务最大告警条数（防止数据量过大打爆日志） */
    private static final int MAX_ALERMS_PER_RUN = 100;
    /** PROCESSING 退款查单宽限期（分钟）：给正常退款回调留窗口，避免中途查询噪音 */
    private static final long PROCESS_GRACE_MINUTES = 5;
    /** PROCESSING 退款最大查单年龄（天）：微信退款正常数日内终态化，超窗转人工 */
    private static final long PROCESS_MAX_AGE_DAYS = 7;
    /** 单租户单轮 PROCESSING 查单上限（防止积压时打爆渠道限流） */
    private static final int PROCESS_BATCH_LIMIT = 50;
    /** 渠道退款终态（可定局）：成功 / 关闭 / 异常 */
    private static final List<String> TERMINAL_STATUSES = Arrays.asList("SUCCESS", "CLOSED", "ABNORMAL");

    @Autowired
    private RefundRecordMapper refundRecordMapper;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private PaymentChannelFactory paymentChannelFactory;

    @Autowired
    private PaymentConfigProperties paymentConfigProperties;

    @Autowired
    private RefundCallbackService refundCallbackService;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 场景一：扫描 "[对账待办]" 痕迹并告警（P1-6：放开 72h 窗口，资金待办不静默过期）。
     */
    @Scheduled(fixedDelay = INTERVAL_MS)
    public void scanReconcileTraces() {
        String lockValue = tryLock(LOCK_KEY);
        if (lockValue == null) {
            log.debug("[退款对账] 实例锁获取失败，跳过本次执行");
            return;
        }
        try {
            List<Tenant> tenants = tenantService.listActiveTenants();
            if (tenants == null || tenants.isEmpty()) {
                log.debug("[退款对账] 无活跃租户，跳过");
                return;
            }
            int total = 0;
            for (Tenant tenant : tenants) {
                Long originalTenantId = BaseContext.getCurrentTenantId();
                BaseContext.setCurrentTenantId(tenant.getId());
                try {
                    total += scanReconcileTracesForTenant();
                } catch (Exception e) {
                    // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
                    log.error("[退款对账] 租户 {} 扫描失败: {}", tenant.getId(), e.getMessage(), e);
                } finally {
                    if (originalTenantId != null) {
                        BaseContext.setCurrentTenantId(originalTenantId);
                    } else {
                        BaseContext.remove();
                    }
                }
            }
            if (total > 0) {
                log.warn("[退款对账] 租户扫描完成，共 {} 个租户，告警 {} 条", tenants.size(), total);
            }
        } finally {
            unlock(LOCK_KEY, lockValue);
        }
    }

    /**
     * 场景二：PROCESSING 退款主动查单定终态（P1-6，掉单同款问题在退款侧的兜底）。
     */
    @Scheduled(fixedDelay = INTERVAL_MS)
    public void scanProcessingRefunds() {
        if (paymentConfigProperties.isMockMode()) {
            log.debug("[退款查单] mock-mode 无真实资金，跳过 PROCESSING 退款查单");
            return;
        }
        String lockValue = tryLock(PROCESS_LOCK_KEY);
        if (lockValue == null) {
            log.debug("[退款查单] 实例锁获取失败，跳过本次执行");
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
                    finalizeProcessingRefundsForTenant(tenant.getId());
                } catch (Exception e) {
                    // 宽异常兜底：有意捕获 Exception，避免单个租户失败影响主流程
                    log.error("[退款查单] 租户 {} 扫描失败: {}", tenant.getId(), e.getMessage(), e);
                } finally {
                    if (originalTenantId != null) {
                        BaseContext.setCurrentTenantId(originalTenantId);
                    } else {
                        BaseContext.remove();
                    }
                }
            }
        } finally {
            unlock(PROCESS_LOCK_KEY, lockValue);
        }
    }

    /**
     * 扫描当前租户下 "[对账待办]" 痕迹并告警。
     * <p>不设年龄上限：痕迹在人工处理（状态离开 PENDING）前每轮持续告警，
     * 最旧的优先出现在限量窗口内。</p>
     *
     * @return 本轮告警条数
     */
    private int scanReconcileTracesForTenant() {
        List<RefundRecord> traces = refundRecordMapper.selectList(new LambdaQueryWrapper<RefundRecord>()
                .likeRight(RefundRecord::getReason, "[对账待办]")
                .eq(RefundRecord::getStatus, RefundStatus.PENDING.getCode())
                .orderByAsc(RefundRecord::getCreatedTime)
                .last("limit " + MAX_ALERMS_PER_RUN));
        if (traces == null || traces.isEmpty()) {
            return 0;
        }
        int count = traces.size();
        for (RefundRecord trace : traces) {
            log.warn("[退款对账] 待人工核对: tenantId={}, traceId={}, refundNo={}, amount={}, reason={}, createdTime={}",
                    trace.getTenantId(), trace.getId(), trace.getRefundNo(),
                    trace.getAmount(), trace.getReason(), trace.getCreatedTime());
        }
        return count;
    }

    /**
     * 当前租户 PROCESSING 退款查单定终态 + 超窗告警。
     *
     * @param tenantId 租户ID（渠道构建用，不依赖 BaseContext）
     */
    private void finalizeProcessingRefundsForTenant(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        List<RefundRecord> processing = refundRecordMapper.selectList(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getStatus, RefundStatus.PROCESSING.getCode())
                        .le(RefundRecord::getCreatedTime, now.minusMinutes(PROCESS_GRACE_MINUTES))
                        .ge(RefundRecord::getCreatedTime, now.minusDays(PROCESS_MAX_AGE_DAYS))
                        .orderByAsc(RefundRecord::getCreatedTime)
                        .last("limit " + PROCESS_BATCH_LIMIT));
        if (processing != null) {
            for (RefundRecord record : processing) {
                try {
                    finalizeOneProcessingRefund(tenantId, record);
                } catch (Exception e) {
                    // 宽异常兜底：有意捕获 Exception，避免单笔查单失败（渠道网络异常等）影响整批
                    log.error("[退款查单] 单笔查单失败: tenantId={}, refundNo={}, err={}",
                            tenantId, record.getRefundNo(), e.getMessage(), e);
                }
            }
        }
        // 超窗仍 PROCESSING：渠道查不到/永不终态，error 告警转人工（绝不静默悬挂）
        Long stale = refundRecordMapper.selectCount(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getStatus, RefundStatus.PROCESSING.getCode())
                .lt(RefundRecord::getCreatedTime, now.minusDays(PROCESS_MAX_AGE_DAYS)));
        if (stale != null && stale > 0) {
            log.error("[退款查单] 存在 {} 条超过 {} 天仍 PROCESSING 的退款记录（租户 {}），"
                    + "需人工核对渠道后台定局: tenantId={}", stale, PROCESS_MAX_AGE_DAYS, tenantId, tenantId);
        }
    }

    /**
     * 单笔 PROCESSING 退款查单：渠道返回终态则复用退款回调链路定局（CAS 幂等）。
     */
    private void finalizeOneProcessingRefund(Long tenantId, RefundRecord record) {
        PaymentOrder paymentOrder = paymentOrderService.getById(record.getPaymentOrderId());
        if (paymentOrder == null || paymentOrder.getChannel() == null) {
            log.warn("[退款查单] 支付单缺失无法定位渠道，跳过等人工: refundNo={}, paymentOrderId={}",
                    record.getRefundNo(), record.getPaymentOrderId());
            return;
        }
        PaymentChannel channel = paymentChannelFactory.getChannelForTenantNullable(
                tenantId, paymentOrder.getChannel());
        if (!(channel instanceof RefundQueryCapable)) {
            log.warn("[退款查单] 渠道不支持退款查单，保持 PROCESSING 等人工: refundNo={}, channel={}",
                    record.getRefundNo(), paymentOrder.getChannel());
            return;
        }
        RefundNotifyResult query = ((RefundQueryCapable) channel)
                .queryRefundStatus(record.getRefundNo());
        if (query == null || !query.isSuccess()) {
            log.warn("[退款查单] 渠道查单失败，下轮重试: refundNo={}, err={}",
                    record.getRefundNo(), query == null ? "null" : query.getErrorMsg());
            return;
        }
        if (!TERMINAL_STATUSES.contains(query.getStatus())) {
            // 仍 PROCESSING：钱在路上，保守不动，等下轮或渠道通知
            log.debug("[退款查单] 渠道仍处理中: refundNo={}, status={}", record.getRefundNo(), query.getStatus());
            return;
        }
        // 终态定局：与退款回调同链路（markProcessingToSuccess/Fail CAS + 全额联动），迟到通知幂等跳过
        boolean handled = refundCallbackService.handleRefundNotify(query);
        log.warn("[退款查单] PROCESSING 退款查得渠道终态并定局: refundNo={}, status={}, handled={}",
                record.getRefundNo(), query.getStatus(), handled);
    }

    private String tryLock(String lockKey) {
        if (redisTemplate == null) {
            log.warn("[退款对账] Redis 不可用，跳过本次执行（分布式锁获取失败）");
            return null;
        }
        try {
            String lockValue = UUID.randomUUID().toString();
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, LOCK_TTL_MS, TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(ok) ? lockValue : null;
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("[退款对账] 获取分布式锁失败，跳过本次执行: {}", e.getMessage());
            return null;
        }
    }

    private void unlock(String lockKey, String lockValue) {
        if (redisTemplate == null || lockValue == null) {
            return;
        }
        try {
            String lua =
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(lua, Long.class),
                    Collections.singletonList(lockKey),
                    lockValue);
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("[退款对账] 释放分布式锁失败: {}", e.getMessage());
        }
    }
}
