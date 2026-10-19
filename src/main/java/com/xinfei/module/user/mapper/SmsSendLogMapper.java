package com.xinfei.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinfei.module.user.model.SmsSendLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信发送台账 Mapper。
 * <p>sms_send_log 无 tenant_id 列，已在 tenant-tables.yml 的 ignore-tables 白名单中，
 * 查询无需 {@code @InterceptorIgnore}（与 permission/region 等全局表同理）。</p>
 *
 * @author 心飞为你飞
 * @since 2026-10-19
 */
@Mapper
public interface SmsSendLogMapper extends BaseMapper<SmsSendLog> {
}
