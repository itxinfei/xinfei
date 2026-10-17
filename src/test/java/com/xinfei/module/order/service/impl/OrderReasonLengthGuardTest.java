package com.xinfei.module.order.service.impl;

import com.xinfei.common.CustomException;
import com.xinfei.module.order.service.statusflow.impl.OrderStatusFlowServiceImpl;
import com.xinfei.module.payment.service.impl.RefundRecordServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 锁定「拒单/取消/申请售后原因长度无上限」的修复。
 *
 * <p>{@code orders.cancel_reason}、{@code refund_record.reason} 均为 varchar(255)，
 * 而服务端此前只校验非空、不校验长度：超过 255 字的 {@code reason} 会走到 UPDATE 时抛
 * MySQL 1406 Data too long，整个 {@code @Transactional} 状态流转回滚——表现为"拒单永远失败"。
 * 三处守卫都在读库之前抛出，故本测试直接实例化服务、不连库即可断言。</p>
 */
class OrderReasonLengthGuardTest {

    private static String repeat(String unit, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("拒单原因超过200字 → 拒单前抛出业务异常，不落库")
    void rejectOrderRejectsOverLongReason() {
        OrderStatusFlowServiceImpl service = new OrderStatusFlowServiceImpl();
        CustomException ex = assertThrows(CustomException.class,
                () -> service.rejectOrder(1L, repeat("原", 201)));
        assertEquals("拒单原因不能超过200个字符", ex.getMessage());
    }

    @Test
    @DisplayName("取消原因超过200字 → 取消前抛出业务异常")
    void cancelOrderRejectsOverLongReason() {
        OrderStatusFlowServiceImpl service = new OrderStatusFlowServiceImpl();
        CustomException ex = assertThrows(CustomException.class,
                () -> service.cancelOrder(1L, repeat("因", 201)));
        assertEquals("取消原因不能超过200个字符", ex.getMessage());
    }

    @Test
    @DisplayName("申请售后原因超过200字 → 建单前抛出业务异常")
    void applyUserRefundRejectsOverLongReason() {
        RefundRecordServiceImpl service = new RefundRecordServiceImpl();
        CustomException ex = assertThrows(CustomException.class,
                () -> service.applyUserRefund(1L, repeat("因", 201)));
        assertEquals("退款原因不能超过200个字符", ex.getMessage());
    }

    @Test
    @DisplayName("200字以内原因不被守卫拦下（服务未接入 mapper，只可能以别的异常失败）")
    void normalReasonNotBlockedByGuard() {
        OrderStatusFlowServiceImpl service = new OrderStatusFlowServiceImpl();
        Throwable t = assertThrows(Throwable.class,
                () -> service.rejectOrder(1L, repeat("原", 200)));
        boolean blockedByGuard = t instanceof CustomException
                && String.valueOf(t.getMessage()).contains("不能超过200个字符");
        assertFalse(blockedByGuard, "恰好 200 字不应被长度守卫拦下，实际异常：" + t);
    }
}
