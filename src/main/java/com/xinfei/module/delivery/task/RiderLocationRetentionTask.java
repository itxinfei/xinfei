package com.xinfei.module.delivery.task;

import com.xinfei.common.BaseContext;
import com.xinfei.module.delivery.service.DeliveryTrackingService;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 骑手轨迹留存清理定时任务。
 *
 * <p>骑手端每 12 秒上报一次，每名在线骑手每天约 7200 个轨迹点写满 {@code rider_location_record}，
 * 而业务只需近期轨迹用于追踪展示——无清理则该表无界增长、索引随之膨胀。本任务每日凌晨按租户
 * 分批删除早于留存窗口的历史轨迹点，把表规模长期钉在「近 N 天」内。</p>
 *
 * <p>逐租户设置 {@link BaseContext} 上下文后调用服务清理，租户过滤由 MyBatis-Plus 租户插件自动注入；
 * 删除恒带 {@code record_time < cutoff} 条件（符合无界 DELETE 红线）。留存天数与开关走配置，
 * 默认保留 30 天（{@code xinfei.delivery.location-retention-days}），无需改代码即可调整。</p>
 *
 * @author xinfei
 * @since 2026-10-19
 */
@Slf4j
@Component
public class RiderLocationRetentionTask {

    @Autowired
    private DeliveryTrackingService deliveryTrackingService;

    @Autowired
    private TenantService tenantService;

    /** 轨迹留存天数，早于「今天 - N 天」的轨迹点删除。 */
    @Value("${xinfei.delivery.location-retention-days:30}")
    private int retentionDays;

    /** 总开关：false 时本任务直接跳过（如临时排障期不想删数据）。 */
    @Value("${xinfei.delivery.location-retention-enabled:true}")
    private boolean enabled;

    /**
     * 每日 03:15 执行一轮轨迹留存清理。
     */
    @Scheduled(cron = "0 15 3 * * ?")
    public void purgeExpiredLocationRecords() {
        if (!enabled) {
            log.debug("[轨迹留存] 清理开关关闭，跳过本轮");
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        try {
            List<Tenant> tenants = tenantService.list();
            if (tenants == null || tenants.isEmpty()) {
                return;
            }
            int totalPurged = 0;
            for (Tenant tenant : tenants) {
                try {
                    BaseContext.setCurrentTenantId(tenant.getId());
                    int purged = deliveryTrackingService.cleanupLocationRecordsBefore(cutoff);
                    totalPurged += purged;
                    if (purged > 0) {
                        log.info("[轨迹留存] 租户 {} 清理 {} 条早于 {} 的轨迹点",
                                tenant.getId(), purged, cutoff);
                    }
                } catch (Exception e) {
                    log.error("[轨迹留存] 租户 {} 清理异常: {}", tenant.getId(), e.getMessage(), e);
                } finally {
                    BaseContext.remove();
                }
            }
            if (totalPurged > 0) {
                log.info("[轨迹留存] 本轮共清理 {} 条过期轨迹点（留存窗口 {} 天）", totalPurged, retentionDays);
            }
        } catch (Exception e) {
            log.error("[轨迹留存] 定时任务执行异常: {}", e.getMessage(), e);
        }
    }
}
