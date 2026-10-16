package com.xinfei.module.auth.service;

import com.xinfei.common.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoginAttemptService 单测（P0-5）：锁定阈值、成功清零、Redis 正常走原子脚本、
 * Redis 异常降级本地计数仍锁定（fail-safe 而非 fail-open）。
 *
 * @author xinfei
 * @since 2026-10-16
 */
class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
        service.init();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void belowThreshold_notLocked() {
        service.recordFailure(LoginAttemptService.Scope.EMP, "999:alice");
        assertFalse(service.isLocked(LoginAttemptService.Scope.EMP, "999:alice"));
    }

    @Test
    void recordFailureWithoutRedis_locksAfterMaxAttemptsLocally() {
        // redisTemplate == null：降级本地计数，仍须在 MAX 次后锁定（fail-safe，非 fail-open）
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_FAIL_COUNT; i++) {
            service.recordFailure(LoginAttemptService.Scope.RIDER, "13800000000");
        }
        assertTrue(service.isLocked(LoginAttemptService.Scope.RIDER, "13800000000"));
    }

    @Test
    void clearOnSuccess_resetsLocalCount() {
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_FAIL_COUNT; i++) {
            service.recordFailure(LoginAttemptService.Scope.EMP, "999:bob");
        }
        assertTrue(service.isLocked(LoginAttemptService.Scope.EMP, "999:bob"));
        service.clearOnSuccess(LoginAttemptService.Scope.EMP, "999:bob");
        assertFalse(service.isLocked(LoginAttemptService.Scope.EMP, "999:bob"));
    }

    @Test
    void scopeIsolation_empAndRiderDoNotShareCount() {
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_FAIL_COUNT; i++) {
            service.recordFailure(LoginAttemptService.Scope.EMP, "same-identity");
        }
        assertTrue(service.isLocked(LoginAttemptService.Scope.EMP, "same-identity"));
        assertFalse(service.isLocked(LoginAttemptService.Scope.RIDER, "same-identity"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisPath_usesAtomicScriptAndDelete() throws Exception {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(3L);
        when(valueOps.get(any())).thenReturn(3);
        setField(service, "redisTemplate", redisTemplate);

        String key = LoginAttemptService.buildKey(LoginAttemptService.Scope.EMP, "999:dave");
        service.recordFailure(LoginAttemptService.Scope.EMP, "999:dave");
        verify(redisTemplate).execute(any(RedisScript.class), eq(Collections.singletonList(key)), any());

        assertFalse(service.isLocked(LoginAttemptService.Scope.EMP, "999:dave"));
        when(valueOps.get(any())).thenReturn(SecurityConstants.MAX_LOGIN_FAIL_COUNT);
        assertTrue(service.isLocked(LoginAttemptService.Scope.EMP, "999:dave"));

        service.clearOnSuccess(LoginAttemptService.Scope.EMP, "999:dave");
        verify(redisTemplate).delete(key);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisWriteThrows_fallsBackToLocalCounting_stillLocks() throws Exception {
        // 关键安全属性：Redis 写入异常不能 fail-open 放行爆破，须降级本地计数仍能锁定
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("mock redis down"));
        when(valueOps.get(any())).thenReturn(null);
        setField(service, "redisTemplate", redisTemplate);

        for (int i = 0; i < SecurityConstants.MAX_LOGIN_FAIL_COUNT; i++) {
            service.recordFailure(LoginAttemptService.Scope.RIDER, "13800000001");
        }
        assertTrue(service.isLocked(LoginAttemptService.Scope.RIDER, "13800000001"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankIdentity_isNeverLockedAndNeverCounted() throws Exception {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        setField(service, "redisTemplate", redisTemplate);

        service.recordFailure(LoginAttemptService.Scope.EMP, null);
        assertFalse(service.isLocked(LoginAttemptService.Scope.EMP, null));
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }
}
