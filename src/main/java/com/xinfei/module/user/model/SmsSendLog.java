package com.xinfei.module.user.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 短信发送台账（验证码防盗刷，见 V8__add_sms_send_log.sql / tenant-tables.yml）。
 * <p>全局表，不参与租户隔离：/user/sendMsg 是匿名公开端点，调用时无租户上下文，
 * 频控维度是手机号本身而非租户，故登记在 {@code tenant.ignore-tables} 白名单中。</p>
 *
 * @author 心飞为你飞
 * @since 2026-10-19
 */
@Data
@TableName("sms_send_log")
@Schema(description = "短信发送台账")
public class SmsSendLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "接收验证码的手机号")
    private String phone;

    @Schema(description = "发送场景，当前仅登录验证码")
    private String scene;

    @Schema(description = "发送日期（按日频控计数窗口）")
    private LocalDate sendDate;

    @Schema(description = "记录落库时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
