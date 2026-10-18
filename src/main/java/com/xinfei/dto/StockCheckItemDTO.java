package com.xinfei.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 盘点明细项DTO
 *
 * @author xinfei
 * @since 2026-07-09
 */
@Data
public class StockCheckItemDTO {

    @Schema(description = "食材ID", required = true, example = "1")
    @NotNull(message = "食材ID不能为空")
    private Long materialId;

    @Schema(description = "系统库存（可选，完成盘点时由后端自动读取）", example = "100.5")
    private BigDecimal systemStock;

    @Schema(description = "实际库存（录入实盘和完成盘点时必填）", example = "98.0")
    private BigDecimal actualStock;

    // 修改点(2026-10-18 §10.4)：stock_check_detail.remark varchar(200)，与 §4.1 族同口径补守卫（潜伏面）
    @Schema(description = "备注", example = "部分食材损耗")
    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;
}
