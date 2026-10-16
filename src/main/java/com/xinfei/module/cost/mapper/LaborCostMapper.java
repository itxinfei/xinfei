package com.xinfei.module.cost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinfei.module.cost.model.LaborCost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人工成本 Mapper 接口
 *
 * @author xinfei
 * @since 2026-08-10
 */
@Mapper
public interface LaborCostMapper extends BaseMapper<LaborCost> {
}
