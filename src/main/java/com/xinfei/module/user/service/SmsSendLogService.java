package com.xinfei.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xinfei.module.user.model.SmsSendLog;

/**
 * 短信发送台账服务：落库一条记录 + 按日计数，作为 Redis 频控之外的 DB 权威兜底，
 * 防止 Redis 重启导致 sms:daily:* 计数键丢失而架空每日上限（见功能测试报告 §13）。
 *
 * @author 心飞为你飞
 * @since 2026-10-19
 */
public interface SmsSendLogService extends IService<SmsSendLog> {

    /**
     * 记录一次验证码发送。
     *
     * @param phone 手机号
     * @param scene 发送场景（当前固定 "login"）
     */
    void record(String phone, String scene);

    /**
     * 统计该手机号今日已发送条数（不依赖 Redis，重启后依然有效）。
     *
     * @param phone 手机号
     * @return 今日已发送条数
     */
    long countTodayByPhone(String phone);

    /**
     * 统计全站今日总发送条数（全局熔断兜底）。
     *
     * @return 今日全站总条数
     */
    long countTodayTotal();
}
