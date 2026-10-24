package com.xinfei.module.user.service;

import com.xinfei.common.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * 微信小程序 code2Session 客户端（miniapp 前置改造 5.3）。
 * <p>
 * 调用 {@code GET https://api.weixin.qq.com/sns/jscode2session}，用 wx.login 的临时 code
 * 换取 openid（+session_key，本项目不使用 session_key）。appid/secret 来自
 * {@code xinfei.wechat-miniapp.appid/secret} 配置——secret 属敏感信息，按仓库纪律
 * 放本地 application-dev.yml（gitignore），application.yml 只留空占位。</p>
 * <p>测试可注入 {@code @MockitoBean} 替换本组件，规避真实外网调用。</p>
 *
 * @author xinfei
 * @since 2026-10-22
 */
@Slf4j
@Component
public class WxCode2SessionClient {

    private static final String JSCODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}"
                    + "&js_code={code}&grant_type=authorization_code";

    @Value("${xinfei.wechat-miniapp.appid:}")
    private String appid;

    @Value("${xinfei.wechat-miniapp.secret:}")
    private String secret;

    private final RestClient restClient;

    public WxCode2SessionClient() {
        // 网络请求必须设超时（JDK HttpClient：连接 3s / 响应 5s），防微信接口抖动拖死请求线程
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * 临时 code 换 openid。
     *
     * @param jsCode wx.login 获得的临时登录凭证（一次性，5 分钟有效）
     * @return openid
     * @throws CustomException 配置缺失 / code 无效 / 微信侧异常（fail-closed，不下发任何登录态）
     */
    public String code2OpenId(String jsCode) {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            log.warn("xinfei.wechat-miniapp.appid/secret 未配置，拒绝微信登录（fail-closed）");
            throw new CustomException("微信登录未配置，请联系管理员");
        }
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(URI.create(JSCODE2SESSION_URL
                            .replace("{appid}", appid)
                            .replace("{secret}", secret)
                            .replace("{code}", java.net.URLEncoder.encode(jsCode, java.nio.charset.StandardCharsets.UTF_8))))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("code2Session 调用失败: {}", e.getMessage());
            throw new CustomException("微信登录失败，请稍后重试");
        }
        if (body == null || body.get("openid") == null) {
            Object errcode = body == null ? null : body.get("errcode");
            Object errmsg = body == null ? null : body.get("errmsg");
            // code 无效/过期（40029/40163）等业务错误：不泄露 secret，仅提示重试
            log.warn("code2Session 未返回 openid: errcode={}, errmsg={}", errcode, errmsg);
            throw new CustomException("微信登录失败，请重新点击登录（" + errcode + "）");
        }
        String openid = String.valueOf(body.get("openid"));
        // openid 属个人信息，日志只暴露尾 4 位；code 一次性且不落日志
        log.info("code2Session 成功: openid=****{}", openid.length() > 4
                ? openid.substring(openid.length() - 4) : openid);
        return openid;
    }
}
