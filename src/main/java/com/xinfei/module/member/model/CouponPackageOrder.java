package com.xinfei.module.member.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 付费券包购买单（O-18）：支付成功（bizType=COUPON_PACKAGE 支付单回调）后自动拆包发券。
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Data
@TableName("coupon_package_order")
@Schema(description = "付费券包购买单")
public class CouponPackageOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待支付 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已支付（已发券） */
    public static final int STATUS_PAID = 1;
    /** 状态：已关闭 */
    public static final int STATUS_CLOSED = 2;

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "租户ID")
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @Schema(description = "购买单号")
    private String orderNo;

    @Schema(description = "购买用户ID")
    private Long userId;

    @Schema(description = "券包ID")
    private Long packageId;

    @Schema(description = "券包名称快照")
    private String packageName;

    @Schema(description = "支付金额（下单时快照）")
    private BigDecimal price;

    @Schema(description = "状态 0:待支付 1:已支付(已发券) 2:已关闭")
    private Integer status;

    @Schema(description = "支付时间")
    private LocalDateTime paidTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "创建人ID")
    @TableField(fill = FieldFill.INSERT)
    private Long createUser;

    @Schema(description = "修改人ID")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;

    /** 逻辑删除：0=未删除，1=已删除 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
