package com.xinfei.module.member.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.common.CustomException;
import com.xinfei.common.R;
import com.xinfei.common.RateLimit;
import com.xinfei.module.member.model.CouponPackage;
import com.xinfei.module.member.model.CouponPackageOrder;
import com.xinfei.module.member.service.CouponPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端付费券包接口（O-18）：上架券包列表、创建购买单（支付走 /api/payment/coupon-package/pay）、
 * 我的购买记录。不挂 @RequireEmployee：从登录态取 userId，禁止前端传他人身份。
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@RestController
@RequestMapping("/api/coupon-package")
@Tag(name = "C端付费券包", description = "券包浏览与购买")
public class CustomerCouponPackageController {

    @Autowired
    private CouponPackageService couponPackageService;

    /**
     * 上架券包列表（匿名可看，购买需登录）。
     */
    @GetMapping("/list")
    @RateLimit(maxRequestsPerSecond = 10)
    @Operation(summary = "C端-券包列表", description = "上架中的券包（含包内券摘要）")
    public R<List<CouponPackage>> list() {
        return R.success(couponPackageService.listPublished());
    }

    /**
     * 创建购买单（幂等：同用户同包复用待支付单）；支付由前端调 /api/payment/coupon-package/pay。
     */
    @PostMapping("/buy")
    @RateLimit(maxRequestsPerSecond = 3)
    @Operation(summary = "C端-购买券包", description = "创建待支付购买单（幂等复用），返回单号供发起支付")
    public R<CouponPackageOrder> buy(@RequestParam Long packageId) {
        Long userId = requireUser();
        CouponPackageOrder order = couponPackageService.createOrder(userId, packageId);
        log.info("[券包] 顾客创建购买单: userId={}, packageId={}, orderNo={}", userId, packageId, order.getOrderNo());
        return R.success(order);
    }

    /**
     * 我的购买记录（倒序）。
     */
    @GetMapping("/my")
    @RateLimit(maxRequestsPerSecond = 10)
    @Operation(summary = "C端-我的购买记录", description = "当前登录用户的券包购买记录")
    public R<List<CouponPackageOrder>> my() {
        Long userId = requireUser();
        return R.success(couponPackageService.myOrders(userId));
    }

    /**
     * 取当前登录用户ID。
     */
    private Long requireUser() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new CustomException("请先登录");
        }
        return userId;
    }
}
