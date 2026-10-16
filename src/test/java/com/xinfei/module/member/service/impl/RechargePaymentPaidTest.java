package com.xinfei.module.member.service.impl;

import com.xinfei.module.member.mapper.MemberMapper;
import com.xinfei.module.member.mapper.RechargeRecordMapper;
import com.xinfei.module.member.model.RechargeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RechargeRecordServiceImpl#handleRechargePaid 在线充值资金路径纯 Mockito 测试（P1-4/P1-5）。
 * 覆盖：CAS 成功入账(true)、SUCCESS 回调幂等(true)、CANCELLED 支付迟到不入账(false→自动退款)、
 * 记录缺失(false)、rechargeId 为空(false)、加余额失败仅告警不抛(true=人工补录非退款)、
 * 渠道号缺失兜底内部交易号。
 *
 * @author xinfei
 * @since 2026-09-29
 */
@ExtendWith(MockitoExtension.class)
class RechargePaymentPaidTest {

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private RechargeRecordMapper rechargeRecordMapper;

    private RechargeRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new RechargeRecordServiceImpl());
        ReflectionTestUtils.setField(service, "memberMapper", memberMapper);
        ReflectionTestUtils.setField(service, "rechargeRecordMapper", rechargeRecordMapper);
    }

    @Test
    void paid_casOk_addBalanceOk_returnsTrue() {
        doReturn(buildRecord("PENDING")).when(service).getById(6101L);
        when(rechargeRecordMapper.casSuccessByPayment(6101L, "CH1")).thenReturn(1);
        when(memberMapper.addBalance(eq(7001L), eq(new BigDecimal("100")), any())).thenReturn(1);

        assertTrue(service.handleRechargePaid(6101L, "T1", "CH1"));

        verify(memberMapper).addBalance(eq(7001L), eq(new BigDecimal("100")), any());
    }

    @Test
    void paid_cas0_alreadySuccess_idempotentTrue() {
        doReturn(buildRecord("SUCCESS")).when(service).getById(6101L);
        when(rechargeRecordMapper.casSuccessByPayment(6101L, "CH1")).thenReturn(0);

        // 已入账：幂等 true，不触发退款
        assertTrue(service.handleRechargePaid(6101L, "T1", "CH1"));

        verify(memberMapper, never()).addBalance(any(), any(), any());
    }

    @Test
    void paid_cas0_cancelled_latePayment_returnsFalseForAutoRefund() {
        doReturn(buildRecord("CANCELLED")).when(service).getById(6101L);
        when(rechargeRecordMapper.casSuccessByPayment(6101L, "CH1")).thenReturn(0);

        // P1-5：超时取消后支付迟到：钱已收不能入账，返回 false 由支付回调自动退款；不得再加余额
        assertFalse(service.handleRechargePaid(6101L, "T1", "CH1"));
        verify(memberMapper, never()).addBalance(any(), any(), any());
    }

    @Test
    void paid_rechargeIdNull_returnsFalse() {
        // P1-5：支付单未携带充值ID：无法入账，必须退款（不应静默吞款）
        assertFalse(service.handleRechargePaid(null, "T1", "CH1"));
        verify(memberMapper, never()).addBalance(any(), any(), any());
    }

    @Test
    void paid_recordMissing_returnsFalse() {
        // P1-5：充值记录不存在：钱已收无入账对象，返回 false 触发自动退款
        doReturn(null).when(service).getById(6101L);
        assertFalse(service.handleRechargePaid(6101L, "T1", "CH1"));
        verify(memberMapper, never()).addBalance(any(), any(), any());
    }

    @Test
    void paid_casOk_addBalanceFail0_returnsTrueNoRefund() {
        doReturn(buildRecord("PENDING")).when(service).getById(6101L);
        when(rechargeRecordMapper.casSuccessByPayment(6101L, "CH1")).thenReturn(1);
        when(memberMapper.addBalance(eq(7001L), eq(new BigDecimal("100")), any())).thenReturn(0);

        // 终态已落、加余额失败：充值单已 SUCCESS，属人工补录场景，返回 true 不触发退款
        assertTrue(service.handleRechargePaid(6101L, "T1", "CH1"));
        verify(memberMapper).addBalance(eq(7001L), eq(new BigDecimal("100")), any());
    }

    @Test
    void paid_channelTradeNoBlank_fallbackPaymentTradeNo() {
        doReturn(buildRecord("PENDING")).when(service).getById(6101L);
        when(rechargeRecordMapper.casSuccessByPayment(6101L, "T1")).thenReturn(1);
        when(memberMapper.addBalance(eq(7001L), eq(new BigDecimal("100")), any())).thenReturn(1);

        // 渠道号缺失（mock）兜底存内部交易号
        assertTrue(service.handleRechargePaid(6101L, "T1", ""));
        verify(rechargeRecordMapper).casSuccessByPayment(6101L, "T1");
    }

    private RechargeRecord buildRecord(String status) {
        RechargeRecord record = new RechargeRecord();
        record.setId(6101L);
        record.setMemberId(7001L);
        record.setAmount(new BigDecimal("100"));
        record.setStatus(status);
        return record;
    }
}
