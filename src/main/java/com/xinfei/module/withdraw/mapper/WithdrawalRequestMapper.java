package com.xinfei.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinfei.module.withdraw.model.WithdrawalRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 提现申请 Mapper 接口
 * </p>
 *
 * @author xinfei
 * @since 2026-09-01
 */
@Mapper
public interface WithdrawalRequestMapper extends BaseMapper<WithdrawalRequest> {
}
