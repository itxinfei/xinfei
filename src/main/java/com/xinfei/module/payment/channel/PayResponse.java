package com.xinfei.module.payment.channel;

import lombok.Data;

/**
 * <p>
 * 支付响应结果封装类。
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-07-09
 */
@Data
public class PayResponse {
    /** 是否成功 */
    private boolean success;
    /** 商户支付单号（PaymentOrder.tradeNo，回调 out_trade_no 用，供沙箱收银台发起模拟支付） */
    private String tradeNo;
    /** 渠道交易号 */
    private String channelTradeNo;
    /** 支付URL */
    private String payUrl;
    /** 二维码URL */
    private String qrCodeUrl;
    /** 原始响应 */
    private String rawResponse;
    /** 实际支付方式：NATIVE / H5 / PC */
    private String payType;
    /** 是否 mock 渠道：C 端据此区分真实扫码轮询与沙箱模拟回调 */
    private boolean mockMode;
    /** JSAPI 小程序调起参数（wx.requestPayment 五参：appId/timeStamp/nonceStr/package/signType/paySign）；miniapp 前置改造 5.3 */
    private java.util.Map<String, String> jsapiParams;
    /** 错误信息 */
    private String errorMsg;
}
