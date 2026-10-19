package com.xinfei.module.member.service.impl;

import com.xinfei.common.BaseContext;
import com.xinfei.enums.CouponStatus;
import com.xinfei.module.member.model.CouponTemplate;
import com.xinfei.module.member.model.CouponUser;
import com.xinfei.module.member.model.Member;
import com.xinfei.module.member.service.CouponTemplateService;
import com.xinfei.module.member.service.CouponUserService;
import com.xinfei.module.member.service.MemberService;
import com.xinfei.test.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * batchIssue 预过滤改造的回归锁：
 * 已持有券的会员应被单次批量预查询识别并计入 alreadyIssuedCount，
 * 结果口径（success/already/fail/total、库存扣减）必须与逐个 claimCoupon + getOne 完全一致。
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = {"classpath:schema-member.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CouponBatchIssuePrefilterTest {

    @Autowired
    private CouponTemplateService couponTemplateService;
    @Autowired
    private CouponUserService couponUserService;
    @Autowired
    private MemberService memberService;
    @Autowired
    private TestDatabaseCleaner cleaner;

    private Long templateId;
    private Long heldMemberId;   // 已持有该模板券
    private Long freshMemberId;  // 尚未领取

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentTenantId(999L);
        cleaner.cleanTables("coupon_user", "coupon_template", "member");

        CouponTemplate tpl = new CouponTemplate();
        tpl.setTenantId(999L);
        tpl.setName("测试满减券");
        tpl.setType("CASH");
        tpl.setDiscountAmount(new BigDecimal("10.00"));
        tpl.setTotalCount(10);
        tpl.setRemainCount(10);
        tpl.setValidDays(30);
        tpl.setStatus(1);
        couponTemplateService.save(tpl);
        templateId = tpl.getId();

        Member held = memberService.registerByPhone("13900001101", "已领会员");
        Member fresh = memberService.registerByPhone("13900001102", "新会员");
        heldMemberId = held.getId();
        freshMemberId = fresh.getId();

        // 预置：held 会员已持有该模板券
        CouponUser cu = new CouponUser();
        cu.setTenantId(999L);
        cu.setMemberId(heldMemberId);
        cu.setTemplateId(templateId);
        cu.setCode("PREEXIST00000001");
        cu.setStatus(CouponStatus.UNUSED.getValue());
        couponUserService.save(cu);
    }

    @Test
    void batchIssue_preFiltersHeldMember_resultMatchesLegacy() {
        List<Long> targets = new ArrayList<>(Arrays.asList(heldMemberId, freshMemberId));

        Map<String, Object> result = couponTemplateService.batchIssue(templateId, targets);

        // 口径与旧实现一致：fresh 成功、held 记为已领取、无失败、总数 2
        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("alreadyIssuedCount"));
        assertEquals(0, result.get("failCount"));
        assertEquals(2, result.get("total"));

        // 库存只被新领的一张扣减（held 被预过滤，不触发扣减+回滚）
        CouponTemplate after = couponTemplateService.getById(templateId);
        assertNotNull(after);
        assertEquals(9, after.getRemainCount(), "库存应仅减 1（10→9）");

        // held 仍只有 1 张（未被重复发放），fresh 新增 1 张
        assertEquals(1, countCoupons(heldMemberId));
        assertEquals(1, countCoupons(freshMemberId));
    }

    @Test
    void batchIssue_exhaustedStock_countsAsFail() {
        // 库存清零：新会员领取应失败（库存耗尽），已持有会员被预过滤计为 already
        CouponTemplate tpl = couponTemplateService.getById(templateId);
        tpl.setRemainCount(0);
        couponTemplateService.updateById(tpl);

        Map<String, Object> result = couponTemplateService.batchIssue(
                templateId, Arrays.asList(heldMemberId, freshMemberId));

        assertEquals(0, result.get("successCount"));
        assertEquals(1, result.get("alreadyIssuedCount"));
        assertEquals(1, result.get("failCount"));
        assertEquals(2, result.get("total"));
        assertEquals(0, countCoupons(freshMemberId), "库存耗尽不应发出新券");
    }

    private long countCoupons(Long memberId) {
        return couponUserService.lambdaQuery()
                .eq(CouponUser::getMemberId, memberId)
                .eq(CouponUser::getTemplateId, templateId)
                .count();
    }
}
