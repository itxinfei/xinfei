package com.xinfei.module.export.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.common.R;
import com.xinfei.common.RateLimit;
import com.xinfei.common.annotation.RequireEmployee;
import com.xinfei.common.CustomException;
import com.xinfei.module.export.model.ExportTask;
import com.xinfei.module.export.service.ExportTaskService;
import com.xinfei.module.export.task.ExportTaskRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * 异步导出任务中心 Controller（O-25）
 * <p>导出改为「提交任务 → 后台生成 → 任务中心下载」：create 同步落库排队并交给
 * exportTaskExecutor 异步执行；file 按任务下载生成好的文件。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@RestController
@RequestMapping("/export/tasks")
@Tag(name = "异步导出任务", description = "导出任务提交、进度查询、文件下载与删除")
@RequireEmployee
public class ExportTaskController {

    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private ExportTaskService exportTaskService;

    @Resource
    private ExportTaskRunner exportTaskRunner;

    /**
     * 创建导出任务（请求体 JSON：taskType / fileFormat / params{...}）。
     * 同步落库后异步执行，返回排队中的任务（含ID），前端凭此提示并轮询任务中心。
     */
    @PostMapping
    @RateLimit(maxRequestsPerSecond = 5)
    @Operation(summary = "创建导出任务", description = "提交导出任务并异步生成文件，返回任务信息（状态=排队中）")
    public R<ExportTask> create(@RequestBody CreateReq req) {
        if (req == null) {
            throw new CustomException("请求体不能为空");
        }
        ExportTask task = exportTaskService.create(req.taskType(), req.fileFormat(), req.params());
        try {
            exportTaskRunner.run(task.getId());
        } catch (RejectedExecutionException e) {
            // 队列打满：任务落失败态，明确告知稍后重试（CallerRuns 会让请求线程同步生成，故不用）
            exportTaskService.markFailed(task.getId(), "导出任务队列已满，请稍后再试");
            throw new CustomException("导出任务队列已满，请稍后再试");
        }
        return R.success(task);
    }

    /**
     * 分页查询当前租户的导出任务（创建时间倒序）。
     */
    @GetMapping
    @Operation(summary = "导出任务列表", description = "分页查询当前租户导出任务，按创建时间倒序")
    public R<Page<ExportTask>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "任务类型过滤（可选）") @RequestParam(required = false) String taskType,
            @Parameter(description = "状态过滤（可选）") @RequestParam(required = false) Integer status) {
        return R.success(exportTaskService.page(page, pageSize, taskType, status));
    }

    /**
     * 下载任务生成文件（仅 SUCCESS 且文件存在）。
     */
    @GetMapping("/{id}/file")
    @Operation(summary = "下载导出文件", description = "下载已完成任务生成的文件")
    public ResponseEntity<?> download(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        ExportTask task = exportTaskService.getDownloadable(id);
        Path file = exportTaskService.taskFile(task);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            log.error("[导出任务] 文件读取失败: id={}", id, e);
            throw new CustomException("导出文件读取失败，请重新导出");
        }

        String fullName = task.getFileName() != null ? task.getFileName()
                : ("export_" + LocalDateTime.now().format(FILE_DATE_FMT) + ".xlsx");
        MediaType mediaType = "pdf".equalsIgnoreCase(task.getFileFormat())
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(bytes.length);
        try {
            String encoded = URLEncoder.encode(fullName, "UTF-8").replace("+", "%20");
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"download." + extOf(task) + "\"; filename*=UTF-8''" + encoded);
        } catch (UnsupportedEncodingException e) {
            log.warn("[导出任务] 文件名编码失败: {}", fullName);
        }
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * 删除任务（删文件 + 逻辑删除记录）。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除导出任务", description = "删除任务记录及其生成文件")
    public R<String> delete(
            @Parameter(description = "任务ID") @PathVariable Long id) {
        exportTaskService.delete(id);
        return R.success("导出任务已删除");
    }

    // ==================== 工具 ====================

    private String extOf(ExportTask task) {
        return "pdf".equalsIgnoreCase(task.getFileFormat()) ? "pdf" : "xlsx";
    }

    /** 创建任务请求体（params 值一律字符串，与 @RequestParam 语义一致） */
    public record CreateReq(String taskType, String fileFormat, Map<String, String> params) { }
}
