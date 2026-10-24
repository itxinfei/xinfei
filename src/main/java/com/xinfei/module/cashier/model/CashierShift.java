package com.xinfei.module.cashier.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收银交班台账实体（O-29，O-22 交班对账的持久化收尾）
 * <p>员工点「确认交班」时把当时点的当日+本人收款聚合快照落一条（口径复用
 * shiftSummary），台账可查历史、跨日对账；金额均为收银记录的实收/找零口径。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Data
@TableName("cashier_shift")
@Schema(description = "收银交班台账")
public class CashierShift implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "租户ID")
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @Schema(description = "收银员工ID")
    private Long employeeId;

    @Schema(description = "员工姓名（快照）")
    private String employeeName;

    @Schema(description = "班次所属日期（自然日口径）")
    private LocalDate shiftDate;

    @Schema(description = "交班时间（落库时刻）")
    private LocalDateTime closeTime;

    @Schema(description = "本班收款笔数")
    private Integer orderCount;

    @Schema(description = "本班收款总额（元，实收口径）")
    private BigDecimal totalAmount;

    @Schema(description = "本班找零合计（元）")
    private BigDecimal changeAmount;

    @Schema(description = "按支付方式分布JSON（payType/count/amount）")
    private String payMethodSummary;

    @Schema(description = "交班备注（差额说明等）")
    private String remark;

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
