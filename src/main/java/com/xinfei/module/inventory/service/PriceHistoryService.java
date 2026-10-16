package com.xinfei.module.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xinfei.module.inventory.model.PriceHistory;

import java.util.List;

/**
 * <p>
 * 价格历史服务接口
 * </p>
 *
 * @author xinfei
 * @since 2026-09-01
 */
public interface PriceHistoryService extends IService<PriceHistory> {

    /**
     * 记录价格变动
     */
    PriceHistory recordPriceChange(Long materialId, java.math.BigDecimal oldPrice, java.math.BigDecimal newPrice,
            String changeReason, Long operatorId);

    /**
     * 查询物料价格历史
     */
    List<PriceHistory> listByMaterialId(Long materialId);
}
