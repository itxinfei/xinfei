package com.xinfei.module.order.controller;

import com.xinfei.common.R;
import com.xinfei.module.order.dto.CheckoutPreviewDTO;
import com.xinfei.module.order.dto.CheckoutPreviewRequestDTO;
import com.xinfei.module.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * C 端结算预览（只读）：订单确认页单一数据源，与真实下单同源核价。
 *
 * @author xinfei
 * @since 2026-09-24
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
@Tag(name = "C端结算预览", description = "购物车结算实时核价（只读）")
public class CustomerCheckoutController {

    @Autowired
    private OrderService orderService;

    /**
     * 结算预览：按收货地址 + 当前购物车 + 所选券实时核价，不产生任何写（不扣库存/不抢锁/不核销券）。
     *
     * @param request 预览请求（addressBookId + usedCouponId）
     * @return 结算预览视图
     */
    @PostMapping("/preview")
    @Operation(summary = "结算预览", description = "只读核价，返回明细/赠品/各项优惠/配送费/应付，与下单同源")
    public R<CheckoutPreviewDTO> preview(@Valid @RequestBody CheckoutPreviewRequestDTO request) {
        return R.success(orderService.previewCheckout(request));
    }
}
