package com.xinfei.module.notification.wxpush;

import com.xinfei.module.user.model.User;
import com.xinfei.module.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信订阅消息推送服务（P0-1 订单状态推送）。
 *
 * <p>发送链：access_token（stable 接口，进程内缓存至 expiry-5min）→
 * POST /cgi-bin/message/subscribe/send。appid/secret 复用
 * {@code xinfei.wechat-miniapp.*}（与 WxCode2SessionClient 同源配置）；
 * 模板 ID 走 {@code xinfei.wechat-miniapp.subscribe.status-template-id}——
 * 一个「订单状态通知」类目模板覆盖三个节点（title/detail 双字段），
 * 比每节点一模板少两次用户授权，授权成功率更高。</p>
 *
 * <p>纪律：①配置缺失/用户无 openid 静默跳过（推送是增强，绝不阻塞订单主流程）；
 * ②openid 日志脱敏尾 4 位；③微信 43101（用户未订阅）为常态，info 级不告警；
 * ④复用 eventListenerExecutor 异步消费（与 OrderCancelledNotifyListener 同口径）。</p>
 *
 * @author xinfei
 * @since 2026-10-25
 */
@Slf4j
@Component
public class WxSubscribePushService {

    private static final String TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String SEND_URL =
            "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=";

    @Value("${xinfei.wechat-miniapp.appid:}")
    private String appid;

    @Value("${xinfei.wechat-miniapp.secret:}")
    private String secret;

    /** 订单状态通知模板 ID（微信公众平台「订单状态」类目） */
    @Value("${xinfei.wechat-miniapp.subscribe.status-template-id:}")
    private String statusTemplateId;

    private final UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RestClient restClient;

    /** 进程内 access_token 缓存（微信 stable_token 有效期 2h；多实例各自缓存可接受） */
    private volatile String cachedToken;
    private volatile long tokenExpireAt = 0;

    public WxSubscribePushService(UserService userService) {
        this.userService = userService;
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * 推送订单状态事件给下单用户（无 openid / 未订阅 / 配置缺失全部静默降级）。
     */
    @Async("eventListenerExecutor")
    public void push(OrderPushEvent event) {
        try {
            if (appid == null || appid.isBlank() || secret == null || secret.isBlank()
                    || statusTemplateId == null || statusTemplateId.isBlank()) {
                log.debug("[订阅推送] 未配置 appid/secret/模板，跳过: orderId={}", event.getOrderId());
                return;
            }
            if (event.getUserId() == null) {
                return;
            }
            User user = userService.getById(event.getUserId());
            if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
                log.debug("[订阅推送] 用户无 openid（非微信登录），跳过: userId={}", event.getUserId());
                return;
            }
            doSend(user.getOpenid(), event);
        } catch (Exception e) {
            // 宽异常兜底：推送失败绝不影响订单状态流转（异步线程无接收方）
            log.warn("[订阅推送] 发送异常: orderId={}, err={}", event.getOrderId(), e.getMessage());
        }
    }

    private void doSend(String openid, OrderPushEvent event) {
        Map<String, Object> body = new HashMap<>();
        body.put("touser", openid);
        body.put("template_id", statusTemplateId);
        // page：小程序内跳转页（带订单 ID，点通知直达订单详情）
        body.put("page", "pages/order/detail?id=" + event.getOrderId());
        Map<String, Object> data = new HashMap<>();
        // thing1/thing2：类目模板的两个短文本字段（订单状态 + 备注说明）
        data.put("thing1", Map.of("value", clip(event.title(), 20)));
        data.put("thing2", Map.of("value", clip(event.detail(), 20)));
        body.put("data", data);

        Map resp;
        try {
            resp = restClient.post()
                    .uri(URI.create(SEND_URL + accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("[订阅推送] HTTP 失败: orderId={}, err={}", event.getOrderId(), e.getMessage());
            return;
        }
        Object errcode = resp == null ? null : resp.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            // 43101=用户未订阅（常态，授权是一次性的，每次下单重新请求）
            log.info("[订阅推送] 微信返回 errcode={}, errmsg={}, orderId=****{}",
                    errcode, resp.get("errmsg"),
                    String.valueOf(event.getOrderId()).substring(Math.max(0, String.valueOf(event.getOrderId()).length() - 2)));
            return;
        }
        log.info("[订阅推送] 已发送: openid=****{}, orderId={}, title={}",
                openid.length() > 4 ? openid.substring(openid.length() - 4) : openid,
                event.getOrderId(), event.title());
    }

    private synchronized String accessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAt) {
            return cachedToken;
        }
        Map<String, Object> req = Map.of("grant_type", "client_credential",
                "appid", appid, "secret", secret);
        Map resp = restClient.post()
                .uri(URI.create(TOKEN_URL))
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(req))
                .retrieve()
                .body(Map.class);
        if (resp == null || resp.get("access_token") == null) {
            throw new IllegalStateException("获取 access_token 失败: errcode=" + (resp == null ? null : resp.get("errcode")));
        }
        cachedToken = String.valueOf(resp.get("access_token"));
        int expiresIn = resp.get("expires_in") == null ? 7200 : Integer.parseInt(String.valueOf(resp.get("expires_in")));
        // 提前 5 分钟过期，避免边界竞态
        tokenExpireAt = now + (expiresIn - 300) * 1000L;
        return cachedToken;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
