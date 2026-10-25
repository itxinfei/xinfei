package com.xinfei.module.notification.wxpush;

import com.xinfei.module.user.model.User;
import com.xinfei.module.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link WxSubscribePushService} 降级路径单测（P0-1）：
 * 推送是增强能力，所有异常路径必须静默（不影响订单状态机）。
 * 真实微信调用不在此覆盖（需 appid/secret，属真机联调 P2-1）。
 *
 * @author xinfei
 * @since 2026-10-25
 */
class WxSubscribePushServiceTest {

    @Test
    @DisplayName("1. 用户不存在：静默不抛")
    void missingUserQuiet() {
        UserService userService = Mockito.mock(UserService.class);
        Mockito.when(userService.getById(1L)).thenReturn(null);
        WxSubscribePushService service = new WxSubscribePushService(userService);
        assertDoesNotThrow(() -> service.push(
                new OrderConfirmedPushEvent(this, 100L, 1L, 1L)));
    }

    @Test
    @DisplayName("2. 用户无 openid（非微信登录）：静默不抛")
    void noOpenidQuiet() {
        UserService userService = Mockito.mock(UserService.class);
        User u = new User();
        u.setId(1L);
        u.setOpenid(null);
        Mockito.when(userService.getById(1L)).thenReturn(u);
        WxSubscribePushService service = new WxSubscribePushService(userService);
        assertDoesNotThrow(() -> service.push(
                new OrderDeliveredPushEvent(this, 100L, 1L, 1L)));
    }

    @Test
    @DisplayName("3. userId 为空：静默不抛")
    void nullUserIdQuiet() {
        WxSubscribePushService service = new WxSubscribePushService(Mockito.mock(UserService.class));
        assertDoesNotThrow(() -> service.push(
                new OrderConfirmedPushEvent(this, 100L, 1L, null)));
    }

    @Test
    @DisplayName("4. 事件文案：三节点标题与截断口径")
    void eventTexts() {
        assertEquals2("商家已接单", new OrderConfirmedPushEvent(this, 1L, 1L, 1L).title());
        assertEquals2("订单已送达", new OrderDeliveredPushEvent(this, 1L, 1L, 1L).title());
        OrderPickedUpPushEvent picked = new OrderPickedUpPushEvent(this, 1L, 1L, 1L, "张骑手");
        assertEquals2("张骑手已取餐，正在配送途中", picked.detail());
        OrderPickedUpPushEvent pickedAnon = new OrderPickedUpPushEvent(this, 1L, 1L, 1L, null);
        assertEquals2("骑手已取餐，正在配送途中", pickedAnon.detail());
    }

    private static void assertEquals2(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
