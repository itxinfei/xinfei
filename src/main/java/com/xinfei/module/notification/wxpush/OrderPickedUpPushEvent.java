package com.xinfei.module.notification.wxpush;

/**
 * 骑手已取餐推送事件（P0-1）：pickupRiderTask 后发布。
 *
 * @author xinfei
 * @since 2026-10-25
 */
public class OrderPickedUpPushEvent extends OrderPushEvent {

    private final String riderName;

    public OrderPickedUpPushEvent(Object source, Long orderId, Long tenantId, Long userId, String riderName) {
        super(source, orderId, tenantId, userId);
        this.riderName = riderName;
    }

    public String getRiderName() { return riderName; }

    @Override
    public String title() { return "骑手已取餐"; }

    @Override
    public String detail() {
        return (riderName != null && !riderName.isBlank() ? riderName : "骑手") + "已取餐，正在配送途中";
    }
}
