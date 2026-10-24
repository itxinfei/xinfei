package com.xinfei.module.payment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinfei.common.BaseContext;
import com.xinfei.controller.BaseControllerTest;
import com.xinfei.module.member.model.Member;
import com.xinfei.module.member.service.MemberService;
import com.xinfei.module.order.model.Orders;
import com.xinfei.module.order.service.OrderService;
import com.xinfei.module.payment.model.PaymentOrder;
import com.xinfei.module.payment.service.PaymentOrderService;
import com.xinfei.module.user.model.User;
import com.xinfei.module.user.mapper.UserMapper;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会员储值支付（BALANCE 渠道，O-06）全链路集成测试：
 * 下单待付款 → POST /api/payment/pay{channel:BALANCE} 同步扣余额 → 订单进入待接单、支付单 SUCCESS。
 * <p>与收银台储值支付（payType=5 → MemberRewardService#deductStoredBalance）同一扣款内核。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "classpath:schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class BalancePayIntegrationTest extends BaseControllerTest {

    private static final long USER_ID = 990401L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberService memberService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        cleaner.cleanTables("member", "points_record", "payment_order", "orders", "user");
        testIdGuard.assertAbsent("user", USER_ID);
        Set<String> rateLimitKeys = redisTemplate.keys("rate_limit:*");
        if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
            redisTemplate.delete(rateLimitKeys);
        }
        BaseContext.setCurrentId(USER_ID);
        BaseContext.setCurrentTenantId(999L);
    }

    @Test
    @DisplayName("1. 储值支付成功 - 扣余额、订单进入待接单、支付单 SUCCESS(BALANCE)")
    void testPayByBalance_success() throws Exception {
        insertUser();
        openMemberWithBalance("100.00");
        long orderId = insertPendingOrder(990401L, "30.00");

        mockMvc.perform(withCsrfToken(mockMvc, post("/api/payment/pay"))
                        .sessionAttr("user", USER_ID).sessionAttr("tenantId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"channel\":\"BALANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.success").value(true));

        assertEquals(Orders.STATUS_ORDERED, orderService.getById(orderId).getStatus(), "支付成功应进入待接单");
        assertEquals(0, new BigDecimal("70.00").compareTo(memberOf().getBalance()), "会员余额应扣减为 70");
        PaymentOrder po = paymentOf(orderId);
        assertNotNull(po, "应产生 BALANCE 支付单");
        assertEquals(PaymentOrder.STATUS_SUCCESS, po.getStatus());
        assertEquals("BALANCE", po.getChannel());
    }

    @Test
    @DisplayName("2. 储值余额不足 - 拒付且订单保持待付款、余额不动")
    void testPayByBalance_insufficient() throws Exception {
        insertUser();
        openMemberWithBalance("10.00");
        long orderId = insertPendingOrder(990402L, "30.00");

        mockMvc.perform(withCsrfToken(mockMvc, post("/api/payment/pay"))
                        .sessionAttr("user", USER_ID).sessionAttr("tenantId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"channel\":\"BALANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("储值不可用")));

        assertEquals(Orders.STATUS_PENDING_PAY, orderService.getById(orderId).getStatus(), "失败应保持待付款");
        assertEquals(0, new BigDecimal("10.00").compareTo(memberOf().getBalance()), "余额不应变动");
    }

    @Test
    @DisplayName("3. 未开通会员 - 拒付且不产生扣款")
    void testPayByBalance_noMember() throws Exception {
        insertUser();
        long orderId = insertPendingOrder(990403L, "30.00");

        mockMvc.perform(withCsrfToken(mockMvc, post("/api/payment/pay"))
                        .sessionAttr("user", USER_ID).sessionAttr("tenantId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"channel\":\"BALANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("储值不可用")));

        assertEquals(Orders.STATUS_PENDING_PAY, orderService.getById(orderId).getStatus());
        assertNull(memberOf(), "不应开户");
    }

    @Test
    @DisplayName("4. 支付后重复发起 - 订单状态不允许支付，余额不再扣减")
    void testPayByBalance_idempotentRetry() throws Exception {
        insertUser();
        openMemberWithBalance("100.00");
        long orderId = insertPendingOrder(990404L, "30.00");

        mockMvc.perform(withCsrfToken(mockMvc, post("/api/payment/pay"))
                        .sessionAttr("user", USER_ID).sessionAttr("tenantId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"channel\":\"BALANCE\"}"))
                .andExpect(jsonPath("$.code").value(1));

        mockMvc.perform(withCsrfToken(mockMvc, post("/api/payment/pay"))
                        .sessionAttr("user", USER_ID).sessionAttr("tenantId", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"channel\":\"BALANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("不允许支付")));

        assertEquals(0, new BigDecimal("70.00").compareTo(memberOf().getBalance()), "重试不应二次扣款");
    }

    // ==================== helpers ====================

    private void insertUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setName("储值支付测试用户");
        user.setPhone("13900990401");
        user.setStatus(1);
        user.setTenantId(999L);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    /** 自助开户后直接把余额设为指定值（绕过充值确认链路，聚焦支付路径） */
    private void openMemberWithBalance(String balance) {
        Member member = memberService.registerForUser(USER_ID, "13900990401", "储值支付测试用户");
        assertNotNull(member, "开户应成功");
        member.setBalance(new BigDecimal(balance));
        memberService.updateById(member);
    }

    private long insertPendingOrder(long orderId, String amount) {
        Orders order = new Orders();
        order.setId(orderId);
        order.setNumber("BAL" + orderId);
        order.setUserId(USER_ID);
        order.setStatus(Orders.STATUS_PENDING_PAY);
        order.setAmount(new BigDecimal(amount));
        order.setOrderTime(LocalDateTime.now());
        order.setUserName("储值测试用户");
        order.setPhone("13800138000");
        order.setAddress("测试地址");
        order.setConsignee("收餐人");
        order.setSource("TAKEOUT");
        order.setTenantId(999L);
        orderService.save(order);
        return orderId;
    }

    private Member memberOf() {
        return memberService.getByUserId(USER_ID);
    }

    private PaymentOrder paymentOf(long orderId) {
        return paymentOrderService.getOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderId, orderId)
                .eq(PaymentOrder::getChannel, "BALANCE")
                .orderByDesc(PaymentOrder::getId)
                .last("LIMIT 1"));
    }
}
