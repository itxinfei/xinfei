package com.xinfei.module.cashier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinfei.module.cashier.model.CommissionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工提成记录 Mapper
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Mapper
public interface CommissionRecordMapper extends BaseMapper<CommissionRecord> {
}
