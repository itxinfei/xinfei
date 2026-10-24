package com.xinfei.module.export.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 异步导出任务实体（O-25 导出任务中心）
 * <p>导出中心各类型导出改为「提交即落库排队、后台异步生成文件、任务中心下载」：
 * status 走 0 排队中 → 1 生成中 → 2 已完成 / 3 失败 的单向流转。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Data
@TableName("export_task")
@Schema(description = "异步导出任务")
public class ExportTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务类型：订单数据 */
    public static final String TYPE_ORDERS = "orders";
    /** 任务类型：菜品数据 */
    public static final String TYPE_DISHES = "dishes";
    /** 任务类型：员工数据 */
    public static final String TYPE_EMPLOYEES = "employees";
    /** 任务类型：经营报表 */
    public static final String TYPE_REPORT = "report";

    /** 状态：排队中（已落库待执行） */
    public static final int STATUS_PENDING = 0;
    /** 状态：生成中 */
    public static final int STATUS_RUNNING = 1;
    /** 状态：已完成（文件可下载） */
    public static final int STATUS_SUCCESS = 2;
    /** 状态：失败 */
    public static final int STATUS_FAILED = 3;

    /** 文件格式：Excel */
    public static final String FORMAT_EXCEL = "excel";
    /** 文件格式：PDF */
    public static final String FORMAT_PDF = "pdf";

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "租户ID")
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    @Schema(description = "发起员工ID")
    private Long employeeId;

    @Schema(description = "发起员工姓名（冗余快照）")
    private String employeeName;

    @Schema(description = "任务类型 orders/dishes/employees/report")
    private String taskType;

    @Schema(description = "文件格式 excel/pdf")
    private String fileFormat;

    @Schema(description = "查询参数JSON")
    private String paramsJson;

    @Schema(description = "状态 0:排队中 1:生成中 2:已完成 3:失败")
    private Integer status;

    @Schema(description = "下载文件名（完成时写入）")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "失败原因")
    private String errorMsg;

    @Schema(description = "完成/失败时间")
    private LocalDateTime finishTime;

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
