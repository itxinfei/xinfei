package com.xinfei.module.platform.task;

import com.xinfei.common.RedisLockUtil;
import com.xinfei.module.platform.adapter.PlatformOrder;
import com.xinfei.module.platform.model.PlatformConfig;
import com.xinfei.module.platform.service.PlatformConfigService;
import com.xinfei.module.platform.service.PlatformSyncService;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PlatformRecoveryTask} 单元测试（O-14）：恢复拉单的时间窗格式与全链参数透传。
 * <p>回归守卫：原实现把 LocalDateTime.toString()（ISO 带 T/毫秒）拼进平台 API，
 * 与拉单链路要求的 yyyy-MM-dd HH:mm:ss 不一致导致恢复实际拉不到单。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
class PlatformRecoveryTaskTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    @DisplayName("1. 恢复窗口：[now-30min, now]，格式 yyyy-MM-dd HH:mm:ss（无 T/毫秒），两次取值同源不漂移")
    void testRecoveryWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 13, 30, 15);
        String[] window = PlatformRecoveryTask.recoveryWindow(now);
        assertEquals(2, window.length);
        assertEquals("2026-10-24 13:00:15", window[0], "起点应为 now 减 30 分钟");
        assertEquals("2026-10-24 13:30:15", window[1], "终点应为同一 now");
        // 回归守卫：绝不出现 ISO 格式的 T 与毫秒（原缺陷）
        assertFalse(window[0].contains("T"), "beginTime 不得为 ISO 带 T 格式");
        assertFalse(window[1].contains("T"), "endTime 不得为 ISO 带 T 格式");
        assertFalse(window[0].contains("."), "beginTime 不得带毫秒");
    }

    @Test
    @DisplayName("2. 全链：健康检查失败 → 以正确格式时间窗调 pullOrders")
    void testHealthCheckAndRecoverPullsWithFormattedWindow() {
        PlatformRecoveryTask task = new PlatformRecoveryTask();
        PlatformConfigService configService = mock(PlatformConfigService.class);
        PlatformSyncService syncService = mock(PlatformSyncService.class);
        TenantService tenantService = mock(TenantService.class);
        RedisLockUtil lockUtil = mock(RedisLockUtil.class);

        ReflectionTestUtils.setField(task, "platformConfigService", configService);
        ReflectionTestUtils.setField(task, "platformSyncService", syncService);
        ReflectionTestUtils.setField(task, "tenantService", tenantService);
        ReflectionTestUtils.setField(task, "redisLockUtil", lockUtil);
        ReflectionTestUtils.setField(task, "syncEnabled", true);

        Tenant tenant = new Tenant();
        tenant.setId(999L);
        when(tenantService.listActiveTenants()).thenReturn(List.of(tenant));

        PlatformConfig config = new PlatformConfig();
        config.setId(1L);
        config.setPlatformType("MEITUAN");
        config.setEnabled(1);
        when(configService.listEnabledConfigs()).thenReturn(List.of(config));
        when(syncService.checkHealth(config)).thenReturn(false);
        when(syncService.pullOrders(any(), anyString(), anyString()))
                .thenReturn(Collections.singletonList(new PlatformOrder()));

        // 锁 mock：直接执行任务体（真实实现是 SETNX+Lua，此处只验证业务链）
        AtomicReference<Object> executed = new AtomicReference<>();
        when(lockUtil.executeWithLock(anyString(), anyLong(), any(Runnable.class))).thenAnswer(inv -> {
            Runnable body = inv.getArgument(2);
            body.run();
            executed.set(Boolean.TRUE);
            return true;
        });

        task.healthCheckAndRecover();

        assertTrue(executed.get() != null, "锁体内任务应执行");
        ArgumentCaptor<String> begin = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> end = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(syncService).pullOrders(any(), begin.capture(), end.capture());
        assertNotNull(begin.getValue());
        // 透传给平台 API 的时间必须是拉单链路口径（可被 yyyy-MM-dd HH:mm:ss 解析）
        LocalDateTime beginTime = LocalDateTime.parse(begin.getValue(), FMT);
        LocalDateTime endTime = LocalDateTime.parse(end.getValue(), FMT);
        assertTrue(beginTime.isBefore(endTime), "起点必须早于终点");
        assertTrue(beginTime.plusMinutes(29).isBefore(endTime), "窗口应约 30 分钟");
        assertTrue(beginTime.plusMinutes(31).isAfter(endTime), "窗口应约 30 分钟（上界）");
    }

    @Test
    @DisplayName("3. 总开关关闭时不执行健康检查（不触锁、不拉单）")
    void testSyncDisabledSkips() {
        PlatformRecoveryTask task = new PlatformRecoveryTask();
        RedisLockUtil lockUtil = mock(RedisLockUtil.class);
        ReflectionTestUtils.setField(task, "redisLockUtil", lockUtil);
        ReflectionTestUtils.setField(task, "syncEnabled", false);

        task.healthCheckAndRecover();

        org.mockito.Mockito.verify(lockUtil, org.mockito.Mockito.never())
                .executeWithLock(anyString(), anyLong(), any(Runnable.class));
    }
}
