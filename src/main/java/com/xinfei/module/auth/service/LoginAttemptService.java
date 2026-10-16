package com.xinfei.module.auth.service;

import com.xinfei.common.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数记录与账号锁定检查（P0-5：防暴力破解）。
 * <p>
 * 背景：员工端 / 骑手端登录接口仅有 {@code @RateLimit}（按 IP 限流，防瞬时高频请求），
 * 但攻击者可以低频慢速针对单个账号无限重试密码，按 IP 限流不生效。本服务按「账号身份」
 * （而非 IP）累计连续失败次数，达到 {@link SecurityConstants#MAX_LOGIN_FAIL_COUNT} 后
 * 锁定 {@link SecurityConstants#LOGIN_LOCK_DURATION} 分钟；登录入口应先查锁定、再查库校验密码，
 * 密码错误 / 账号不存在时记一次失败，登录成功清零。
 * </p>
 *
 * <p><b>计数窗口</b>：固定窗口（自该身份第一次失败起算），复用与
 * {@link com.xinfei.common.RateLimitAspect} 同一份 {@code scripts/rate_limit.lua}
 * 原子 INCR + 首次 EXPIRE 脚本，避免并发下丢失计数。窗口结束 key 自动过期，
 * 下一段失败重新从 1 计数；不存在「攻击者持续重试反而把锁定期无限延长」的问题
 * （锁定期间即使再次失败也不会刷新 TTL，因为是首次 EXPIRE 语义）。</p>
 *
 * <p><b>Redis 不可用</b>：与 {@code RateLimitAspect} / {@code VerifyCodeUtils} 一致，
 * 降级为进程内本地计数（{@link #localAttempts}），保证「Redis 故障期间仍能防爆破」，
 * 绝不因基础设施抖动 fail-open 放行爆破。本地计数是单实例近似值（多实例部署时各实例独立计数，
 * 精度低于 Redis 全局计数），仅作兜底；{@link #cleanExpiredLocalAttempts()} 定时清理过期条目
 * 避免内存泄漏。</p>
 *
 * @author xinfei
 * @since 2026-10-16
 */
@Slf4j
@Service
public class LoginAttemptService {

    /** 失败计数 key 前缀（Redis / 本地降级 map 共用同一份 key，保证两条路径可互相清除） */
    private static final String KEY_PREFIX = "login:fail:";

    /** 锁定窗口秒数（与 SecurityConstants.LOGIN_LOCK_DURATION 分钟同步） */
    private static final int LOCK_WINDOW_SECONDS = SecurityConstants.LOGIN_LOCK_DURATION * 60;

    /**
     * Redis 操作模板，可选依赖：未配置 Redis 时为 null，自动降级本地计数。
     */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 原子 INCR + 首次 EXPIRE 脚本，与限流切面复用同一份通用计数脚本。
     */
    private DefaultRedisScript<Long> counterScript;

    /**
     * 本地降级计数窗口（fail-safe）：key = 与 Redis 相同格式的计数 key。
     */
    private final ConcurrentHashMap<String, LocalAttempt> localAttempts = new ConcurrentHashMap<>();

    /**
     * 本地计数条目：首次失败时间戳（用于固定窗口过期判定）+ 累计失败次数。
     */
    private static class LocalAttempt {
        long count;
        long firstFailMillis;
    }

    /**
     * 锁定范围：不同登录入口共用本服务，用 scope 隔离各自的计数命名空间，
     * 避免「员工用户名恰好与某骑手手机号字符串相同」之类的跨入口串扰。
     */
    public enum Scope {
        /** 员工端登录，identity = 租户ID:用户名 */
        EMP,
        /** 骑手端登录，identity = 手机号（全局唯一，无需租户前缀） */
        RIDER
    }

    @PostConstruct
    public void init() {
        counterScript = new DefaultRedisScript<>();
        counterScript.setScriptSource(new ResourceScriptSource(
                new org.springframework.core.io.ClassPathResource("scripts/rate_limit.lua")));
        counterScript.setResultType(Long.class);
    }

    /**
     * 是否已因连续登录失败被锁定。应在查库校验密码<b>之前</b>调用。
     *
     * @param scope    登录入口
     * @param identity 身份标识（由调用方归一化后传入，见 {@link #buildKey}）
     * @return true=已锁定，调用方应直接拒绝本次登录并提示稍后重试；false=未锁定
     */
    public boolean isLocked(Scope scope, String identity) {
        if (isBlank(identity)) {
            return false;
        }
        return currentCount(buildKey(scope, identity)) >= SecurityConstants.MAX_LOGIN_FAIL_COUNT;
    }

    /**
     * 记录一次登录失败（账号不存在、密码错误均调用；账号被禁用不调用，见调用方注释）。
     *
     * @param scope    登录入口
     * @param identity 身份标识
     */
    public void recordFailure(Scope scope, String identity) {
        if (isBlank(identity)) {
            // 身份为空（如用户名未填写）不归属任何账号，共享计数没有意义，
            // 还可能被当成"所有空用户名共用一个锁定桶"来针对性干扰，直接跳过
            return;
        }
        String key = buildKey(scope, identity);
        long count = increment(key);
        if (count >= SecurityConstants.MAX_LOGIN_FAIL_COUNT) {
            log.warn("[登录保护] 连续登录失败 {} 次，已锁定 {} 分钟 - scope={}",
                    count, SecurityConstants.LOGIN_LOCK_DURATION, scope);
        }
    }

    /**
     * 登录成功后清除失败计数，解除历史累计。
     *
     * @param scope    登录入口
     * @param identity 身份标识
     */
    public void clearOnSuccess(Scope scope, String identity) {
        if (isBlank(identity)) {
            return;
        }
        String key = buildKey(scope, identity);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                // 宽异常兜底：有意捕获 Exception，Redis 删除失败不应影响已成功的登录响应
                log.warn("[登录保护] 清除 Redis 失败计数异常，降级本地清除: key={}, error={}", key, e.getMessage(), e);
            }
        }
        localAttempts.remove(key);
    }

    /**
     * 组合身份标识，供调用方与 {@link #isLocked}/{@link #recordFailure}/{@link #clearOnSuccess}
     * 使用同一份 key 语义。
     *
     * @param scope    登录入口
     * @param identity 归一化后的身份标识（调用方负责去空白 / 转小写等归一化）
     * @return 计数 key（Redis / 本地降级 map 共用）
     */
    public static String buildKey(Scope scope, String identity) {
        return KEY_PREFIX + scope.name() + ":" + (identity == null ? "" : identity);
    }

    private long currentCount(String key) {
        long redisCount = 0L;
        if (redisTemplate != null) {
            try {
                Object raw = redisTemplate.opsForValue().get(key);
                redisCount = toCount(raw);
            } catch (Exception e) {
                // 宽异常兜底：有意捕获 Exception，读取失败降级本地计数，不阻断本次登录判定
                log.warn("[登录保护] 读取 Redis 失败计数异常，降级本地计数: key={}, error={}", key, e.getMessage(), e);
            }
        }
        // 取 Redis 与本地降级的较大值：recordFailure 可能因 Redis 写入异常只落在本地计数，
        // 而本次读取走 Redis 分支成功返回 0/null——若只看一侧会漏判锁定。
        return Math.max(redisCount, localCount(key));
    }

    private long localCount(String key) {
        LocalAttempt attempt = localAttempts.get(key);
        if (attempt == null) {
            return 0L;
        }
        synchronized (attempt) {
            if (isWindowExpired(attempt)) {
                return 0L;
            }
            return attempt.count;
        }
    }

    private long increment(String key) {
        if (redisTemplate != null) {
            try {
                // 与 RateLimitAspect 同口径：直接传 Integer，经 JSON 序列化器写出裸数字字面量
                // （Lua 侧 tonumber(ARGV[1]) 可用）；若包成 String 会带上 JSON 引号导致 tonumber 解析失败
                Long count = redisTemplate.execute(counterScript, Collections.singletonList(key),
                        LOCK_WINDOW_SECONDS);
                if (count != null) {
                    return count;
                }
            } catch (Exception e) {
                // 宽异常兜底：有意捕获 Exception，Redis 计数失败降级本地计数，绝不静默放行
                log.warn("[登录保护] Redis 计数异常，降级本地计数: key={}, error={}", key, e.getMessage(), e);
            }
        }
        LocalAttempt attempt = localAttempts.computeIfAbsent(key, k -> new LocalAttempt());
        synchronized (attempt) {
            long now = System.currentTimeMillis();
            if (attempt.firstFailMillis == 0 || now - attempt.firstFailMillis > LOCK_WINDOW_SECONDS * 1000L) {
                attempt.firstFailMillis = now;
                attempt.count = 1;
            } else {
                attempt.count++;
            }
            return attempt.count;
        }
    }

    private boolean isWindowExpired(LocalAttempt attempt) {
        return attempt.firstFailMillis == 0
                || System.currentTimeMillis() - attempt.firstFailMillis > LOCK_WINDOW_SECONDS * 1000L;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static long toCount(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 定时清理本地降级计数中已过期（超过锁定窗口无新失败）的条目，避免内存泄漏（OOM）。
     * 每分钟执行一次（{@code @EnableScheduling} 已在本应用启用）。
     */
    @Scheduled(fixedRate = 60000)
    public void cleanExpiredLocalAttempts() {
        if (localAttempts.isEmpty()) {
            return;
        }
        int before = localAttempts.size();
        localAttempts.entrySet().removeIf(entry -> {
            LocalAttempt attempt = entry.getValue();
            synchronized (attempt) {
                return isWindowExpired(attempt);
            }
        });
        int removed = before - localAttempts.size();
        if (removed > 0) {
            log.debug("[登录保护] 定时清理本地降级计数：移除 {} 条过期条目，剩余 {}", removed, localAttempts.size());
        }
    }
}
