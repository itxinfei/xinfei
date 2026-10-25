package com.xinfei.module.notification.wxpush;

/**
 * 订单送达/完成推送事件（P0-1）：deliverRiderOrder 与 completeOrder（堂食/自提路径）后发布。
 *
 * @author xinfei
 * @since 2026-10-25
 */
public class OrderDeliveredPushEvent extends OrderPushEvent {

    public OrderDeliveredPushEvent(Object source, Long orderId, Long tenantId, Long userId) {
        super(source, orderId, tenantId, userId);
    }

    @Override
    public String title() { return "订单已送达"; }

    @Override
    public String detail() { return "订单已送达，感谢您的惠顾，欢迎评价"; }
}
