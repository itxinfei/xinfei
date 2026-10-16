package com.xinfei.module.delivery.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DeliveryMockSecurityCheck 启动防线单测（P0-3b）：
 * prod/production profile 与 delivery mock-mode=true 互斥（无 ALLOW_MOCK_IN_PROD 豁免即拒绝启动）；
 * 非 prod 或 mock 关闭时放行。
 *
 * @author xinfei
 * @since 2026-10-16
 */
class DeliveryMockSecurityCheckTest {

    private DeliveryMockSecurityCheck build(boolean mockMode, String... profiles) throws Exception {
        DeliveryMockSecurityCheck check = new DeliveryMockSecurityCheck();
        DeliveryPlatformConfigProperties props = new DeliveryPlatformConfigProperties();
        props.setMockMode(mockMode);
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(profiles);
        set(check, "deliveryConfig", props);
        set(check, "environment", env);
        return check;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void prodWithMockNoExemption_failsFast() throws Exception {
        Assumptions.assumeTrue(!"true".equalsIgnoreCase(System.getenv("ALLOW_MOCK_IN_PROD")),
                "测试机显式设置了 ALLOW_MOCK_IN_PROD=true，跳过");
        DeliveryMockSecurityCheck check = build(true, "prod");
        IllegalStateException e = assertThrows(IllegalStateException.class, check::validate);
        assertTrue(e.getMessage().contains("mock-mode=true 在 prod 环境禁止启用"));
    }

    @Test
    void devWithMock_passes() throws Exception {
        assertDoesNotThrow(build(true, "dev")::validate);
    }

    @Test
    void prodWithoutMock_passes() throws Exception {
        assertDoesNotThrow(build(false, "prod")::validate);
    }
}
