package com.xinfei.module.user.service;

import com.xinfei.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * <p>
 * 短信验证码存储服务（miniapp 前置改造 5.2）：验证码从 HttpSession 迁 Redis，
 * 使「发码」与「校验」可以跨会话、跨端（小程序无 cookie 容器，发码与登录两个请求
 * 不落在同一 session 上，纯 session 存储时顾客端登录链整条不通）。
 * </p>
 * <p>
 * 设计：
 * <ul>
 *   <li>Redis 键 {@code sms:code:<phone>}，值为 6 位数字码，TTL 5 分钟——过期判断由
 *       Redis TTL 自然承担，替代原 session {@code _time} 时间戳比较；</li>
 *   <li>Redis 不可用时回落 HttpSession（键 \'smsCode_<phone>\' 与 \'smsCode_<phone>_time\' 不变，
 *       口径同 R-01 降级：Web 端仍可用，只是小程序端暂时不可用）；校验端先查 Redis、
 *       未命中再查 session，兼容「发码时降级、校验时恢复」的降级窗口；</li>
 *   <li>校验通过立即删除（一次性使用）；校验失败不删除（与原 session 逻辑一致，
 *       防爆破由 5 分钟 TTL 与发码频控共同兜底）；</li>
 *   <li>比较使用常量时间算法（口径同 CsrfFilter P1-3），防时序侧信道。</li>
 * </ul>
 * 复用频控模板 {@code smsRateLimitRedisTemplate}：该 bean 以 \'@Autowired(required=false)\'
 * 风格注入（Redis 未配置时为 null），本服务对 null 与异常统一按「session 兜底」处理。
 * </p>
 *
 * @author xinfei
 * @since 2026-10-22
 */
@Slf4j
@Service
public class SmsCodeService {

    /** Redis 键前缀 */
    public static final String REDIS_KEY_PREFIX = "sms:code:";

    /** 验证码有效期 5 分钟（与各调用方原常量一致） */
    public static final long EXPIRE_MS = 5L * 60 * 1000;

    /** 复用频控模板；Redis 未配置时为 null，统一按 session 兜底处理 */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 发码侧存储验证码。
     *
     * @param phone   手机号
     * @param code    验证码明文（6 位数字）
     * @param session 当前会话（Redis 不可用时的兜底载体，可为 null）
     * @return true=存入 Redis；false=Redis 不可用已回落 session
     */
    public boolean save(String phone, String code, jakarta.servlet.http.HttpSession session) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + phone, code, Duration.ofMillis(EXPIRE_MS));
                return true;
            } catch (Exception e) {
                log.warn("验证码写 Redis 失败，回落 HttpSession（R-01 降级）: phone={}, error={}",
                        phone, e.getMessage());
            }
        }
        if (session != null) {
            session.setAttribute("smsCode_" + phone, code);
            session.setAttribute("smsCode_" + phone + "_time", System.currentTimeMillis());
        }
        return false;
    }

    /**
     * 校验并消费验证码（一次性）。
     * <p>Redis 命中：常量时间比较，通过即删除；未命中或 Redis 不可用：回落 session
     * 原逻辑（存在性 → 过期 → 等值 → 消费）。校验失败不删除验证码。</p>
     *
     * @param phone           手机号
     * @param code            用户输入的验证码
     * @param session         当前会话（session 兜底载体，可为 null）
     * @param notFoundMessage 「无验证码」场景的提示文案（各端原文案不同，由调用方传入）
     * @return null 表示校验通过（已消费）；否则返回错误响应
     */
    public R<String> verifyAndConsume(String phone, String code,
                                      jakarta.servlet.http.HttpSession session, String notFoundMessage) {
        if (code == null || code.trim().isEmpty()) {
            return R.error("请输入短信验证码");
        }
        if (redisTemplate != null) {
            try {
                String cached = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + phone);
                if (cached != null) {
                    if (!constantTimeEquals(cached, code.trim())) {
                        return R.error("验证码错误");
                    }
                    redisTemplate.delete(REDIS_KEY_PREFIX + phone);
                    return null;
                }
            } catch (Exception e) {
                log.warn("验证码读 Redis 失败，回落 HttpSession 校验（R-01 降级）: phone={}, error={}",
                        phone, e.getMessage());
            }
        }
        return verifyFromSession(phone, code.trim(), session, notFoundMessage);
    }

    /** 发送失败等场景清理验证码（Redis 与 session 都清） */
    public void delete(String phone, jakarta.servlet.http.HttpSession session) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_KEY_PREFIX + phone);
            } catch (Exception e) {
                log.warn("验证码清理 Redis 失败: phone={}, error={}", phone, e.getMessage());
            }
        }
        if (session != null) {
            session.removeAttribute("smsCode_" + phone);
            session.removeAttribute("smsCode_" + phone + "_time");
        }
    }

    /** session 兜底校验（原 UserController#login 逻辑，提示文案保持不变） */
    private R<String> verifyFromSession(String phone, String code,
                                        jakarta.servlet.http.HttpSession session, String notFoundMessage) {
        if (session == null) {
            return R.error(notFoundMessage);
        }
        String sessionCode = (String) session.getAttribute("smsCode_" + phone);
        Long codeTime = (Long) session.getAttribute("smsCode_" + phone + "_time");
        if (sessionCode == null || codeTime == null) {
            return R.error(notFoundMessage);
        }
        if (System.currentTimeMillis() - codeTime > EXPIRE_MS) {
            session.removeAttribute("smsCode_" + phone);
            session.removeAttribute("smsCode_" + phone + "_time");
            return R.error("验证码已过期，请重新获取");
        }
        if (!sessionCode.equals(code)) {
            return R.error("验证码错误");
        }
        session.removeAttribute("smsCode_" + phone);
        session.removeAttribute("smsCode_" + phone + "_time");
        return null;
    }

    /** 常量时间比较（口径同 CsrfFilter P1-3） */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
