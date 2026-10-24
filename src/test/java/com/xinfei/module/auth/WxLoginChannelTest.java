package com.xinfei.module.auth;

import com.xinfei.common.PasswordUtils;
import com.xinfei.module.user.service.WxCode2SessionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 微信一键登录全链路测试（miniapp 前置改造 5.3）。
 * <p>
 * RANDOM_PORT 真实容器 + 保留过滤器链（口径同 {@code AuthTokenChannelTest}），
 * code2Session 外网调用以 {@code @MockitoBean} 替换，验证：
 * <ol>
 *   <li>openid 未绑定：/user/wxLogin 返回 needBind + 一次性 wxTicket；</li>
 *   <li>手机号验证码登录携带 wxTicket：登录成功且 openid 绑定到该账号；</li>
 *   <li>绑定后再 wxLogin：直接登录并下发 X-Auth-Token，凭 token 可访问 /user/info；</li>
 *   <li>wxTicket 一次性：重复消费降级为普通登录（登录成功、不重复绑定）；</li>
 *   <li>openid 已绑其他账号：冲突拒绝登录，且不建立会话、不下发 token。</li>
 * </ol>
 * 真实 Redis（绑定凭据 / 验证码 / token）+ 真实 MySQL（user 档案）。
 * </p>
 *
 * @author xinfei
 * @since 2026-10-22
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "xinfei.test.keep-auth-filters=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WxLoginChannelTest {

    private static final long USER_ID = 990104L;
    private static final String PHONE = "13800138104";
    private static final String OPENID = "oXyz-test-openid-990104";

    @LocalServerPort
    private int port;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    @Autowired
    private StringRedisTemplate stringRedis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 替换真实外网 code2Session 调用（fail-closed 的配置检查在 mock 侧同样被绕过） */
    @MockitoBean
    private WxCode2SessionClient wxCode2SessionClient;

    private TestRestTemplate rest;

    private String base;

    @BeforeEach
    void setUp() {
        rest = new TestRestTemplate();
        base = "http://localhost:" + port;
        when(wxCode2SessionClient.code2OpenId(anyString())).thenReturn(OPENID);

        cleaner.cleanTables("user");
        testIdGuard.assertAbsent("user", USER_ID);
        jdbcTemplate.update("INSERT INTO user (id, name, phone, status, tenant_id, create_time) "
                + "VALUES (?, '微信绑定测试顾客', ?, 1, 999, NOW())", USER_ID, PHONE);
        // 清理跨用例残留：绑定凭据 / 验证码 / 全部频控键 / 台账
        // （sms:daily、sms:total:daily 跨用例累积会触发每日上限，sendMsg 返回 R.error 但 HTTP 仍 200）
        deleteByPrefix("wxbind:ticket:*");
        deleteByPrefix("sms:code:*");
        deleteByPrefix("sms:daily:*");
        deleteByPrefix("sms:total:daily:*");
        deleteByPrefix("auth:token:*");
        stringRedis.delete("sms:cooldown:" + PHONE);
        jdbcTemplate.update("DELETE FROM sms_send_log WHERE phone = ?", PHONE);
    }

    @AfterEach
    void cleanUp() {
        cleaner.cleanTables("user");
        deleteByPrefix("wxbind:ticket:*");
        deleteByPrefix("sms:code:*");
        deleteByPrefix("sms:daily:*");
        deleteByPrefix("sms:total:daily:*");
        deleteByPrefix("auth:token:*");
        stringRedis.delete("sms:cooldown:" + PHONE);
        jdbcTemplate.update("DELETE FROM sms_send_log WHERE phone = ?", PHONE);
    }

    private void deleteByPrefix(String pattern) {
        Set<String> keys = stringRedis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedis.delete(keys);
        }
    }

    @Test
    void wxLoginUnboundReturnsNeedBindAndTicket() {
        ResponseEntity<String> resp = postJson("/user/wxLogin", "{\"code\":\"any-code\"}", null);
        assertEquals(200, resp.getStatusCode().value(), "resp=" + resp.getBody());
        assertTrue(resp.getBody().contains("\"needBind\":true"), resp.getBody());
        assertTrue(resp.getBody().contains("wxTicket"), resp.getBody());
        // openid 绝不回传给前端
        assertFalse(resp.getBody().contains(OPENID), resp.getBody());
    }

    @Test
    void smsLoginWithTicketBindsOpenid() {
        // 1. wxLogin 拿到 needBind + wxTicket
        String wxTicket = extractField(postJson("/user/wxLogin", "{\"code\":\"any-code\"}", null).getBody(), "wxTicket");
        assertNotNull(wxTicket);

        // 2. 发码 → Redis 取码
        assertEquals(200, postJson("/user/sendMsg", "{\"phone\":\"" + PHONE + "\"}", null).getStatusCode().value());
        String code = stringRedis.opsForValue().get("sms:code:" + PHONE);
        assertNotNull(code);

        // 3. 验证码登录携带 wxTicket → 绑定
        ResponseEntity<String> loginResp = postJson("/user/login",
                "{\"phone\":\"" + PHONE + "\",\"code\":\"" + code + "\",\"wxTicket\":\"" + wxTicket + "\"}", null);
        assertEquals(200, loginResp.getStatusCode().value());
        assertTrue(loginResp.getBody().contains("\"code\":1"), loginResp.getBody());

        // 4. openid 已持久化到本人账号
        String openid = jdbcTemplate.queryForObject("SELECT openid FROM user WHERE id = ?", String.class, USER_ID);
        assertEquals(OPENID, openid);

        // 5. wxTicket 一次性：重新发码取新码，用已消费的旧 ticket 登录——
        //    降级为普通登录（登录成功、正常下发 token），已绑定的 openid 保持不变，不报错、不重复绑定。
        //    （必须用新码：短信验证码一次性，旧码在第 3 步已消费；先清 60s 发码冷却键，否则第二次发码被频控拦截）
        stringRedis.delete("sms:cooldown:" + PHONE);
        assertEquals(200, postJson("/user/sendMsg", "{\"phone\":\"" + PHONE + "\"}", null).getStatusCode().value());
        String code2 = stringRedis.opsForValue().get("sms:code:" + PHONE);
        assertNotNull(code2);
        ResponseEntity<String> second = postJson("/user/login",
                "{\"phone\":\"" + PHONE + "\",\"code\":\"" + code2 + "\",\"wxTicket\":\"" + wxTicket + "\"}", null);
        assertTrue(second.getBody().contains("\"code\":1"), second.getBody());
        assertNotNull(second.getHeaders().getFirst("X-Auth-Token"), "降级登录也必须下发 token");
        assertEquals(OPENID, jdbcTemplate.queryForObject(
                "SELECT openid FROM user WHERE id = ?", String.class, USER_ID));
    }

    @Test
    void boundOpenidLogsInDirectly() {
        // 预置：openid 已绑定
        jdbcTemplate.update("UPDATE user SET openid = ? WHERE id = ?", OPENID, USER_ID);

        ResponseEntity<String> resp = postJson("/user/wxLogin", "{\"code\":\"any-code\"}", null);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("\"code\":1"), resp.getBody());
        assertTrue(resp.getBody().contains(String.valueOf(USER_ID)), resp.getBody());
        // 手机号默认脱敏；openid 绝不回传前端
        assertFalse(resp.getBody().contains(PHONE), resp.getBody());
        assertFalse(resp.getBody().contains(OPENID), resp.getBody());

        String token = resp.getHeaders().getFirst("X-Auth-Token");
        assertNotNull(token, "微信一键登录必须下发 X-Auth-Token");
        ResponseEntity<String> info = exchange("/user/info", HttpMethod.GET, token);
        assertEquals(200, info.getStatusCode().value());
        assertTrue(info.getBody().contains("\"code\":1"), info.getBody());
    }

    @Test
    void bindConflictRejectsLoginWithoutToken() {
        // 另一个账号（此刻未绑 openid）
        long otherUserId = 990105L;
        testIdGuard.assertAbsent("user", otherUserId);
        jdbcTemplate.update("INSERT INTO user (id, name, phone, status, tenant_id, create_time) "
                + "VALUES (?, '微信绑定冲突顾客', '13800138105', 1, 999, NOW())", otherUserId);

        // 1. 当前用户 wxLogin：OPENID 未绑任何人，拿到 needBind + wxTicket
        String wxTicket = extractField(postJson("/user/wxLogin", "{\"code\":\"any-code\"}", null).getBody(), "wxTicket");
        assertNotNull(wxTicket);

        // 2. 模拟拿 ticket 期间该微信在别处绑给了另一个账号
        jdbcTemplate.update("UPDATE user SET openid = ? WHERE id = ?", OPENID, otherUserId);

        // 3. 发码 → Redis 取码
        stringRedis.delete("sms:cooldown:" + PHONE);
        assertEquals(200, postJson("/user/sendMsg", "{\"phone\":\"" + PHONE + "\"}", null).getStatusCode().value());
        String code = stringRedis.opsForValue().get("sms:code:" + PHONE);
        assertNotNull(code);

        // 4. 携带 ticket 登录：冲突拒绝，且绝不建立登录态（核心安全断言：错误响应不得携带有效 token）
        ResponseEntity<String> resp = postJson("/user/login",
                "{\"phone\":\"" + PHONE + "\",\"code\":\"" + code + "\",\"wxTicket\":\"" + wxTicket + "\"}", null);
        assertTrue(resp.getBody().contains("\"code\":0"), resp.getBody());
        assertTrue(resp.getBody().contains("该微信已绑定其他账号"), resp.getBody());
        assertNull(resp.getHeaders().getFirst("X-Auth-Token"), "绑定冲突不得下发 token");
        assertNull(jdbcTemplate.queryForObject("SELECT openid FROM user WHERE id = ?", String.class, USER_ID),
                "冲突时不得给当前用户写入 openid");
    }

    @Test
    void invalidCodeRejectedFailClosed() {
        when(wxCode2SessionClient.code2OpenId(anyString()))
                .thenThrow(new com.xinfei.common.CustomException("微信登录失败，请重新点击登录"));
        ResponseEntity<String> resp = postJson("/user/wxLogin", "{\"code\":\"bad\"}", null);
        // 项目约定：CustomException 由全局异常处理器映射为 422 UNPROCESSABLE_ENTITY + R.error
        assertEquals(422, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("\"code\":0"), resp.getBody());
    }

    // ---- 工具 ----

    private String extractField(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private ResponseEntity<String> postJson(String path, String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("X-Auth-Token", token);
        }
        return rest.postForEntity(base + path, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set("X-Auth-Token", token);
        }
        return rest.exchange(base + path, method, new HttpEntity<>(headers), String.class);
    }
}
