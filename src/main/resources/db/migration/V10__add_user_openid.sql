-- V10: user 表增加微信小程序 openid 列（miniapp 前置改造 5.3）
-- 用途：
--   1. POST /user/wxLogin（jscode2session）将 openid 与用户绑定，凭 openid 快捷登录；
--   2. JSAPI 支付（wx.requestPayment）下单时从当前用户取 openid 作为 payer 标识。
-- 唯一键：同一小程序 appid 下 openid 全局唯一；未绑定为 NULL（唯一键允许多个 NULL）。
ALTER TABLE `user`
    ADD COLUMN `openid` varchar(64) DEFAULT NULL COMMENT '微信小程序openid（jscode2session获取，wxLogin绑定）' AFTER `department_id`,
    ADD UNIQUE KEY `uk_user_openid` (`openid`);
