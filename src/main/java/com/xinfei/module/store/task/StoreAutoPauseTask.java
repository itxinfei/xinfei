package com.xinfei.module.store.task;

import com.xinfei.common.BaseContext;
import com.xinfei.common.RedisLockUtil;
import com.xinfei.module.store.model.StoreInfo;
import com.xinfei.module.store.service.BusinessHoursService;
import com.xinfei.module.store.service.StoreService;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

/**
 * 营业时段自动接单任务（O-23）
 * <p>
 * 依据 store_info.businessHours（HH:mm-HH:mm，支持跨午夜）自动翻转门店「暂停接单」挂牌：
 * 跨出营业时段自动暂停（pauseOrder=1）、跨入自动恢复（pauseOrder=0）。下单链路本就拒绝
 * 非营业时间订单（BusinessHoursServiceImpl 校验），本任务解决的是「门店挂牌状态」与真实
 * 可接单状态不一致——小程序/后台显示「休息中」而非让顾客下单后被拒。
 * </p>
 * <p>
 * 边沿触发设计：仅在「预期状态发生变迁」时写入（Redis 记录上次预期状态），
 * 不触碰时段内的手动暂停（店员白天手动暂停营业，任务不会擅自恢复）；
 * Redis 无上次记录时只自动暂停、不自动恢复（保守：防重启后误开手动暂停的店）。
 * 解析失败/未配置营业时间的门店跳过。
 * </p>
 * <p>
 * 多实例经 Redis 分布式锁保证单节点执行；BaseContext 按租户设置/恢复（租户插件注入）。
 * </p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Component
public class StoreAutoPauseTask {

    private static final long LOCK_TTL_SECONDS = 60L;
    private static final String EXPECTED_KEY_PREFIX = "store:auto-pause:expected:";

    @Autowired
    private TenantService tenantService;

    @Autowired
    private StoreService storeService;

    @Autowired
    private BusinessHoursService businessHoursService;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedDelay = 60000)
    public void autoToggleByBusinessHours() {
        redisLockUtil.executeWithLock("store:lock:auto-pause", LOCK_TTL_SECONDS, () -> {
            List<Tenant> tenants = tenantService.listActiveTenants();
            if (tenants == null || tenants.isEmpty()) {
                return;
            }
            LocalTime now = LocalTime.now();
            for (Tenant tenant : tenants) {
                Long originalTenantId = BaseContext.getCurrentTenantId();
                BaseContext.setCurrentTenantId(tenant.getId());
                try {
                    applyExpectedState(tenant.getId(), now);
                } catch (Exception e) {
                    log.error("[自动接单] 租户 {} 处理失败: {}", tenant.getId(), e.getMessage());
                } finally {
                    if (originalTenantId != null) {
                        BaseContext.setCurrentTenantId(originalTenantId);
                    } else {
                        BaseContext.remove();
                    }
                }
            }
        });
    }

    /**
     * 对单个租户应用预期挂牌状态（边沿触发）。
     */
    private void applyExpectedState(Long tenantId, LocalTime now) {
        StoreInfo store = storeService.findByTenantId(tenantId);
        if (store == null) {
            return;
        }
        String businessHours = store.getBusinessHours();
        Boolean inHours = isInBusinessHours(businessHours, now);
        if (inHours == null) {
            return; // 未配置/解析失败：不介入
        }
        boolean expectedPause = !inHours;
        String key = EXPECTED_KEY_PREFIX + tenantId;
        Object last = redisTemplate.opsForValue().get(key);
        String lastStr = last == null ? null : String.valueOf(last);
        if (lastStr != null && lastStr.equals(expectedPause ? "1" : "0")) {
            return; // 状态无变迁
        }
        if (lastStr == null && !expectedPause) {
            // 无历史记录且当前应营业：保守不自动恢复（防重启后误开手动暂停的店），仅记录预期
            redisTemplate.opsForValue().set(key, "0");
            return;
        }
        businessHoursService.togglePauseOrder(tenantId, expectedPause);
        redisTemplate.opsForValue().set(key, expectedPause ? "1" : "0");
        log.info("[自动接单] 营业时段边沿触发: tenantId={}, businessHours={}, 自动{}接单",
                tenantId, businessHours, expectedPause ? "暂停" : "恢复");
    }

    /**
     * 判断当前时间是否在营业时段内（包级静态纯函数，供单测）。
     *
     * @return true=在营业时段；false=不在；null=未配置或格式无法解析（不介入）
     */
    static Boolean isInBusinessHours(String businessHours, LocalTime now) {
        if (businessHours == null || businessHours.isBlank()) {
            return null;
        }
        String[] parts = businessHours.split("-");
        if (parts.length != 2) {
            return null;
        }
        try {
            LocalTime open = LocalTime.parse(parts[0].trim());
            LocalTime close = LocalTime.parse(parts[1].trim());
            if (open.isBefore(close)) {
                // 正常区间：09:00-22:00
                return !now.isBefore(open) && !now.isAfter(close);
            }
            // 跨午夜区间：22:00-06:00
            return !now.isBefore(open) || !now.isAfter(close);
        } catch (Exception e) {
            return null;
        }
    }
}
