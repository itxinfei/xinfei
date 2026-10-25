package com.xinfei.module.notification.wxpush;

/**
 * 订单状态推送域事件基类（P0-1 订阅消息推送）。
 * <p>与 {@link com.xinfei.common.event.OrderDomainEvent} 分开建族：推送事件携带
 * 模板语义（哪个模板、哪些字段值），由 wxpush 监听器消费；避免把推送细节塞进领域事件。</p>
 *
 * @author xinfei
 * @since 2026-10-25
 */
public abstract class OrderPushEvent {

    private final Object source;
    private final Long orderId;
    private final Long tenantId;
    private final Long userId;

    protected OrderPushEvent(Object source, Long orderId, Long tenantId, Long userId) {
        this.source = source;
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.userId = userId;
    }

    public Object getSource() { return source; }
    public Long getOrderId() { return orderId; }
    public Long getTenantId() { return tenantId; }
    public Long getUserId() { return userId; }

    /** 推送标题（模板 name3/thing 字段用） */
    public abstract String title();

    /** 推送正文（thing 字段用，≤20 字） */
    public abstract String detail();
}
