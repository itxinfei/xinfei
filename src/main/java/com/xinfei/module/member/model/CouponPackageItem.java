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
import java.time.LocalDateTime;

/**
 * 付费券包明细（包内券模板，一期每模板限一张）
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Data
@TableName("coupon_package_item")
@Schema(description = "付费券包明细")
public class CouponPackageItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "租户ID")
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @Schema(description = "券包ID")
    private Long packageId;

    @Schema(description = "券模板ID")
    private Long templateId;

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

    /** 券模板名称（关联查询回填，非持久化） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "券模板名称（回填）")
    private String templateName;

    /** 券摘要（如 满30减5 / 8.5折，回填） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    @Schema(description = "券摘要（回填）")
    private String templateSummary;
}
