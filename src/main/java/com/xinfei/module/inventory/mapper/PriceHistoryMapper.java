package com.xinfei.module.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinfei.module.inventory.model.PriceHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 价格历史 Mapper 接口
 * </p>
 *
 * @author xinfei
 * @since 2026-09-01
 */
@Mapper
public interface PriceHistoryMapper extends BaseMapper<PriceHistory> {
}
