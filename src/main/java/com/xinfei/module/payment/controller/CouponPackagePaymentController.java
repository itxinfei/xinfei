package com.xinfei.module.payment.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.common.R;
import com.xinfei.common.RateLimit;
import com.xinfei.common.RateLimitType;
import com.xinfei.module.member.model.CouponPackageOrder;
import com.xinfei.module.member.service.CouponPackageService;
import com.xinfei.module.payment.channel.PayRequest;
import com.xinfei.module.payment.channel.PayResponse;
import com.xinfei.module.payment.channel.PaymentChannel;
import com.xinfei.module.payment.channel.PaymentChannelFactory;
import com.xinfei.module.payment.config.PaymentConfigProperties;
import com.xinfei.module.payment.model.PaymentOrder;
import com.xinfei.module.payment.service.PaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 付费券包在线支付（O-18）。
 * <p>与 {@code RechargePaymentController} 同构：对 PENDING 购买单创建
 * bizType=COUPON_PACKAGE 的支付单并调起渠道；支付成功回调由
 * {@code handlePaymentSuccess} 联动 {@code CouponPackageService.deliverPaid} 拆包发券。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@RestController
@RequestMapping("/api/payment/coupon-package")
@Tag(name = "券包支付", description = "对券包购买单发起在线支付，支付成功自动发券")
public class CouponPackagePaymentController {

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private PaymentChannelFactory paymentChannelFactory;

    @Autowired
    private PaymentConfigProperties paymentConfigProperties;

    @Autowired
    private CouponPackageService couponPackageService;

    @Autowired
    private com.xinfei.module.user.service.UserService userService;

    /**
     * 发起券包购买支付（body: orderNo=购买单号, channel=WECHAT, payType=JSAPI）。
     */
    @PostMapping("/pay")
    @RateLimit(maxRequestsPerSecond = 3, type = RateLimitType.USER)
    @Operation(summary = "券包购买支付", description = "对PENDING购买单发起在线支付，支付成功回调自动拆包发券")
    public R<PayResponse> pay(@Valid @RequestBody Map<String, String> body, HttpServletRequest httpRequest) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            return R.error("请先登录");
        }
        String orderNo = body == null ? null : body.get("orderNo");
        String channel = body == null || body.get("channel") == null ? "WECHAT" : body.get("channel");
        String payType = body == null ? null : body.get("payType");
        if (orderNo == null || orderNo.isEmpty()) {
            return R.error("缺少购买单号");
        }
        // 归属校验：从「我的购买记录」里找该单，不存在/非本人统一文案防探测
        CouponPackageOrder order = couponPackageService.myOrders(userId).stream()
                .filter(o -> orderNo.equals(o.getOrderNo()))
                .findFirst().orElse(null);
        if (order == null) {
            return R.error("购买单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != CouponPackageOrder.STATUS_PENDING) {
            return R.error("购买单当前状态不允许支付");
        }
        BigDecimal payAmount = order.getPrice();
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return R.error("券包金额异常");
        }

        PaymentOrder po = paymentOrderService.createPaymentOrderForBiz(
                PaymentOrder.BIZ_COUPON_PACKAGE, order.getId(), channel, payAmount);

        PaymentChannel paymentChannel = paymentChannelFactory.getChannel(channel);
        PayRequest request = new PayRequest();
        request.setTradeNo(po.getTradeNo());
        request.setAmount(payAmount);
        request.setSubject("心飞券包-" + order.getPackageName());
        request.setPayType(payType);
        request.setClientIp(resolveClientIp(httpRequest));
        // JSAPI（小程序）payer 标识从登录用户档案取，不采信前端入参（与订单/充值同口径）
        if ("JSAPI".equalsIgnoreCase(request.getPayType())) {
            com.xinfei.module.user.model.User payUser = userService.getById(userId);
            if (payUser == null || payUser.getOpenid() == null || payUser.getOpenid().isBlank()) {
                return R.error("当前账号未绑定微信，请先使用微信登录绑定后再支付");
            }
            request.setOpenid(payUser.getOpenid());
        }
        PayResponse response = paymentChannel.createOrder(request);
        response.setTradeNo(po.getTradeNo());
        response.setMockMode(paymentConfigProperties.isMockMode());
        log.info("[券包支付] 发起购买支付: orderNo={}, channel={}, tradeNo={}", orderNo, channel, po.getTradeNo());
        return R.success(response);
    }

    /**
     * 解析客户端真实 IP（与订单/充值支付同口径）。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
