package com.xinfei.module.payment.channel.notify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * NotifyUrlRouter 租户路由参数拼接单测：支付/退款回调共用，
 * 验证 query 追加口径（?/&）与空值原样返回（P0-3a 退款回调漏拼租户路由的回归锚点）。
 *
 * @author xinfei
 * @since 2026-10-16
 */
class NotifyUrlRouterTest {

    @Test
    void withTenant_noQuery_appendsQuestionMark() {
        assertEquals("https://api.example.com/api/payment/refund-notify/WECHAT?tenantId=9",
                NotifyUrlRouter.withTenant("https://api.example.com/api/payment/refund-notify/WECHAT", 9L));
    }

    @Test
    void withTenant_existingQuery_appendsAmpersand() {
        assertEquals("https://x/y?a=b&tenantId=42",
                NotifyUrlRouter.withTenant("https://x/y?a=b", 42L));
    }

    @Test
    void withTenant_nullUrlOrTenant_returnsAsIs() {
        assertNull(NotifyUrlRouter.withTenant(null, 1L));
        assertEquals("", NotifyUrlRouter.withTenant("", 1L));
        assertEquals("  ", NotifyUrlRouter.withTenant("  ", 1L));
        assertEquals("https://x/y", NotifyUrlRouter.withTenant("https://x/y", null));
    }
}
