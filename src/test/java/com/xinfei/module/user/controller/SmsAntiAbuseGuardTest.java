package com.xinfei.module.user.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.module.user.service.SmsSendLogService;
import com.xinfei.test.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 短信验证码防盗刷三层防护守卫测试（V8，2026-10-19）。
 *
 * <p>核心验证点不是 Redis 快路径（那部分 {@code UserControllerTest} 已间接覆盖），而是
 * 功能测试报告 §13 定性的根因——开发机 Redis 无 AOF，重启即清空 sms:daily:* 计数键，
 * 让 SMS_DAILY_LIMIT=10 形同虚设。本测试直接构造"Redis 侧干净、DB 台账已有真实记录"
 * 这一 Redis 重启后的现场，验证 sms_send_log DB 权威兜底能否独立于 Redis 拦下超限请求。</p>
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "classpath:schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SmsAntiAbuseGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private SmsSendLogService smsSendLogService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserController userController;

    /** 与 UserControllerTest 同族测试号段，避免撞 db/xinfei.sql 演示数据手机号 */
    private static final String TEST_PHONE = "13900139991";

    /** 与 UserController.SMS_DAILY_LIMIT 保持一致（该常量为 private，此处按同值断言） */
    private static final int SMS_DAILY_LIMIT = 10;

    /** 默认全局日上限，@BeforeEach 复位，避免某个用例改小后污染同类其余用例 */
    private static final long DEFAULT_TOTAL_CAP = 500L;

    @BeforeEach
    void setUp() {
        cleaner.cleanTables("sms_send_log");
        clearRedisSmsKeys(TEST_PHONE);
        // @RateLimit(maxRequestsPerSecond=3) 是 IP 维度 AOP 限流（RateLimitAspect 注入
        // RedisTemplate<String,Object>，非 StringRedisTemplate），本类多个用例快速连打同一路径，
        // 不清 rate_limit:* 键会命中 429 而非业务频控分支，导致断言落点错误（与 UserControllerTest 同款处理）
        java.util.Set<String> rateLimitKeys = redisTemplate.keys("rate_limit:*");
        if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
            redisTemplate.delete(rateLimitKeys);
        }
        ReflectionTestUtils.setField(userController, "smsDailyTotalCap", DEFAULT_TOTAL_CAP);
        BaseContext.setCurrentId(1L);
        BaseContext.setCurrentTenantId(999L);
    }

    private void clearRedisSmsKeys(String phone) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        stringRedisTemplate.delete("sms:cooldown:" + phone);
        stringRedisTemplate.delete("sms:daily:" + phone + ":" + today);
        stringRedisTemplate.delete("sms:total:daily:" + today);
    }

    private void sendMsg(String phone) throws Exception {
        mockMvc.perform(post("/user/sendMsg")
                .session(new MockHttpSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\"}"));
    }

    @Test
    @DisplayName("Redis 重启归零模拟：sms_send_log 已记满10条时，即便 Redis 冷却/计数键全空也必须拒绝第11条")
    void dbFallbackBlocksWhenRedisCountersLost() throws Exception {
        for (int i = 0; i < SMS_DAILY_LIMIT; i++) {
            smsSendLogService.record(TEST_PHONE, "login");
        }
        clearRedisSmsKeys(TEST_PHONE);

        mockMvc.perform(post("/user/sendMsg")
                .session(new MockHttpSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + TEST_PHONE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("今日验证码发送次数已达上限，请明日再试"));
    }

    @Test
    @DisplayName("全站日总量熔断：跨不同手机号累计达到 smsDailyTotalCap 时，第 N+1 个新号码也会被拒绝")
    void globalTotalCapBlocksAcrossPhones() throws Exception {
        ReflectionTestUtils.setField(userController, "smsDailyTotalCap", 3L);
        for (int i = 0; i < 3; i++) {
            smsSendLogService.record("1390013999" + i, "login");
        }

        mockMvc.perform(post("/user/sendMsg")
                .session(new MockHttpSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"13900139999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("系统今日验证码发送总量已达上限，请稍后再试"));
    }

    @Test
    @DisplayName("发送成功后必须落台账一条，作为下次 DB 权威计数的数据源")
    void successfulSendIsRecordedInLedger() throws Exception {
        long before = smsSendLogService.countTodayByPhone(TEST_PHONE);
        mockMvc.perform(post("/user/sendMsg")
                .session(new MockHttpSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + TEST_PHONE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        long after = smsSendLogService.countTodayByPhone(TEST_PHONE);
        assertEquals(before + 1, after);
    }

    @Test
    @DisplayName("60s 冷却跨会话生效：新会话（换 Cookie）也不能绕过，只有 Redis 冷却键能拦下")
    void cooldownBlocksAcrossSessions() throws Exception {
        sendMsg(TEST_PHONE);
        mockMvc.perform(post("/user/sendMsg")
                .session(new MockHttpSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + TEST_PHONE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.matchesRegex("请\\d+秒后再试")));
    }
}
