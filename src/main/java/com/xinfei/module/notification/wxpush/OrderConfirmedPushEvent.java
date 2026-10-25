package com.xinfei.module.notification.wxpush;

/**
 * 商家已接单推送事件（P0-1）：confirmOrder 后发布。
 *
 * @author xinfei
 * @since 2026-10-25
 */
public class OrderConfirmedPushEvent extends OrderPushEvent {

    public OrderConfirmedPushEvent(Object source, Long orderId, Long tenantId, Long userId) {
        super(source, orderId, tenantId, userId);
    }

    @Override
    public String title() { return "商家已接单"; }

    @Override
    public String detail() { return "商家已接单，正在为您准备餐品"; }
}
