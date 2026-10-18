package com.xinfei.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 创建采购单请求DTO
 *
 * @author xinfei
 * @since 2026-07-09
 */
@Data
public class CreatePurchaseOrderDTO {

    @Schema(description = "供应商ID", required = true, example = "1")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @Schema(description = "操作人", required = true, example = "张三")
    @NotNull(message = "操作人不能为空")
    private String operator;

    // 修改点(2026-10-18 §10.4)：purchase_order.remark varchar(200)，此前无守卫，超长直穿库层 1406
    @Schema(description = "备注", example = "紧急采购")
    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;

    @Schema(description = "凭证图片（逗号分隔相对路径，最多5张）")
    @Size(max = 1000, message = "凭证图片信息过长")
    private String voucherImages;
}
