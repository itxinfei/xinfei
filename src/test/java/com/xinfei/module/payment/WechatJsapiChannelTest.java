package com.xinfei.module.payment;

import com.wechat.pay.java.core.cipher.SignatureResult;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayResponse;
import com.xinfei.module.payment.channel.PayRequest;
import com.xinfei.module.payment.channel.PayResponse;
import com.xinfei.module.payment.channel.real.WechatV3PayChannel;
import com.xinfei.module.payment.channel.sdk.WechatSdkClients;
import com.xinfei.module.payment.model.PaymentChannelConfig;
import com.xinfei.utils.QRCodeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 微信 JSAPI（小程序）下单分支单元测试（miniapp 前置改造 5.3）。
 * <p>Mockito 模拟 SDK 客户端与签名器，验证：</p>
 * <ol>
 *   <li>openid 齐全：返回 wx.requestPayment 五参（appId/timeStamp/nonceStr/package/signType=RSA/paySign）；</li>
 *   <li>openid 缺失：fail-closed，不下发任何调起参数；</li>
 *   <li>payer.openid 正确透传给 SDK prepay 请求。</li>
 * </ol>
 *
 * @author xinfei
 * @since 2026-10-22
 */
class WechatJsapiChannelTest {

    private static final String OPENID = "oXyz-test-openid";
    private static final String PREPAY_ID = "wx201410272009395522657a690389285100";

    private WechatV3PayChannel channel;
    private JsapiService jsapiService;

    private PrepayRequest capturedPrepay;

    @BeforeEach
    void setUp() {
        jsapiService = mock(JsapiService.class);
        com.wechat.pay.java.core.Config wxConfig = mock(com.wechat.pay.java.core.Config.class);
        com.wechat.pay.java.core.cipher.Signer signer = mock(com.wechat.pay.java.core.cipher.Signer.class);
        when(signer.sign(any(String.class)))
                .thenReturn(new SignatureResult("mock-pay-sign", "mock-serial-no"));
        when(wxConfig.createSigner()).thenReturn(signer);
        WechatSdkClients clients = mock(WechatSdkClients.class);
        when(clients.getJsapiService()).thenReturn(jsapiService);
        when(clients.getConfig()).thenReturn(wxConfig);
        when(jsapiService.prepay(any(PrepayRequest.class))).thenAnswer(inv -> {
            capturedPrepay = inv.getArgument(0);
            PrepayResponse resp = new PrepayResponse();
            resp.setPrepayId(PREPAY_ID);
            return resp;
        });

        PaymentChannelConfig config = new PaymentChannelConfig();
        config.setWxAppId("wx-test-appid");
        config.setWxMchId("1900000109");
        config.setPayNotifyUrl("https://example.com/api/payment/notify/WECHAT");
        config.setTenantId(1L);

        channel = new WechatV3PayChannel(clients, config, mock(QRCodeUtil.class));
    }

    private PayRequest jsapiRequest(String openid) {
        PayRequest request = new PayRequest();
        request.setTradeNo("TRD20261022001");
        request.setAmount(new java.math.BigDecimal("18.80"));
        request.setSubject("心飞外卖-订单1");
        request.setPayType("JSAPI");
        request.setOpenid(openid);
        return request;
    }

    @Test
    void jsapiOrderReturnsWxRequestPaymentParams() {
        PayResponse response = channel.createOrder(jsapiRequest(OPENID));

        assertTrue(response.isSuccess(), "JSAPI 下单应成功: err=" + response.getErrorMsg());
        assertEquals("JSAPI", response.getPayType());
        assertNotNull(response.getJsapiParams());
        assertEquals("wx-test-appid", response.getJsapiParams().get("appId"));
        assertEquals("prepay_id=" + PREPAY_ID, response.getJsapiParams().get("package"));
        assertEquals("RSA", response.getJsapiParams().get("signType"));
        assertEquals("mock-pay-sign", response.getJsapiParams().get("paySign"));
        assertNotNull(response.getJsapiParams().get("timeStamp"));
        assertNotNull(response.getJsapiParams().get("nonceStr"));
        // payer.openid 透传给微信下单请求
        assertEquals(OPENID, capturedPrepay.getPayer().getOpenid());
        assertEquals("wx-test-appid", capturedPrepay.getAppid());
    }

    @Test
    void jsapiOrderWithoutOpenidFailsClosed() {
        PayResponse response = channel.createOrder(jsapiRequest(null));

        assertTrue(!response.isSuccess(), "缺 openid 必须失败");
        assertTrue(response.getErrorMsg() != null && response.getErrorMsg().contains("未绑定微信"));
    }
}
