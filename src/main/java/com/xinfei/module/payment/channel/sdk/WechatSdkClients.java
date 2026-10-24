package com.xinfei.module.payment.channel.sdk;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;

/**
 * 微信支付 APIv3 SDK 客户端集合。
 * <p>一份商户配置构建一次（{@code RSAAutoCertificateConfig} 构建时会下载并校验平台证书），
 * 随真实渠道按「租户 + 渠道」缓存复用，避免重复构建与证书下载。</p>
 *
 * @author xinfei
 * @since 2026-09-21
 */
public class WechatSdkClients {

    private final Config config;
    private final NativePayService nativePayService;
    private final H5Service h5Service;
    private final RefundService refundService;
    /** JSAPI（小程序）下单服务：miniapp 前置改造 5.3 */
    private final JsapiService jsapiService;

    public WechatSdkClients(Config config, NativePayService nativePayService,
                            H5Service h5Service, RefundService refundService) {
        this(config, nativePayService, h5Service, refundService, null);
    }

    public WechatSdkClients(Config config, NativePayService nativePayService,
                            H5Service h5Service, RefundService refundService, JsapiService jsapiService) {
        this.config = config;
        this.nativePayService = nativePayService;
        this.h5Service = h5Service;
        this.refundService = refundService;
        this.jsapiService = jsapiService;
    }

    public Config getConfig() {
        return config;
    }

    public NativePayService getNativePayService() {
        return nativePayService;
    }

    public H5Service getH5Service() {
        return h5Service;
    }

    public RefundService getRefundService() {
        return refundService;
    }

    public JsapiService getJsapiService() {
        return jsapiService;
    }
}
