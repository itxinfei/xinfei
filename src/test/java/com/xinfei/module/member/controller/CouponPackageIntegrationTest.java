package com.xinfei.module.member.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.controller.BaseControllerTest;
import com.xinfei.module.member.model.CouponPackage;
import com.xinfei.module.member.model.CouponPackageOrder;
import com.xinfei.module.member.model.CouponTemplate;
import com.xinfei.module.member.model.CouponUser;
import com.xinfei.module.member.service.CouponPackageService;
import com.xinfei.module.member.service.CouponUserService;
import com.xinfei.module.member.service.CouponTemplateService;
import com.xinfei.module.payment.model.PaymentOrder;
import com.xinfei.module.payment.service.PaymentOrderService;
import com.xinfei.module.user.mapper.UserMapper;
import com.xinfei.module.user.model.User;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 付费券包（O-18）全链集成测试：管理端建包 → 顾客购买单 → 支付回调拆包发券 → 幂等与下架约束。
 *
 * @author xinfei
 * @since 2026-10-24
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = {"classpath:schema.sql", "classpath:schema-member.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class CouponPackageIntegrationTest extends BaseControllerTest {

    private static final long USER_ID = 994501L;
    private static final long TPL_A = 994511L;
    private static final long TPL_B = 994512L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CouponPackageService couponPackageService;

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private CouponUserService couponUserService;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    @BeforeEach
    void setUp() {
        cleaner.cleanTables("coupon_package", "coupon_package_item", "coupon_package_order",
                "coupon_template", "coupon_user", "member", "payment_order", "user");
        testIdGuard.assertAbsent("user", USER_ID);
        BaseContext.setCurrentId(1L);
        BaseContext.setCurrentTenantId(999L);
        // 两个券模板每方法插一次（buildPackage 可被同方法多次调用，模板不能随包重复插）
        insertTemplate(TPL_A, "满30减5券", "30", "5");
        insertTemplate(TPL_B, "满50减10券", "50", "10");
    }

    @Test
    @DisplayName("1. 全链：建包(2张券/售9.9) → 购买 → 支付回调 → 购买单已支付且发券2张")
    void testFullChainBuyAndDeliver() throws Exception {
        insertUser();
        Long pkgId = insertPackageOnShelf();

        // 顾客创建购买单（幂等复用）
        String orderNo = buyPackage(pkgId);
        String orderNoAgain = buyPackage(pkgId);
        assertEquals(orderNo, orderNoAgain, "同用户同包应复用待支付单");

        // 创建 BIZ_COUPON_PACKAGE 支付单并模拟支付成功回调（与渠道回调同入口逻辑）
        CouponPackageOrder order = couponPackageService.myOrders(USER_ID).get(0);
        PaymentOrder po = paymentOrderService.createPaymentOrderForBiz(
                PaymentOrder.BIZ_COUPON_PACKAGE, order.getId(), "WECHAT", order.getPrice());
        paymentOrderService.handlePaymentSuccess(po.getTradeNo(), "MOCK-CP-1");

        // 购买单已支付；会员自动开通并发券 2 张
        CouponPackageOrder paid = couponPackageService.myOrders(USER_ID).get(0);
        assertEquals(CouponPackageOrder.STATUS_PAID, paid.getStatus());
        Long memberId = paidMemberId();
        assertNotNull(memberId, "购买者应自动开通会员");
        assertEquals(2, couponUserService.lambdaQuery()
                .eq(CouponUser::getMemberId, memberId)
                .in(CouponUser::getTemplateId, Arrays.asList(TPL_A, TPL_B))
                .count(), "包内两张券都应发放");

        // 重复回调幂等：不二次发券
        paymentOrderService.handlePaymentSuccess(po.getTradeNo(), "MOCK-CP-1");
        assertEquals(2, couponUserService.lambdaQuery()
                .eq(CouponUser::getMemberId, memberId).count());
    }

    @Test
    @DisplayName("2. 同模板重复配置被拒；下架包不可购买")
    void testSaveConstraintAndOffShelf() throws Exception {
        insertUser();
        CouponPackage pkg = buildPackage(0);
        pkg.setItems(Arrays.asList(item(TPL_A), item(TPL_A)));
        try {
            couponPackageService.saveWithItems(pkg);
            throw new AssertionError("同模板重复应被拒绝");
        } catch (com.xinfei.common.CustomException e) {
            assertTrue(e.getMessage().contains("每模板限一张"));
        }

        // 下架包购买被拒（顾客身份直设 BaseContext，MockMvc 下 filter 不生效为既有语义）
        Long pkgId = couponPackageService.saveWithItems(buildPackage(0)).getId();
        BaseContext.setCurrentId(USER_ID);
        try {
            mockMvc.perform(post("/api/coupon-package/buy")
                            .param("packageId", String.valueOf(pkgId))
                            .sessionAttr("user", USER_ID).sessionAttr("tenantId", 999L))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("下架")));
        } finally {
            BaseContext.setCurrentId(1L);
        }
    }

    @Test
    @DisplayName("3. 管理端接口：保存/上下架/分页（含明细摘要回填）")
    void testAdminEndpoints() throws Exception {
        Long pkgId = insertPackageOnShelf();

        mockMvc.perform(get("/coupon-package/page")
                        .param("page", "1").param("pageSize", "10")
                        .sessionAttr("employee", 1L).sessionAttr("tenantId", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].items.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].items[0].templateSummary").isNotEmpty());

        // 下架 → 顾客端列表不可见
        mockMvc.perform(put("/coupon-package/" + pkgId + "/status/0")
                        .sessionAttr("employee", 1L).sessionAttr("tenantId", 999L))
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(get("/api/coupon-package/list"))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ==================== helpers ====================

    private void insertUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setName("券包测试用户");
        user.setPhone("13900994501");
        user.setStatus(1);
        user.setTenantId(999L);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    private void insertTemplate(long id, String name, String cond, String off) {
        CouponTemplate tpl = new CouponTemplate();
        tpl.setId(id);
        tpl.setName(name);
        tpl.setType("FULL_REDUCTION");
        tpl.setConditionAmount(new BigDecimal(cond));
        tpl.setDiscountAmount(new BigDecimal(off));
        tpl.setTotalCount(100);
        tpl.setRemainCount(100);
        tpl.setValidDays(30);
        tpl.setStatus(1);
        tpl.setTenantId(999L);
        couponTemplateService.save(tpl);
    }

    /** 建包并直接上架，返回包 ID */
    private Long insertPackageOnShelf() {
        CouponPackage saved = couponPackageService.saveWithItems(buildPackage(1));
        return saved.getId();
    }

    private CouponPackage buildPackage(int status) {
        CouponPackage pkg = new CouponPackage();
        pkg.setName("尝鲜券包");
        pkg.setPrice(new BigDecimal("9.90"));
        pkg.setOriginalValue(new BigDecimal("15.00"));
        pkg.setDescription("两张小额券打包");
        pkg.setStatus(status);
        pkg.setSort(0);
        pkg.setItems(Arrays.asList(item(TPL_A), item(TPL_B)));
        return pkg;
    }

    private com.xinfei.module.member.model.CouponPackageItem item(long templateId) {
        com.xinfei.module.member.model.CouponPackageItem it = new com.xinfei.module.member.model.CouponPackageItem();
        it.setTemplateId(templateId);
        return it;
    }

    private String buyPackage(Long pkgId) throws Exception {
        final String[] holder = new String[1];
        // MockMvc 上下文 LoginCheckFilter 不生效（项目测试既有语义），顾客身份直设 BaseContext
        BaseContext.setCurrentId(USER_ID);
        try {
            mockMvc.perform(post("/api/coupon-package/buy")
                            .param("packageId", String.valueOf(pkgId))
                            .sessionAttr("user", USER_ID).sessionAttr("tenantId", 999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1))
                    .andDo(result -> {
                        String body = result.getResponse().getContentAsString();
                        holder[0] = tools.jackson.databind.json.JsonMapper.builder().build()
                                .readTree(body).path("data").path("orderNo").asString();
                    });
        } finally {
            BaseContext.setCurrentId(1L);
        }
        return holder[0];
    }

    private Long paidMemberId() {
        return couponPackageService.myOrders(USER_ID).isEmpty() ? null
                : memberIdOf(USER_ID);
    }

    @Autowired
    private com.xinfei.module.member.service.MemberService memberService;

    private Long memberIdOf(long userId) {
        return memberService.getByUserId(userId) != null ? memberService.getByUserId(userId).getId() : null;
    }
}
