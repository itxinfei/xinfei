package com.xinfei.module.payment.task;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.xinfei.module.payment.channel.PayResponse;
import com.xinfei.module.payment.channel.PaymentChannel;
import com.xinfei.module.payment.channel.PaymentChannelFactory;
import com.xinfei.module.payment.config.PaymentConfigProperties;
import com.xinfei.module.payment.model.PaymentOrder;
import com.xinfei.module.payment.service.PaymentOrderService;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PaymentReconcileTask} 单元测试：掉单补偿决策。
 * <p>
 * 断言聚焦：mock 模式跳过、渠道查单成功才补联动、未成功/渠道不可用/单笔异常都不动本地状态。
 * lambdaQuery 链按项目惯例显式 mock（深桩对 MP 泛型 Children 解析不稳）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PaymentReconcileTaskTest {

    @Mock
    private PaymentOrderService paymentOrderService;

    @Mock
    private PaymentChannelFactory paymentChannelFactory;

    @Mock
    private PaymentConfigProperties paymentConfigProperties;

    @Mock
    private TenantService tenantService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private PaymentReconcileTask task;

    @SuppressWarnings("unchecked")
    private LambdaQueryChainWrapper<PaymentOrder> chain;

    private static final Long TENANT_ID = 999L;

    @BeforeEach
    void setUp() {
        chain = org.mockito.Mockito.mock(LambdaQueryChainWrapper.class);
        when(paymentOrderService.lambdaQuery()).thenReturn(chain);
        when(chain.le(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), any()))
                .thenReturn(chain);
        when(chain.ge(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), any()))
                .thenReturn(chain);
        when(chain.eq(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), any()))
                .thenReturn(chain);
        // scanForTenant 用 in(status, PENDING, CLOSED) 双值变长参数，两个 any() 精确匹配两个元素
        when(chain.in(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class),
                any(), any()))
                .thenReturn(chain);
        when(chain.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class)))
                .thenReturn(chain);
        when(chain.last(anyString())).thenReturn(chain);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(true);
    }

    private PaymentOrder pending(String tradeNo, String channel) {
        PaymentOrder po = new PaymentOrder();
        po.setId(1L);
        po.setTradeNo(tradeNo);
        po.setChannel(channel);
        po.setStatus(PaymentOrder.STATUS_PENDING);
        po.setAmount(new BigDecimal("50.00"));
        po.setTenantId(TENANT_ID);
        return po;
    }

    private void stubScanResult(PaymentOrder... orders) {
        when(chain.list()).thenReturn(Arrays.asList(orders));
        Tenant t = new Tenant();
        t.setId(TENANT_ID);
        when(tenantService.listActiveTenants()).thenReturn(Collections.singletonList(t));
    }

    @Test
    void mockMode_skipsEntirely() {
        when(paymentConfigProperties.isMockMode()).thenReturn(true);

        task.scanPendingPayments();

        verify(paymentChannelFactory, never()).getChannelForTenantNullable(anyLong(), anyString());
        verify(paymentOrderService, never()).handlePaymentSuccess(anyString(), any());
    }

    @Test
    void channelPaidButLocalPending_compensatesSuccess() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScanResult(pending("TR-001", "ALIPAY"));

        PaymentChannel channel = org.mockito.Mockito.mock(PaymentChannel.class);
        when(paymentChannelFactory.getChannelForTenantNullable(TENANT_ID, "ALIPAY")).thenReturn(channel);
        PayResponse ok = new PayResponse();
        ok.setSuccess(true);
        ok.setChannelTradeNo("ALI-TXN-777");
        when(channel.queryOrder("TR-001")).thenReturn(ok);

        task.scanPendingPayments();

        verify(paymentOrderService).handlePaymentSuccess("TR-001", "ALI-TXN-777");
    }

    @Test
    void channelNotPaid_leavesPendingUntouched() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScanResult(pending("TR-002", "WECHAT"));

        PaymentChannel channel = org.mockito.Mockito.mock(PaymentChannel.class);
        when(paymentChannelFactory.getChannelForTenantNullable(TENANT_ID, "WECHAT")).thenReturn(channel);
        PayResponse notPaid = new PayResponse();
        notPaid.setSuccess(false);
        notPaid.setErrorMsg("微信订单当前状态: NOTPAY");
        when(channel.queryOrder("TR-002")).thenReturn(notPaid);

        task.scanPendingPayments();

        verify(paymentOrderService, never()).handlePaymentSuccess(anyString(), any());
    }

    @Test
    void channelUnavailable_skipsWithoutThrow() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScanResult(pending("TR-003", "ALIPAY"));
        when(paymentChannelFactory.getChannelForTenantNullable(TENANT_ID, "ALIPAY")).thenReturn(null);

        task.scanPendingPayments();

        verify(paymentOrderService, never()).handlePaymentSuccess(anyString(), any());
    }

    @Test
    void singleQueryFailure_doesNotBlockBatch() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        stubScanResult(pending("TR-FAIL", "ALIPAY"), pending("TR-OK", "ALIPAY"));

        PaymentChannel channel = org.mockito.Mockito.mock(PaymentChannel.class);
        when(paymentChannelFactory.getChannelForTenantNullable(TENANT_ID, "ALIPAY")).thenReturn(channel);
        when(channel.queryOrder("TR-FAIL")).thenThrow(new RuntimeException("渠道网络超时"));
        PayResponse ok = new PayResponse();
        ok.setSuccess(true);
        ok.setChannelTradeNo("ALI-TXN-888");
        when(channel.queryOrder("TR-OK")).thenReturn(ok);

        task.scanPendingPayments();

        // 第一笔异常不影响第二笔补偿
        verify(paymentOrderService).handlePaymentSuccess(eq("TR-OK"), eq("ALI-TXN-888"));
        verify(paymentOrderService, never()).handlePaymentSuccess(eq("TR-FAIL"), any());
    }

    @Test
    void lockNotAcquired_skips() {
        when(paymentConfigProperties.isMockMode()).thenReturn(false);
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any())).thenReturn(false);

        task.scanPendingPayments();

        verify(tenantService, never()).listActiveTenants();
    }
}
