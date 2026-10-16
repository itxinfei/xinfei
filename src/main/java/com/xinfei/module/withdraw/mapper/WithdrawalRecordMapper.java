package com.xinfei.module.withdraw.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinfei.module.withdraw.model.WithdrawalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 提现记录 Mapper 接口
 * </p>
 *
 * @author xinfei
 * @since 2026-09-01
 */
@Mapper
public interface WithdrawalRecordMapper extends BaseMapper<WithdrawalRecord> {
}
