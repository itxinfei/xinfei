package com.xinfei.dto;

import com.xinfei.module.order.model.OrderDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * <p>
 * 堂食扫码下单请求DTO
 * </p>
 *
 * @author 心飞为你飞
 * @since 2024-01-01
 */
@Data
@Schema(description = "堂食扫码下单请求")
public class EatInOrderRequest {

    @NotNull(message = "订单信息不能为空")
    @Valid
    @Schema(description = "订单基本信息", required = true)
    private OrderInfo order;

    @NotEmpty(message = "订单明细不能为空")
    @Schema(description = "订单明细列表", required = true)
    @Size(max = 100, message = "订单明细不能超过100项")
    private List<OrderDetail> orderDetails;

    /**
     * OrderInfo。
     */
    @Schema(description = "订单信息")
    @Data
    public static class OrderInfo {
        @NotNull(message = "桌台ID不能为空")
        @Schema(description = "桌台ID", required = true, example = "1")
        private Long tableId;

        @Size(max = 32, message = "桌台名称不能超过32个字符")
        @Schema(description = "桌台名称", example = "A01")
        private String tableName;

        @Size(max = 50, message = "联系人姓名不能超过50个字符")
        @Schema(description = "联系人姓名", example = "张三")
        private String userName;

        @Schema(description = "手机号", example = "13800138000")
        private String phone;

        @Size(max = 100, message = "备注不能超过100个字符")
        @Schema(description = "备注", example = "少放辣")
        private String remark;

        @Schema(description = "支付方式：1=现金，2=微信，3=支付宝，4=银行卡，5=会员储值，6=货到付款", example = "1")
        private Integer payMethod;

        @Schema(description = "用餐人数", example = "4")
        private Integer customerCount;
    }
}

