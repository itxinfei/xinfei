package com.xinfei.module.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 非 cookie 会话通道（miniapp 前置改造 5.1）：以 {@code X-Auth-Token} 请求头承载登录态。
 * </p>
 * <p>
 * 背景：小程序没有浏览器 cookie 容器，{@code uni.request} 不回传 JSESSIONID，
 * 纯 Session 通道下除公开接口外一律 401。本服务在登录成功后颁发一次令牌：
 * 令牌 → 载荷（主体类型/ID/租户ID/角色）存 Redis，后续请求凭该头由
 * {@code LoginCheckFilter} 的 token 分支恢复上下文，与 Session 通道共存（会话优先，token 兜底）。
 * </p>
 * <p>安全设计：</p>
 * <ul>
 *   <li>令牌为 {@link SecureRandom} 256bit 的 Base64URL 串，不可预测、无业务含义；</li>
 *   <li>载荷不含手机号/密码等敏感字段，仅主体标识与租户上下文；</li>
 *   <li>Redis 降级安全（口径同 R-01）：颁发失败登录不受影响（仅不下发 token），
 *       校验失败回落 401，吊销失败仅告警；</li>
 *   <li>令牌随登出吊销，TTL 到期自动失效（默认 72h，{@code xinfei.auth-token.ttl-hours} 可配）。</li>
 * </ul>
 *
 * @author xinfei
 * @since 2026-10-22
 */
@Slf4j
@Service
public class AuthTokenService {

    /** 承载会话标识的请求/响应头名（与 miniapp packages/shared/src/config.js 的 AUTH_HEADER 对齐） */
    public static final String HEADER_NAME = "X-Auth-Token";

    /** 主体类型：员工 */
    public static final String TYPE_EMPLOYEE = "employee";
    /** 主体类型：C 端顾客 */
    public static final String TYPE_USER = "user";
    /** 主体类型：骑手 */
    public static final String TYPE_RIDER = "rider";

    private static final String REDIS_KEY_PREFIX = "auth:token:";
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, Object> redisTemplate;

    /** 令牌有效期（小时），默认 72h */
    private final long ttlHours;

    public AuthTokenService(RedisTemplate<String, Object> redisTemplate,
                            @Value("${xinfei.auth-token.ttl-hours:72}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttlHours = ttlHours;
    }

    /**
     * 令牌载荷：仅主体标识与租户上下文，不含敏感字段。
     *
     * @param type     主体类型（employee/user/rider）
     * @param id       主体 ID
     * @param tenantId 租户 ID（登录态完整性校验必需）
     * @param roleKey  员工角色标识（仅员工登录携带，供 AOP 权限拦截器使用）
     */
    public record AuthTokenPayload(String type, Long id, Long tenantId, String roleKey) {
    }

    /**
     * 登录成功后颁发令牌。
     * <p>Redis 写失败时返回 null（登录主流程不受影响，本次不下发 token，走原 Session 通道）。</p>
     *
     * @param type     主体类型，见 {@link #TYPE_EMPLOYEE} 等常量
     * @param id       主体 ID
     * @param tenantId 租户 ID
     * @param roleKey  员工角色标识，非员工传 null
     * @return 令牌明文（仅此一次可见，客户端自行保存）；颁发失败返回 null
     */
    public String issue(String type, Long id, Long tenantId, String roleKey) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("id", id);
        payload.put("tenantId", tenantId);
        if (roleKey != null && !roleKey.isEmpty()) {
            payload.put("roleKey", roleKey);
        }
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + token, payload, Duration.ofHours(ttlHours));
        } catch (DataAccessException e) {
            log.error("auth token 写 Redis 失败，本次登录不下发 token（Redis 降级）：type={}, id={}", type, id, e);
            return null;
        }
        log.info("颁发 auth token：type={}, id={}, tenantId={}, ttlHours={}", type, id, tenantId, ttlHours);
        return token;
    }

    /**
     * 校验令牌并返回载荷。
     * <p>令牌不存在/已过期/格式非法/Redis 不可用统一返回 null，由调用方回落 401。</p>
     *
     * @param token 请求头中的令牌明文
     * @return 载荷；无效返回 null
     */
    public AuthTokenPayload validate(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Object raw;
        try {
            raw = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + token);
        } catch (DataAccessException e) {
            log.warn("auth token 读 Redis 失败，按未登录处理（Redis 降级）：{}", e.getMessage());
            return null;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        String type = stringValue(map.get("type"));
        Long id = longValue(map.get("id"));
        Long tenantId = longValue(map.get("tenantId"));
        // 主体标识与租户上下文缺一不可（与 LoginCheckFilter 会话分支的完整性口径一致）
        if (type == null || id == null || tenantId == null) {
            log.warn("auth token 载荷不完整，按未登录处理：type={}, id={}, tenantId={}", type, id, tenantId);
            return null;
        }
        return new AuthTokenPayload(type, id, tenantId, stringValue(map.get("roleKey")));
    }

    /**
     * 吊销令牌（登出时调用）。Redis 不可用仅告警：令牌仍有 TTL 兜底，不会永久存续。
     *
     * @param token 请求头中的令牌明文，可为 null
     */
    public void revoke(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + token);
        } catch (DataAccessException e) {
            log.warn("auth token 吊销失败（Redis 降级，TTL 兜底）：{}", e.getMessage());
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /** JSON 往返后整型可能变为 Integer/Long，统一按 Number 取值 */
    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isEmpty()) {
            try {
                return Long.valueOf(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
