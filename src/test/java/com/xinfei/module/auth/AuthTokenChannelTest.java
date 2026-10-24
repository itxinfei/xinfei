package com.xinfei.module.auth;

import com.xinfei.common.PasswordUtils;
import com.xinfei.module.auth.model.Employee;
import com.xinfei.module.auth.service.AuthTokenService;
import com.xinfei.module.auth.service.LoginAttemptService;
import com.xinfei.module.user.model.User;
import com.xinfei.module.user.service.SmsCodeService;
import com.xinfei.module.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 非 cookie 会话通道（miniapp 前置改造 5.1）端到端测试（三端主链路）。
 * <p>
 * 用 RANDOM_PORT 真实 Servlet 容器（MockMvc 链上 LoginCheckFilter 不生效，
 * 测不了 token 分支），覆盖：
 * <ol>
 *   <li>登录成功响应头下发 X-Auth-Token，载荷主体/租户正确；</li>
 *   <li>仅凭该头（无 session、无 cookie）可访问需登录接口；</li>
 *   <li>不带/携带无效令牌被 401 拒绝；</li>
 *   <li>登出吊销令牌后同一令牌立即失效。</li>
 * </ol>
 * 员工端 /user/loginout 登出、骑手端 /api/rider/logout 登出、顾客端 /user/info
 * 均走真实过滤器链。顾客端用例绕过短信链路（5.2 未完成，验证码仍在 HttpSession），
 * 直接经 AuthTokenService 颁发令牌，聚焦验证本改造的通道本身。
 * 真实 Redis（令牌与会话）+ 真实 MySQL（档案）。
 * </p>
 *
 * @author xinfei
 * @since 2026-10-22
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// 保留 CSRF/登录校验过滤器链（默认被 MockMvcFilterExclusionAutoConfiguration 移除）：
// 本类必须走完整过滤器链才能验证 LoginCheckFilter 的 X-Auth-Token 分支
@TestPropertySource(properties = "xinfei.test.keep-auth-filters=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthTokenChannelTest {

    private static final long EMP_ID = 990101L;
    private static final String EMP_USERNAME = "test_token_login";
    private static final long RIDER_ID = 990102L;
    private static final String RIDER_PHONE = "13800138102";
    private static final long USER_ID = 990103L;
    private static final String USER_PHONE = "13800138103";

    @LocalServerPort
    private int port;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedis;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private com.xinfei.module.auth.service.EmployeeService employeeService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestRestTemplate rest;

    private String base;

    @BeforeEach
    void setUp() {
        rest = new TestRestTemplate();
        base = "http://localhost:" + port;
        // 限流/锁定计数跨用例残留清理；令牌前缀键一并清空，保证断言不受历史数据干扰
        deleteByPrefix("rate_limit:*");
        deleteByPrefix("auth:token:*");
        // 5.2 验证码迁 Redis：清码键与本手机号冷却键，防历史残留遮蔽/频控误伤
        deleteByPrefix("sms:code:*");
        redisTemplate.delete("sms:cooldown:" + USER_PHONE);
        jdbcTemplate.update("DELETE FROM sms_send_log WHERE phone = ?", USER_PHONE);
        redisTemplate.delete(LoginAttemptService.buildKey(LoginAttemptService.Scope.EMP, "0:" + EMP_USERNAME));
        redisTemplate.delete(LoginAttemptService.buildKey(LoginAttemptService.Scope.EMP, "999:" + EMP_USERNAME));
        redisTemplate.delete(LoginAttemptService.buildKey(LoginAttemptService.Scope.RIDER, RIDER_PHONE));

        // 员工档案（MD5 口径同 EmployeeLoginSecurityTest）
        cleaner.cleanTables("employee");
        testIdGuard.assertAbsent("employee", EMP_ID);
        Employee employee = new Employee();
        employee.setId(EMP_ID);
        employee.setUsername(EMP_USERNAME);
        employee.setName("令牌测试员工");
        employee.setPassword("e10adc3949ba59abbe56e057f20f883e"); // 123456
        employee.setPasswordType("MD5");
        employee.setPhone("13800138101");
        employee.setSex("1");
        employee.setIdNumber("110101199001011234");
        employee.setStatus(1);
        employee.setTenantId(999L);
        employee.setCreateUser(EMP_ID);
        employee.setUpdateUser(EMP_ID);
        employeeService.save(employee);

        // 骑手档案（BCrypt 口径同 RiderAuthControllerTest）
        jdbcTemplate.update("DELETE FROM rider WHERE id = ? OR phone = ?", RIDER_ID, RIDER_PHONE);
        jdbcTemplate.update("INSERT INTO rider (id, name, phone, password, status, current_order_count, "
                + "total_order_count, tenant_id, create_time, update_time) "
                + "VALUES (?, '令牌测试骑手', ?, ?, 1, 0, 0, 999, NOW(), NOW())",
                RIDER_ID, RIDER_PHONE, PasswordUtils.encodePassword("123456"));

        // 顾客档案（本类专用高位主键，避免与演示段/其他测试段冲突）
        cleaner.cleanTables("user");
        testIdGuard.assertAbsent("user", USER_ID);
        User user = new User();
        user.setId(USER_ID);
        user.setPhone(USER_PHONE);
        user.setName("令牌测试顾客");
        user.setStatus(1);
        user.setTenantId(999L);
        userService.save(user);
    }

    @AfterEach
    void cleanUp() {
        // 插入型测试自清理：档案行 + 本类颁发的令牌，防跨类污染共享库/共享 Redis
        cleaner.cleanTables("employee");
        cleaner.cleanTables("user");
        jdbcTemplate.update("DELETE FROM rider WHERE id = ? OR phone = ?", RIDER_ID, RIDER_PHONE);
        deleteByPrefix("auth:token:*");
        deleteByPrefix("sms:code:*");
        redisTemplate.delete("sms:cooldown:" + USER_PHONE);
        jdbcTemplate.update("DELETE FROM sms_send_log WHERE phone = ?", USER_PHONE);
    }

    private void deleteByPrefix(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ---------- 员工端 ----------

    @Test
    void employeeLoginIssuesAuthToken() {
        ResponseEntity<String> resp = postJson("/employee/login",
                "{\"username\":\"" + EMP_USERNAME + "\",\"password\":\"123456\"}", null);
        assertEquals(200, resp.getStatusCode().value());
        String token = resp.getHeaders().getFirst(AuthTokenService.HEADER_NAME);
        assertNotNull(token, "员工登录成功响应头必须下发 X-Auth-Token");
        AuthTokenService.AuthTokenPayload payload = authTokenService.validate(token);
        assertNotNull(payload);
        assertEquals(AuthTokenService.TYPE_EMPLOYEE, payload.type());
        assertEquals(EMP_ID, payload.id());
        assertEquals(999L, payload.tenantId());
    }

    @Test
    void employeeTokenGrantsAccessWithoutSession() {
        String token = issueEmployeeTokenViaLogin();
        ResponseEntity<String> resp = exchange("/employee/me", HttpMethod.GET, token);
        assertEquals(200, resp.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(resp.getBody().contains("\"code\":1"));
        org.junit.jupiter.api.Assertions.assertTrue(resp.getBody().contains(String.valueOf(EMP_ID)));
    }

    @Test
    void employeeEndpointRejectedWithoutToken() {
        ResponseEntity<String> resp = exchange("/employee/me", HttpMethod.GET, null);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void employeeEndpointRejectedWithInvalidToken() {
        ResponseEntity<String> resp = exchange("/employee/me", HttpMethod.GET, "not-a-real-token");
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    void employeeLogoutRevokesToken() {
        String token = issueEmployeeTokenViaLogin();
        ResponseEntity<String> resp = postJson("/employee/logout", "{}", token);
        assertEquals(200, resp.getStatusCode().value());
        assertNull(authTokenService.validate(token), "登出后令牌必须已吊销");
        assertEquals(401, exchange("/employee/me", HttpMethod.GET, token).getStatusCode().value());
    }

    // ---------- 骑手端 ----------

    @Test
    void riderTokenGrantsAccessWithoutSession() {
        ResponseEntity<String> loginResp = postJson("/api/rider/login",
                "{\"phone\":\"" + RIDER_PHONE + "\",\"password\":\"123456\"}", null);
        assertEquals(200, loginResp.getStatusCode().value());
        String token = loginResp.getHeaders().getFirst(AuthTokenService.HEADER_NAME);
        assertNotNull(token, "骑手登录成功响应头必须下发 X-Auth-Token");
        ResponseEntity<String> resp = exchange("/api/rider/me", HttpMethod.GET, token);
        assertEquals(200, resp.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(resp.getBody().contains("\"code\":1"));
        org.junit.jupiter.api.Assertions.assertTrue(resp.getBody().contains(String.valueOf(RIDER_ID)));
    }

    @Test
    void riderLogoutRevokesToken() {
        ResponseEntity<String> loginResp = postJson("/api/rider/login",
                "{\"phone\":\"" + RIDER_PHONE + "\",\"password\":\"123456\"}", null);
        String token = loginResp.getHeaders().getFirst(AuthTokenService.HEADER_NAME);
        assertNotNull(token);
        ResponseEntity<String> resp = postJson("/api/rider/logout", "{}", token);
        assertEquals(200, resp.getStatusCode().value());
        assertNull(authTokenService.validate(token), "骑手登出后令牌必须已吊销");
        assertEquals(401, exchange("/api/rider/me", HttpMethod.GET, token).getStatusCode().value());
    }

    // ---------- 顾客端 ----------

    @Test
    void userTokenGrantsInfoAccess() {
        // 绕过短信链路（5.2 未完成）直接颁发令牌，聚焦验证通道与 /user/info 的 BaseContext 回落
        String token = authTokenService.issue(AuthTokenService.TYPE_USER, USER_ID, 999L, null);
        assertNotNull(token);
        ResponseEntity<String> resp = exchange("/user/info", HttpMethod.GET, token);
        assertEquals(200, resp.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(resp.getBody().contains("\"code\":1"));
        org.junit.jupiter.api.Assertions.assertTrue(resp.getBody().contains(String.valueOf(USER_ID)));
        // 默认脱敏：完整手机号不得出现，掩码必须存在
        org.junit.jupiter.api.Assertions.assertFalse(resp.getBody().contains(USER_PHONE));
        org.junit.jupiter.api.Assertions.assertTrue(resp.getBody().contains("****"));
    }

    @Test
    void customerSmsLoginFullChainViaToken() {
        // 5.2 验收：顾客端小程序登录链全链路——发码（Redis 存码，跨会话）→ 登录（无 session）
        // → 下发 X-Auth-Token → 凭 token 访问 /user/info
        ResponseEntity<String> sendResp = postJson("/user/sendMsg",
                "{\"phone\":\"" + USER_PHONE + "\"}", null);
        assertEquals(200, sendResp.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(sendResp.getBody().contains("\"code\":1"),
                "发码应成功: " + sendResp.getBody());

        String code = stringRedis.opsForValue().get(SmsCodeService.REDIS_KEY_PREFIX + USER_PHONE);
        assertNotNull(code, "验证码应已写入 Redis sms:code:<phone>");

        ResponseEntity<String> loginResp = postJson("/user/login",
                "{\"phone\":\"" + USER_PHONE + "\",\"code\":\"" + code + "\"}", null);
        assertEquals(200, loginResp.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(loginResp.getBody().contains("\"code\":1"),
                "验证码登录应成功: " + loginResp.getBody());
        String token = loginResp.getHeaders().getFirst(AuthTokenService.HEADER_NAME);
        assertNotNull(token, "顾客登录成功响应头必须下发 X-Auth-Token");

        ResponseEntity<String> infoResp = exchange("/user/info", HttpMethod.GET, token);
        assertEquals(200, infoResp.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(infoResp.getBody().contains("\"code\":1"));
        org.junit.jupiter.api.Assertions.assertTrue(infoResp.getBody().contains(String.valueOf(USER_ID)));
    }

    @Test
    void userLogoutRevokesToken() {
        String token = authTokenService.issue(AuthTokenService.TYPE_USER, USER_ID, 999L, null);
        ResponseEntity<String> resp = postJson("/user/loginout", "{}", token);
        assertEquals(200, resp.getStatusCode().value());
        assertNull(authTokenService.validate(token), "顾客登出后令牌必须已吊销");
        assertEquals(401, exchange("/user/info", HttpMethod.GET, token).getStatusCode().value());
    }

    // ---------- 上传/下载 token 通道（2026-10-24：CommonController 兼容 token 登录态） ----------

    @Test
    void userTokenGrantsUploadWithoutSession() {
        String token = authTokenService.issue(AuthTokenService.TYPE_USER, USER_ID, 999L, null);
        assertNotNull(token);
        org.springframework.util.MultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        // 1x1 PNG（真实魔数，通过 CommonController 的扩展名+魔数双重校验）
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        body.add("file", new org.springframework.core.io.ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "token-upload-test.png";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(AuthTokenService.HEADER_NAME, token);
        ResponseEntity<String> resp = rest.postForEntity(base + "/common/upload",
                new HttpEntity<>(body, headers), String.class);
        assertEquals(200, resp.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(resp.getBody().contains("\"code\":1"),
                "token 通道上传应成功: " + resp.getBody());

        // 同一文件匿名上传必须被拒（NOTLOGIN，防口径放宽）
        org.springframework.util.MultiValueMap<String, Object> anonBody =
                new org.springframework.util.LinkedMultiValueMap<>();
        anonBody.add("file", new org.springframework.core.io.ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "token-upload-test.png";
            }
        });
        ResponseEntity<String> anonResp = rest.postForEntity(base + "/common/upload",
                new HttpEntity<>(anonBody, headers_noToken()), String.class);
        org.junit.jupiter.api.Assertions.assertTrue(
                anonResp.getBody() != null && anonResp.getBody().contains("NOTLOGIN"),
                "匿名上传必须被拒: " + anonResp.getBody());
    }

    @Test
    void userTokenGrantsPrivateDownloadWithoutSession() {
        String token = authTokenService.issue(AuthTokenService.TYPE_USER, USER_ID, 999L, null);
        // 先经 token 通道上传一张用户侧私图，再凭 token 下载、匿名拒绝
        org.springframework.util.MultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        body.add("file", new org.springframework.core.io.ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "token-download-test.png";
            }
        });
        // bizType=avatar 落 private/user/avatar（匿名不可下载），空 bizType 会落旧公开目录导致断言失真
        body.add("bizType", "avatar");
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        uploadHeaders.set(AuthTokenService.HEADER_NAME, token);
        ResponseEntity<String> uploadResp = rest.postForEntity(base + "/common/upload",
                new HttpEntity<>(body, uploadHeaders), String.class);
        org.junit.jupiter.api.Assertions.assertTrue(uploadResp.getBody().contains("\"code\":1"),
                "前置上传应成功: " + uploadResp.getBody());
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"data\"\\s*:\\s*\"([^\"]+)\"").matcher(uploadResp.getBody());
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find(),
                "上传响应应包含文件相对路径: " + uploadResp.getBody());
        String relativePath = matcher.group(1);

        ResponseEntity<byte[]> dlResp = rest.exchange(base + "/common/download?name=" + relativePath,
                HttpMethod.GET, new HttpEntity<>(tokenHeaders(token)), byte[].class);
        assertEquals(200, dlResp.getStatusCode().value(),
                "token 通道应可下载用户私图: " + relativePath);

        ResponseEntity<byte[]> anonDl = rest.exchange(base + "/common/download?name=" + relativePath,
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);
        assertEquals(401, anonDl.getStatusCode().value(), "匿名下载私图必须 401");
    }

    private HttpHeaders headers_noToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private HttpHeaders tokenHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthTokenService.HEADER_NAME, token);
        return headers;
    }

    // ---------- 工具方法 ----------

    private String issueEmployeeTokenViaLogin() {
        ResponseEntity<String> resp = postJson("/employee/login",
                "{\"username\":\"" + EMP_USERNAME + "\",\"password\":\"123456\"}", null);
        assertEquals(200, resp.getStatusCode().value());
        return resp.getHeaders().getFirst(AuthTokenService.HEADER_NAME);
    }

    private ResponseEntity<String> postJson(String path, String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set(AuthTokenService.HEADER_NAME, token);
        }
        return rest.postForEntity(base + path, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set(AuthTokenService.HEADER_NAME, token);
        }
        return rest.exchange(base + path, method, new HttpEntity<>(headers), String.class);
    }
}
