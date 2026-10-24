package com.xinfei.module.export.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.common.R;
import com.xinfei.common.annotation.RequireEmployee;
import com.xinfei.module.export.service.ExportDataService;
import com.xinfei.module.subsidy.service.SubsidyService;
import com.xinfei.module.export.util.ExportUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据导出Controller
 * 统一提供各业务模块的Excel和PDF导出功能
 * <p>修改点（O-25）：数据构建逻辑整体抽取到 {@link ExportDataService}，
 * 本控制器只负责参数收集与文件响应构建；异步导出任务与同步端点共用同一数据口径。</p>
 *
 * @author xinfei
 * @since 2026-07-09
 */
@Slf4j
@RestController
@RequestMapping("/export")
@Tag(name = "数据导出", description = "订单、菜品、员工等业务数据的Excel/PDF导出功能")
@RequireEmployee
public class ExportController {

    /** 导出数据构建服务（同步/异步共用） */
    @Resource
    private ExportDataService exportDataService;

    /** 餐补服务（部门对账聚合，2026-10-06） */
    @Resource
    private com.xinfei.module.subsidy.service.SubsidyService subsidyService;

    /** 文件名日期格式 */
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ==================== 订单导出 ====================

    /**
     * 导出订单数据 - Excel
     *
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @param status    订单状态（可选）
     * @return Excel文件流
     */
    @GetMapping("/orders/excel")
    @Operation(summary = "导出订单Excel", description = "导出订单数据为Excel文件，支持勾选行导出或按日期/订单号/状态筛选导出")
    public ResponseEntity<?> exportOrdersExcel(
                        @Parameter(description = "开始日期 yyyy-MM-dd（可选）") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期 yyyy-MM-dd（可选）") @RequestParam(required = false) String endDate,
            @Parameter(description = "订单状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "订单号模糊匹配（可选）") @RequestParam(required = false) String number,
            @Parameter(description = "勾选导出的订单ID，逗号分隔（可选，非空时仅导出选中行）") @RequestParam(required = false) String ids) {

        try {
            byte[] bytes = exportDataService.orders("excel", params(
                    "startDate", startDate, "endDate", endDate,
                    "status", status == null ? null : String.valueOf(status), "number", number, "ids", ids));
            return buildFileResponse(bytes, "订单数据", "xlsx");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出订单Excel失败: startDate={}, endDate={}", startDate, endDate, e);
            return buildErrorResponse("订单Excel导出失败，请稍后重试");
        }
    }

    /**
     * 导出订单数据 - PDF
     *
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @param status    订单状态（可选）
     * @return PDF文件流
     */
    @GetMapping("/orders/pdf")
    @Operation(summary = "导出订单PDF", description = "导出订单数据为PDF报表，支持勾选行导出或按日期/订单号/状态筛选导出")
    public ResponseEntity<?> exportOrdersPdf(
                        @Parameter(description = "开始日期 yyyy-MM-dd（可选）") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期 yyyy-MM-dd（可选）") @RequestParam(required = false) String endDate,
            @Parameter(description = "订单状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "订单号模糊匹配（可选）") @RequestParam(required = false) String number,
            @Parameter(description = "勾选导出的订单ID，逗号分隔（可选，非空时仅导出选中行）") @RequestParam(required = false) String ids) {

        try {
            byte[] bytes = exportDataService.orders("pdf", params(
                    "startDate", startDate, "endDate", endDate,
                    "status", status == null ? null : String.valueOf(status), "number", number, "ids", ids));
            return buildFileResponse(bytes, "订单报表", "pdf");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出订单PDF失败: startDate={}, endDate={}", startDate, endDate, e);
            return buildErrorResponse("订单PDF导出失败，请稍后重试");
        }
    }

    // ==================== 菜品导出 ====================

    /**
     * 导出菜品数据 - Excel
     *
     * @param categoryId 分类ID（可选）
     * @return Excel文件流
     */
    @GetMapping("/dishes/excel")
    @Operation(summary = "导出菜品Excel", description = "导出菜品数据为Excel文件，支持勾选行导出或按分类/名称/商品码/状态筛选")
    public ResponseEntity<?> exportDishesExcel(
                        @Parameter(description = "分类ID（可选）") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "菜品名称模糊匹配（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "商品码模糊匹配（可选）") @RequestParam(required = false) String code,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "勾选导出的菜品ID，逗号分隔（可选，非空时仅导出选中行）") @RequestParam(required = false) String ids) {

        try {
            byte[] bytes = exportDataService.dishes("excel", params(
                    "categoryId", categoryId == null ? null : String.valueOf(categoryId),
                    "name", name, "code", code,
                    "status", status == null ? null : String.valueOf(status), "ids", ids));
            return buildFileResponse(bytes, "菜品数据", "xlsx");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出菜品Excel失败: categoryId={}", categoryId, e);
            return buildErrorResponse("菜品Excel导出失败，请稍后重试");
        }
    }

    /**
     * 导出菜品数据 - PDF
     *
     * @param categoryId 分类ID（可选）
     * @return PDF文件流
     */
    @GetMapping("/dishes/pdf")
    @Operation(summary = "导出菜品PDF", description = "导出菜品数据为PDF报表，支持勾选行导出或按分类/名称/商品码/状态筛选")
    public ResponseEntity<?> exportDishesPdf(
                        @Parameter(description = "分类ID（可选）") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "菜品名称模糊匹配（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "商品码模糊匹配（可选）") @RequestParam(required = false) String code,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "勾选导出的菜品ID，逗号分隔（可选，非空时仅导出选中行）") @RequestParam(required = false) String ids) {

        try {
            byte[] bytes = exportDataService.dishes("pdf", params(
                    "categoryId", categoryId == null ? null : String.valueOf(categoryId),
                    "name", name, "code", code,
                    "status", status == null ? null : String.valueOf(status), "ids", ids));
            return buildFileResponse(bytes, "菜品报表", "pdf");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出菜品PDF失败: categoryId={}", categoryId, e);
            return buildErrorResponse("菜品PDF导出失败，请稍后重试");
        }
    }

    // ==================== 员工导出 ====================

    /**
     * 导出员工数据 - Excel
     *
     * @return Excel文件流
     */
    @GetMapping("/employees/excel")
    @Operation(summary = "导出员工Excel", description = "导出员工数据为Excel文件，支持勾选行导出或按姓名/状态筛选")
    public ResponseEntity<?> exportEmployeesExcel(
                        @Parameter(description = "员工姓名模糊匹配（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "勾选导出的员工ID，逗号分隔（可选，非空时仅导出选中行）") @RequestParam(required = false) String ids) {

        try {
            byte[] bytes = exportDataService.employees("excel", params(
                    "name", name, "status", status == null ? null : String.valueOf(status), "ids", ids));
            return buildFileResponse(bytes, "员工数据", "xlsx");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出员工Excel失败", e);
            return buildErrorResponse("员工Excel导出失败，请稍后重试");
        }
    }

    /**
     * 导出员工数据 - PDF
     *
     * @return PDF文件流
     */
    @GetMapping("/employees/pdf")
    @Operation(summary = "导出员工PDF", description = "导出员工数据为PDF报表，支持勾选行导出或按姓名/状态筛选")
    public ResponseEntity<?> exportEmployeesPdf(
                        @Parameter(description = "员工姓名模糊匹配（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "勾选导出的员工ID，逗号分隔（可选，非空时仅导出选中行）") @RequestParam(required = false) String ids) {

        try {
            byte[] bytes = exportDataService.employees("pdf", params(
                    "name", name, "status", status == null ? null : String.valueOf(status), "ids", ids));
            return buildFileResponse(bytes, "员工报表", "pdf");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出员工PDF失败", e);
            return buildErrorResponse("员工PDF导出失败，请稍后重试");
        }
    }

    // ==================== C端用户导出 ====================

    /**
     * 导出C端用户数据 - Excel
     *
     * @return Excel文件流
     */
    @GetMapping("/users/excel")
    @Operation(summary = "导出用户Excel", description = "导出C端用户数据为Excel文件，支持勾选行导出或按姓名/手机号/状态筛选")
    public ResponseEntity<?> exportUsersExcel(
                        @Parameter(description = "用户姓名模糊匹配（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "手机号模糊匹配（可选）") @RequestParam(required = false) String phone,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "勾选导出的用户ID，逗号分隔（可选，非空时仅导出选中行）") @RequestParam(required = false) String ids) {
        try {
            byte[] bytes = exportDataService.users("excel", params(
                    "name", name, "phone", phone,
                    "status", status == null ? null : String.valueOf(status), "ids", ids));
            return buildFileResponse(bytes, "用户数据", "xlsx");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出用户Excel失败", e);
            return buildErrorResponse("用户Excel导出失败，请稍后重试");
        }
    }

    /**
     * 导出C端用户数据 - PDF
     *
     * @return PDF文件流
     */
    @GetMapping("/users/pdf")
    @Operation(summary = "导出用户PDF", description = "导出C端用户数据为PDF报表，支持勾选行导出或按姓名/手机号/状态筛选")
    public ResponseEntity<?> exportUsersPdf(
                        @Parameter(description = "用户姓名模糊匹配（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "手机号模糊匹配（可选）") @RequestParam(required = false) String phone,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "勾选导出的用户ID，逗号分隔（可选，非空时仅导出选中行）") @RequestParam(required = false) String ids) {
        try {
            byte[] bytes = exportDataService.users("pdf", params(
                    "name", name, "phone", phone,
                    "status", status == null ? null : String.valueOf(status), "ids", ids));
            return buildFileResponse(bytes, "用户报表", "pdf");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出用户PDF失败", e);
            return buildErrorResponse("用户PDF导出失败，请稍后重试");
        }
    }

    // ==================== 部门对账导出（企业餐补，2026-10-06） ====================

    /**
     * 导出部门对账 Excel：按用户归属部门聚合有效订单与餐补发放/核销。
     * <p>未挂部门的用户归入「未分配」行；企业自付 = 订单金额 - 餐补核销。</p>
     *
     * @param startDate 开始日期 yyyy-MM-dd（必填）
     * @param endDate   结束日期 yyyy-MM-dd（必填，含当天）
     * @return Excel文件流
     */
    @GetMapping("/subsidy-department/excel")
    @Operation(summary = "导出部门对账Excel", description = "按部门聚合订单数/订单金额/餐补发放/餐补核销/企业自付")
    public ResponseEntity<?> exportSubsidyDepartmentExcel(
            @Parameter(description = "开始日期 yyyy-MM-dd（含）") @RequestParam String startDate,
            @Parameter(description = "结束日期 yyyy-MM-dd（含）") @RequestParam String endDate) {
        try {
            Long tenantId = BaseContext.getCurrentTenantId();
            List<Map<String, Object>> rows = subsidyService.departmentReconciliation(tenantId, startDate, endDate);

            LinkedHashMap<String, String> columns = new LinkedHashMap<>();
            columns.put("departmentName", "部门");
            columns.put("orderCount", "订单数");
            columns.put("orderAmount", "订单金额");
            columns.put("subsidyGranted", "餐补发放");
            columns.put("subsidyUsed", "餐补核销");
            columns.put("selfPay", "企业自付(订单-核销)");

            byte[] bytes = ExportUtil.generateExcelBytes(columns, rows);
            return buildFileResponse(bytes, "部门对账", "xlsx");
        } catch (com.xinfei.common.CustomException ce) {
            return buildErrorResponse(ce.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.error("导出部门对账Excel失败: startDate={}, endDate={}", startDate, endDate, e);
            return buildErrorResponse("部门对账导出失败，请稍后重试");
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 收集端点参数为字符串 Map（null 值丢弃，与服务端「可选参数」语义一致）
     */
    private Map<String, String> params(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                map.put(kv[i], kv[i + 1]);
            }
        }
        return map;
    }

    /**
     * 构建文件下载响应
     * 修改点：统一设置Content-Type和Content-Disposition，确保前端正确识别
     */
    private ResponseEntity<byte[]> buildFileResponse(byte[] bytes, String fileName, String ext) {
        String fullName = fileName + "_" + LocalDateTime.now().format(FILE_DATE_FMT) + "." + ext;
        MediaType mediaType = "pdf".equals(ext)
                ? MediaType.parseMediaType("application/pdf")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(bytes.length);
        // 修改点：手动构造 Content-Disposition，filename* 用 UTF-8 百分号编码，修复中文文件名乱码(????)
        try {
            String encoded = URLEncoder.encode(fullName, "UTF-8").replace("+", "%20");
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"download." + ext + "\"; filename*=UTF-8''" + encoded);
        } catch (UnsupportedEncodingException e) {
            log.warn("文件名编码失败，回退默认文件名: {}", fullName);
            headers.setContentDisposition(ContentDisposition.attachment().filename("download." + ext).build());
        }

        log.info("文件下载响应构建完成: {} ({} bytes)", fullName, bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /**
     * 构建错误响应
     * 修改点：返回JSON格式的错误信息，前端export.js能够正确解析并显示Toast
     */
    private ResponseEntity<R<String>> buildErrorResponse(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(R.error(message), headers, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
