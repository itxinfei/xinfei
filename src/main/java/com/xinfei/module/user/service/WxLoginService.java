package com.xinfei.module.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 微信登录绑定凭据服务（miniapp 前置改造 5.3）。
 * <p>
 * 顾客首次微信登录时 openid 尚未绑定任何用户（user.phone NOT NULL，不允许无手机号注册），
 * 返回一次性 {@code wxTicket}（SecureRandom 128bit，Redis 5 分钟 TTL）：前端引导用户走
 * 手机号+验证码登录，登录请求携带 wxTicket，服务端凭 ticket 取回 openid 与该用户绑定。
 * 绑定完成后，下次 wxLogin 凭 openid 直接登录。</p>
 * <p>安全：ticket 一次性消费（消费即删）、短 TTL、高熵不可猜测；openid 不回传给前端。</p>
 *
 * @author xinfei
 * @since 2026-10-22
 */
@Slf4j
@Service
public class WxLoginService {

    private static final String TICKET_KEY_PREFIX = "wxbind:ticket:";
    private static final Duration TICKET_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private WxCode2SessionClient wxCode2SessionClient;

    /**
     * 临时 code 换 openid 并为「未绑定场景」签发一次性绑定凭据。
     *
     * @param jsCode wx.login 的临时 code
     * @return openid
     * @throws CustomException code 无效 / 微信侧异常
     */
    public String resolveOpenid(String jsCode) {
        return wxCode2SessionClient.code2OpenId(jsCode);
    }

    /**
     * 为未绑定用户签发一次性 wxTicket（Redis 持有 ticket → openid 映射）。
     *
     * @param openid 微信 openid
     * @return ticket 明文（仅此一次可见）
     */
    public String issueBindTicket(String openid) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        stringRedisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket, openid, TICKET_TTL);
        return ticket;
    }

    /**
     * 尝试消费一次性绑定凭据，取回 openid（消费即删，防重放）。
     *
     * @param ticket 前端登录时携带的 wxTicket
     * @return openid；ticket 为空 / 已过期 / 已消费 / Redis 中不存在时返回 {@code null}，
     *         调用方按「降级为普通登录、跳过本次绑定」处理，不抛异常——手机+验证码已通过
     *         强身份校验，不应因绑定凭据 5 分钟 TTL 阻塞登录
     */
    public String tryConsumeBindTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String key = TICKET_KEY_PREFIX + ticket;
        String openid = stringRedisTemplate.opsForValue().get(key);
        if (openid == null) {
            return null;
        }
        stringRedisTemplate.delete(key);
        return openid;
    }
}
