package com.xinfei.controller;

import com.xinfei.common.SecurityConstants;
import com.xinfei.module.auth.model.Employee;
import com.xinfei.module.auth.service.EmployeeService;
import com.xinfei.module.auth.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmployeeLoginSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // MockMvc 不经过 LoginCheckFilter，BaseContext（ThreadLocal）不会像真实请求那样
        // 每个请求重置；同线程内前一个用例成功登录会残留 tenantId，导致本用例的锁定身份
        // key 变成 "999:xxx" 而不是 setUp 里清理的 "0:xxx"，用例间互相污染。显式清空。
        com.xinfei.common.BaseContext.remove();
        cleaner.cleanTables("employee");
        // 显式主键 insert 前防撞库 fail-fast
        testIdGuard.assertAbsent("employee", 990001L);
        // 锁定计数跨用例残留会导致本类用例互相污染，先清掉本类专用身份的历史计数。
        // 登录成功后 establishSessionAndBuildResult 会把 BaseContext 租户设为 999，
        // 同一线程内该用例后半段的失败尝试会以 "999:xxx" 而非 "0:xxx" 计入另一把 key，
        // 两个 key 都要清，避免用例间互相干扰。
        redisTemplate.delete(LoginAttemptService.buildKey(LoginAttemptService.Scope.EMP, "0:test_md5_login"));
        redisTemplate.delete(LoginAttemptService.buildKey(LoginAttemptService.Scope.EMP, "999:test_md5_login"));
        clearRateLimitKeys();

        // 创建测试专用账号 test_md5_login（隔离租户 999），密码为 123456 的 MD5
        Employee md5User = new Employee();
        md5User.setId(990001L);
        md5User.setUsername("test_md5_login");
        md5User.setName("管理员");
        md5User.setPassword("e10adc3949ba59abbe56e057f20f883e");
        md5User.setPasswordType("MD5");
        md5User.setPhone("13800138000");
        md5User.setSex("1");
        md5User.setIdNumber("110101199001011234");
        md5User.setStatus(1);
        md5User.setTenantId(999L);
        md5User.setCreateUser(990001L);
        md5User.setUpdateUser(990001L);
        employeeService.save(md5User);
    }

    /**
     * 清空 /employee/login 的按 IP 限流滑动窗口（{@code @RateLimit(maxRequestsPerSecond = 5, type = IP)}），
     * 否则锁定用例里的连续请求会先触发 429 限流（早于本用例真正要测的账号锁定逻辑）。
     */
    private void clearRateLimitKeys() {
        java.util.Set<String> rateLimitKeys = redisTemplate.keys("rate_limit:*");
        if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
            redisTemplate.delete(rateLimitKeys);
        }
    }

    @Test
    void testLoginWithBCryptPassword() throws Exception {
        mockMvc.perform(post("/employee/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_md5_login\",\"password\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    void testLoginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/employee/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_md5_login\",\"password\":\"wrong\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void testLoginLockedAfterMaxFailedAttempts() throws Exception {
        // P0-5：连续失败达 MAX_LOGIN_FAIL_COUNT 次后，登录接口须拒绝后续尝试并给出锁定文案，
        // 即使携带正确密码也不能绕过锁定
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_FAIL_COUNT; i++) {
            clearRateLimitKeys();
            mockMvc.perform(post("/employee/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"test_md5_login\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        }

        clearRateLimitKeys();
        mockMvc.perform(post("/employee/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_md5_login\",\"password\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.msg").value(
                    org.hamcrest.Matchers.containsString("登录失败次数过多")));
    }

    @Test
    void testLoginFailureCountResetsAfterSuccess() throws Exception {
        // 累计 4 次失败（未达阈值）后成功登录一次，计数清零；再连续失败须重新计满阈值才锁定
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_FAIL_COUNT - 1; i++) {
            clearRateLimitKeys();
            mockMvc.perform(post("/employee/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"test_md5_login\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        }
        clearRateLimitKeys();
        mockMvc.perform(post("/employee/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test_md5_login\",\"password\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(1));

        for (int i = 0; i < SecurityConstants.MAX_LOGIN_FAIL_COUNT - 1; i++) {
            clearRateLimitKeys();
            mockMvc.perform(post("/employee/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"test_md5_login\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"));
        }
    }
}



