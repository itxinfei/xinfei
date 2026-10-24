package com.xinfei.module.notification.listener;

import com.xinfei.common.BaseContext;
import com.xinfei.common.event.OrderCancelledEvent;
import com.xinfei.module.marketing.mapper.MarketingMessageMapper;
import com.xinfei.module.marketing.model.MarketingMessage;
import com.xinfei.module.notification.sse.SseEmitterManager;
import com.xinfei.module.order.mapper.OrderMapper;
import com.xinfei.module.order.model.Orders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 订单取消事件 → 顾客站内消息通知（O-15 事件链首个真实模块消费方）
 * 订单取消后向顾客消息中心（marketing_message，campaignId=0 系统消息）写入
 * 「订单已取消」通知，并对在线用户做 SSE 实时推送；离线由消息中心兜底可见。
 * </p>
 * <p>
 * 与 common/.../OrderCancelledEventListener（事件链路骨架）分工：本监听器是
 * notification 模块对事件的业务消费，二者互不影响，任一失败不阻断取消主流程
 * （取消主事务在事件发布前已提交库存/权益/秒杀/桌台回退，见 OrderStatusFlowServiceImpl）。
 * </p>
 * <p>
 * 异步线程无租户上下文：租户插件对无上下文的 SQL fail-closed 注入 tenant_id=-1，
 * 故进入时以事件携带的 tenantId 手动设置 BaseContext、结束时恢复（与定时任务同口径，
 * 见 schedule/task/OrderTimeoutTask）。
 * </p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Component
public class OrderCancelledNotifyListener {

    private final OrderMapper orderMapper;
    private final MarketingMessageMapper marketingMessageMapper;
    private final SseEmitterManager sseEmitterManager;

    public OrderCancelledNotifyListener(OrderMapper orderMapper, MarketingMessageMapper marketingMessageMapper,
                                        SseEmitterManager sseEmitterManager) {
        this.orderMapper = orderMapper;
        this.marketingMessageMapper = marketingMessageMapper;
        this.sseEmitterManager = sseEmitterManager;
    }

    /**
     * 订单取消后写顾客站内消息（异步，失败仅告警）。
     */
    @EventListener
    @Async("eventListenerExecutor")
    public void onOrderCancelled(OrderCancelledEvent event) {
        Long originalTenantId = BaseContext.getCurrentTenantId();
        try {
            if (event.getTenantId() != null) {
                BaseContext.setCurrentTenantId(event.getTenantId());
            }
            Orders order = orderMapper.selectById(event.getOrderId());
            if (order == null) {
                log.warn("[订单取消通知] 订单不存在，跳过站内通知: orderId={}", event.getOrderId());
                return;
            }
            // 平台拉取单 userId=0（无本地顾客账号），无站内消息收件人
            if (order.getUserId() == null || order.getUserId() <= 0) {
                log.debug("[订单取消通知] 平台单无收件人，跳过: orderId={}", event.getOrderId());
                return;
            }

            String reason = event.getReason();
            String content = reason == null || reason.trim().isEmpty()
                    ? "您的订单 " + order.getNumber() + " 已取消，如有疑问请联系商家"
                    : "您的订单 " + order.getNumber() + " 已取消：" + reason.trim();

            MarketingMessage msg = new MarketingMessage();
            // 系统/业务通知不关联营销活动；campaign_id 列 NOT NULL，用 0 标识系统消息
            msg.setCampaignId(MarketingMessage.CAMPAIGN_SYSTEM);
            msg.setUserId(order.getUserId());
            msg.setPushType(MarketingMessage.PUSH_NOTIFICATION);
            msg.setTitle("订单已取消");
            msg.setContent(content);
            msg.setStatus(MarketingMessage.STATUS_SENT);
            // 异步线程 BaseContext 为空，租户字段显式赋值（metaObject 严格填充只在字段为 null 时生效）
            msg.setTenantId(event.getTenantId());
            marketingMessageMapper.insert(msg);

            // 落库后对在线用户实时推送；离线由消息中心兜底，SSE 失败不影响落库
            sseEmitterManager.sendToUser(order.getUserId(), "message", msg);
            log.info("[订单取消通知] 已写入顾客消息中心: orderId={}, userId={}, number={}",
                    event.getOrderId(), order.getUserId(), order.getNumber());
        } catch (Exception e) {
            // 宽异常兜底：通知失败不影响取消主流程（主事务已提交）
            log.error("[订单取消通知] 写入顾客消息中心失败: orderId={}", event.getOrderId(), e);
        } finally {
            if (originalTenantId != null) {
                BaseContext.setCurrentTenantId(originalTenantId);
            } else {
                BaseContext.remove();
            }
        }
    }
}
