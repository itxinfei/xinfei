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
import java.util.List;

/**
 * 付费券包实体（O-18）：多张优惠券打包售卖；一期口径：包内每模板限一张
 * （受 coupon_user.uk_member_template 约束，放开需迁移改造，未做）。
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Data
@TableName("coupon_package")
@Schema(description = "付费券包")
public class CouponPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：下架 */
    public static final int STATUS_OFF = 0;
    /** 状态：上架 */
    public static final int STATUS_ON = 1;

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "租户ID")
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @Schema(description = "券包名称")
    private String name;

    @Schema(description = "售价（元）")
    private BigDecimal price;

    @Schema(description = "包内券面值/权益合计（展示用）")
    private BigDecimal originalValue;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "图片")
    private String image;

    @Schema(description = "状态 0:下架 1:上架")
    private Integer status;

    @Schema(description = "排序（小在前）")
    private Integer sort;

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

    /** 包内券模板明细（关联查询回填，非持久化） */
    @TableField(exist = false)
    @Schema(description = "包内券模板明细（回填）")
    private List<CouponPackageItem> items;
}
