package com.xinfei.common.event.listener;

import com.xinfei.common.event.OrderCancelledEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 订单取消事件监听器
 * 订单取消（超时/拒单/用户取消）后异步处理：
 * - 清理推荐缓存
 * - 记录通知消息
 * - 更新用户行为数据
 * </p>
 * <p>
 * 使用 @Async 异步执行，不影响订单取消主流程。
 * 监听器失败不会影响订单取消操作（事件已发布，只日志告警）。
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-07-17
 */
@Slf4j
@Component
public class OrderCancelledEventListener {

    /**
     * 处理订单取消事件
     * 异步执行，不阻塞订单取消主流程
     */
    @EventListener
    @Async("eventListenerExecutor")
    public void handleOrderCancelled(OrderCancelledEvent event) {
        Long orderId = event.getOrderId();
        Long tenantId = event.getTenantId();
        String reason = event.getReason();

        log.info("[事件] 订单取消事件触发: orderId={}, tenantId={}, reason={}", orderId, tenantId, reason);

        // 事件链路骨架：业务消费方在各模块内以 @EventListener 自行订阅（与本类并列，互不影响）。
        // 已接入：notification 模块 OrderCancelledNotifyListener（顾客站内消息 + SSE 推送）。
        // 同步回退（库存/积分/券/秒杀名额/桌台）在 OrderStatusFlowServiceImpl 主事务内完成，不走本事件。
    }
}
