package com.xinfei.module.export.task;

import com.xinfei.common.BaseContext;
import com.xinfei.common.RedisLockUtil;
import com.xinfei.module.export.service.ExportTaskService;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 异步导出任务清扫（O-25）
 * <p>两个触发点共用同一把锁与同一遍历口径：</p>
 * <ul>
 *   <li>应用就绪时：恢复重启前卡死的 PENDING/RUNNING 任务（线程已随重启丢失）；</li>
 *   <li>每日 03:30：清扫卡死任务 + 超保留期（7 天）的终态任务文件。</li>
 * </ul>
 * <p>多实例经 Redis 分布式锁单节点执行；逐租户设置 BaseContext（租户插件注入），用后恢复。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Component
public class ExportTaskCleanupTask {

    private static final long LOCK_TTL_SECONDS = 120L;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ExportTaskService exportTaskService;

    @Autowired
    private RedisLockUtil redisLockUtil;

    /** 应用就绪即清扫一次（崩溃恢复） */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        sweepWithLock("启动恢复");
    }

    /** 每日 03:30 清扫 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void sweepDaily() {
        sweepWithLock("每日清扫");
    }

    private void sweepWithLock(String scene) {
        redisLockUtil.executeWithLock("export:lock:task-sweep", LOCK_TTL_SECONDS, () -> {
            List<Tenant> tenants = tenantService.listActiveTenants();
            if (tenants == null || tenants.isEmpty()) {
                return;
            }
            for (Tenant tenant : tenants) {
                Long originalTenantId = BaseContext.getCurrentTenantId();
                BaseContext.setCurrentTenantId(tenant.getId());
                try {
                    exportTaskService.sweepStaleTasks();
                } catch (Exception e) {
                    log.error("[导出任务] {} 租户 {} 失败: {}", scene, tenant.getId(), e.getMessage());
                } finally {
                    if (originalTenantId != null) {
                        BaseContext.setCurrentTenantId(originalTenantId);
                    } else {
                        BaseContext.remove();
                    }
                }
            }
            log.info("[导出任务] {}完成，遍历租户数={}", scene, tenants.size());
        });
    }
}
