package com.xinfei.module.payment.task;

import com.xinfei.enums.RefundStatus;
import com.xinfei.module.payment.channel.PaymentChannel;
import com.xinfei.module.payment.channel.PaymentChannelFactory;
import com.xinfei.module.payment.channel.RefundQueryCapable;
import com.xinfei.module.payment.channel.notify.RefundNotifyResult;
import com.xinfei.module.payment.config.PaymentConfigProperties;
import com.xinfei.module.payment.mapper.RefundRecordMapper;
import com.xinfei.module.payment.model.PaymentOrder;
import com.xinfei.module.payment.model.RefundRecord;
import com.xinfei.module.payment.service.PaymentOrderService;
import com.xinfei.module.payment.service.impl.RefundCallbackService;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RefundReconcileTask PROCESSING 退款查单（P1-6）单元测试。
 *
 * <p>mock 渠道退款同步终态、无 PROCESSING，任务在 mock-mode 下整体跳过；
 * 这里用 Mockito 模拟真实渠道查单返回，验证终态复用回调链路、非终态保守不动。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundReconcileTaskProcessingQueryTest {

    /** 同时具备支付渠道与退款查单能力的测试接口 */
    interface QueryCapableChannel extends PaymentChannel, RefundQueryCapable {
    }

    private static final Long TENANT_ID = 999L;
    private static final String REFUND_NO = "RF-TEST-001";

    @Mock
    private RefundRecordMapper refundRecordMapper;
    @Mock
    private TenantService tenantService;
    @Mock
    private PaymentOrderService paymentOrderService;
    @Mock
    private PaymentChannelFactory paymentChannelFactory;
    @Mock
    private PaymentConfigProperties paymentConfigProperties;
    @Mock
    private RefundCallbackService refundCallbackService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RefundReconcileTask task;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(true);
        Tenant t = new Tenant();
        t.setId(TENANT_ID);
        when(tenantService.listActiveTenants()).thenReturn(Collections.singletonList(t));
        when(refundRecordMapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0L);
    }

    private RefundRecord processingRecord() {
        RefundRecord r = new RefundRecord();
        r.setId(90001L);
        r.setRefundNo(REFUND_NO);
        r.setPaymentOrderId(70001L);
        r.setStatus(RefundStatus.PROCESSING.getCode());
        r.setTenantId(TENANT_ID);
        return r;
    }

    private void stubScan(RefundRecord... records) {
        when(refundRecordMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(java.util.Arrays.asList(records));
    }

    private PaymentOrder wechatPaymentOrder() {
        PaymentOrder po = new PaymentOrder();
        po.setId(70001L);
        po.setChannel("WECHAT");
        po.setTenantId(TENANT_ID);
        return po;
    }

    private QueryCapableChannel stubQueryCapableChannel() {
        when(paymentOrderService.getById(70001L)).thenReturn(wechatPaymentOrder());
        QueryCapableChannel channel = mock(QueryCapableChannel.class);
        when(paymentChannelFactory.getChannelForTenantNullable(TENANT_ID, "WECHAT")).thenReturn(channel);
        return channel;
    }

    private RefundNotifyResult channelResult(boolean success, String status) {
        RefundNotifyResult r = new RefundNotifyResult();
        r.setSuccess(success);
        r.setOutRefundNo(REFUND_NO);
        r.setStatus(status);
        return r;
    }

    @Test
    void mockMode_skipsEntirely() {
        when(paymentConfigProperties.isMockMode()).thenReturn(true);

        task.scanProcessingRefunds();

        verifyNoInteractions(refundRecordMapper);
        verifyNoInteractions(refundCallbackService);
    }

    @Test
    void channelSuccess_finalizesViaCallbackChain() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScan(processingRecord());
        QueryCapableChannel channel = stubQueryCapableChannel();
        when(channel.queryRefundStatus(REFUND_NO)).thenReturn(channelResult(true, "SUCCESS"));

        task.scanProcessingRefunds();

        ArgumentCaptor<RefundNotifyResult> captor = ArgumentCaptor.forClass(RefundNotifyResult.class);
        verify(refundCallbackService).handleRefundNotify(captor.capture());
        assertEquals(REFUND_NO, captor.getValue().getOutRefundNo());
        assertEquals("SUCCESS", captor.getValue().getStatus());
    }

    @Test
    void channelClosed_finalizesViaCallbackChain() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScan(processingRecord());
        QueryCapableChannel channel = stubQueryCapableChannel();
        when(channel.queryRefundStatus(REFUND_NO)).thenReturn(channelResult(true, "CLOSED"));

        task.scanProcessingRefunds();

        ArgumentCaptor<RefundNotifyResult> captor = ArgumentCaptor.forClass(RefundNotifyResult.class);
        verify(refundCallbackService).handleRefundNotify(captor.capture());
        assertEquals("CLOSED", captor.getValue().getStatus());
    }

    @Test
    void channelStillProcessing_noFinalize() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScan(processingRecord());
        QueryCapableChannel channel = stubQueryCapableChannel();
        when(channel.queryRefundStatus(REFUND_NO)).thenReturn(channelResult(true, "PROCESSING"));

        task.scanProcessingRefunds();

        verify(refundCallbackService, never()).handleRefundNotify(any());
    }

    @Test
    void channelQueryFailure_noFinalize() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScan(processingRecord());
        QueryCapableChannel channel = stubQueryCapableChannel();
        when(channel.queryRefundStatus(REFUND_NO)).thenReturn(channelResult(false, null));

        task.scanProcessingRefunds();

        verify(refundCallbackService, never()).handleRefundNotify(any());
    }

    @Test
    void channelWithoutQueryCapability_skipped() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScan(processingRecord());
        when(paymentOrderService.getById(70001L)).thenReturn(wechatPaymentOrder());
        // 支付宝渠道未实现 RefundQueryCapable（同步即终态）： instanceof 不命中，保守跳过
        when(paymentChannelFactory.getChannelForTenantNullable(TENANT_ID, "WECHAT"))
                .thenReturn(mock(PaymentChannel.class));

        task.scanProcessingRefunds();

        verify(refundCallbackService, never()).handleRefundNotify(any());
    }

    @Test
    void lockNotAcquired_skips() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(false);

        task.scanProcessingRefunds();

        verifyNoInteractions(refundRecordMapper);
    }
}
