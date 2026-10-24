package com.xinfei.module.notification.listener;

import com.xinfei.common.BaseContext;
import com.xinfei.common.event.OrderCancelledEvent;
import com.xinfei.module.marketing.mapper.MarketingMessageMapper;
import com.xinfei.module.marketing.model.MarketingMessage;
import com.xinfei.module.notification.sse.SseEmitterManager;
import com.xinfei.module.order.mapper.OrderMapper;
import com.xinfei.module.order.model.Orders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrderCancelledNotifyListener} 单元测试（O-15）：订单取消事件 →
 * 顾客站内消息（campaignId=0 系统消息 + SSE 推送）；平台单（userId=0）与
 * 订单不存在两种场景不落消息；异步线程租户上下文用后必须恢复。
 *
 * @author xinfei
 * @since 2026-10-24
 */
@ExtendWith(MockitoExtension.class)
class OrderCancelledNotifyListenerTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private MarketingMessageMapper marketingMessageMapper;

    @Mock
    private SseEmitterManager sseEmitterManager;

    @InjectMocks
    private OrderCancelledNotifyListener listener;

    @AfterEach
    void cleanContext() {
        BaseContext.remove();
    }

    private Orders order(Long userId) {
        Orders order = new Orders();
        order.setId(990001L);
        order.setNumber("20261024001");
        order.setUserId(userId);
        order.setTenantId(999L);
        return order;
    }

    @Test
    void writesSystemMessageAndPushesSseForUserOrder() {
        when(orderMapper.selectById(990001L)).thenReturn(order(990010L));

        listener.onOrderCancelled(new OrderCancelledEvent(this, 990001L, 999L, "商家忙碌，暂时无法接单"));

        ArgumentCaptor<MarketingMessage> captor = ArgumentCaptor.forClass(MarketingMessage.class);
        verify(marketingMessageMapper).insert(captor.capture());
        MarketingMessage msg = captor.getValue();
        assertEquals(MarketingMessage.CAMPAIGN_SYSTEM, msg.getCampaignId());
        assertEquals(990010L, msg.getUserId());
        assertEquals("订单已取消", msg.getTitle());
        assertTrue(msg.getContent().contains("20261024001"));
        assertTrue(msg.getContent().contains("商家忙碌"));
        assertEquals(MarketingMessage.PUSH_NOTIFICATION, msg.getPushType());
        assertEquals(MarketingMessage.STATUS_SENT, msg.getStatus());
        // 异步线程显式赋租户（metaObject 填充只在 null 时生效，事件租户必须落库）
        assertEquals(999L, msg.getTenantId());
        verify(sseEmitterManager).sendToUser(990010L, "message", msg);
    }

    @Test
    void skipsPlatformOrderWithoutLocalUser() {
        // 平台拉取单 userId=0：无站内消息收件人，不落库不推送
        when(orderMapper.selectById(990002L)).thenReturn(order(0L));

        listener.onOrderCancelled(new OrderCancelledEvent(this, 990002L, 999L, "超时未接单"));

        verify(marketingMessageMapper, never()).insert(any(MarketingMessage.class));
        verify(sseEmitterManager, never()).sendToUser(anyLong(), anyString(), any());
    }

    @Test
    void skipsWhenOrderMissing() {
        when(orderMapper.selectById(990003L)).thenReturn(null);

        listener.onOrderCancelled(new OrderCancelledEvent(this, 990003L, 999L, null));

        verify(marketingMessageMapper, never()).insert(any(MarketingMessage.class));
    }

    @Test
    void defaultContentWhenReasonBlankAndRestoresTenantContext() {
        when(orderMapper.selectById(990004L)).thenReturn(order(990011L));

        listener.onOrderCancelled(new OrderCancelledEvent(this, 990004L, 999L, "  "));

        ArgumentCaptor<MarketingMessage> captor = ArgumentCaptor.forClass(MarketingMessage.class);
        verify(marketingMessageMapper).insert(captor.capture());
        assertTrue(captor.getValue().getContent().contains("已取消"));
        assertTrue(captor.getValue().getContent().contains("如有疑问请联系商家"));
        // 进入前无租户上下文（异步线程的真实形态），处理后必须清掉，不能残留 999 污染线程池
        assertNull(BaseContext.getCurrentTenantId());
    }
}
