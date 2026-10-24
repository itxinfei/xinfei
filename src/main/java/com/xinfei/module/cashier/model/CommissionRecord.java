package com.xinfei.module.cashier.model;

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
 * 员工提成记录实体（收银提成口径，O-17）
 * <p>收银成功即按收银员当次比例快照落一条：基数 = 实收 - 找零，金额 = 基数 × 比例；
 * uk_commission_cashier（cashier_record_id 唯一）保证一单一提幂等。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Data
@TableName("commission_record")
@Schema(description = "员工提成记录")
public class CommissionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：未结算 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已结算 */
    public static final int STATUS_SETTLED = 1;

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "租户ID")
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @Schema(description = "收银员工ID")
    private Long employeeId;

    @Schema(description = "员工姓名（生成时快照）")
    private String employeeName;

    @Schema(description = "收银记录ID（提成基数来源，租户内唯一）")
    private Long cashierRecordId;

    @Schema(description = "关联订单ID")
    private Long orderId;

    @Schema(description = "提成基数=收银实收-找零（元）")
    private BigDecimal baseAmount;

    @Schema(description = "提成比例(%)（生成时快照，改比例不影响历史）")
    private BigDecimal rate;

    @Schema(description = "提成金额（元，四舍五入到分）")
    private BigDecimal commissionAmount;

    @Schema(description = "所属期间 yyyy-MM（按收银时间）")
    private String period;

    @Schema(description = "状态：0-未结算，1-已结算")
    private Integer status;

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
