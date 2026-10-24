package com.xinfei.module.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.common.BaseContext;
import com.xinfei.common.CustomException;
import com.xinfei.common.ObjectMapperHolder;
import com.xinfei.common.utils.PageUtils;
import com.xinfei.module.auth.model.Employee;
import com.xinfei.module.auth.service.EmployeeService;
import com.xinfei.module.export.mapper.ExportTaskMapper;
import com.xinfei.module.export.model.ExportTask;
import com.xinfei.module.export.service.ExportDataService;
import com.xinfei.module.export.service.ExportTaskService;
import com.xinfei.module.notification.sse.EmployeeSseManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步导出任务服务实现（O-25）
 * <p>文件落盘路径：{xinfei.export.dir，默认 {user.dir}/data/exports}/{yyyyMM}/{taskId}.{ext}，
 * 由 taskId+格式确定性推导（下载/清扫共用同一函数，无需存路径列）；
 * 目录非静态伺服，读取只经带会话鉴权的下载端点。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Service
public class ExportTaskServiceImpl implements ExportTaskService {

    /** 排队/生成中超时阈值：超过视为卡死（应用重启丢上下文等），标记失败 */
    private static final int STUCK_TIMEOUT_HOURS = 1;

    /** 终态任务文件保留期：超过后清扫任务删文件 + 逻辑删除记录 */
    private static final int RETENTION_DAYS = 7;

    /** params_json 上限（text 列，V15）：1000 个勾选 ID 序列化约 8KB，留足余量并防滥用 */
    private static final int MAX_PARAMS_JSON_LENGTH = 60000;

    private static final DateTimeFormatter MONTH_DIR_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter FILE_STAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 任务类型中文名（下载文件名用） */
    private static final Map<String, String> TYPE_LABELS = Map.of(
            ExportTask.TYPE_ORDERS, "订单数据",
            ExportTask.TYPE_DISHES, "菜品数据",
            ExportTask.TYPE_EMPLOYEES, "员工数据",
            ExportTask.TYPE_USERS, "用户数据",
            ExportTask.TYPE_REPORT, "经营报表");

    @Resource
    private ExportTaskMapper exportTaskMapper;

    @Resource
    private ExportDataService exportDataService;

    @Resource
    private EmployeeSseManager employeeSseManager;

    @Resource
    private EmployeeService employeeService;

    /** 导出文件存储根目录（空则默认 {user.dir}/data/exports） */
    @Value("${xinfei.export.dir:}")
    private String configuredDir;

    private final ObjectMapper objectMapper = ObjectMapperHolder.getDefault();

    @Override
    public ExportTask create(String taskType, String fileFormat, Map<String, String> params) {
        if (!TYPE_LABELS.containsKey(taskType)) {
            throw new CustomException("不支持的导出类型");
        }
        if (!ExportTask.FORMAT_EXCEL.equals(fileFormat) && !ExportTask.FORMAT_PDF.equals(fileFormat)) {
            throw new CustomException("不支持的导出格式，仅支持 excel/pdf");
        }
        Long employeeId = BaseContext.getCurrentId();
        if (employeeId == null) {
            throw new CustomException("未登录或会话过期");
        }

        ExportTask task = new ExportTask();
        task.setTaskType(taskType);
        task.setFileFormat(fileFormat);
        task.setStatus(ExportTask.STATUS_PENDING);

        // 发起员工姓名快照（employee 表在租户插件忽略列表，直接查）
        Employee emp = employeeService.getById(employeeId);
        task.setEmployeeId(employeeId);
        task.setEmployeeName(emp != null && emp.getName() != null ? emp.getName() : "未知员工");

        // 参数清洗：空值丢弃；序列化后限长（params_json text，防滥用封顶）
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getValue() != null && !e.getValue().trim().isEmpty()) {
                    cleaned.put(e.getKey(), e.getValue().trim());
                }
            }
        }
        String json = cleaned.isEmpty() ? null : objectMapper.writeValueAsString(cleaned);
        if (json != null && json.length() > MAX_PARAMS_JSON_LENGTH) {
            throw new CustomException("导出参数过长，请缩小筛选范围");
        }
        task.setParamsJson(json);
        exportTaskMapper.insert(task);
        return task;
    }

    @Override
    public void execute(Long taskId) {
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null || task.getStatus() == null || task.getStatus() != ExportTask.STATUS_PENDING) {
            return; // 已被并发执行/终态/跨租户不可见：幂等返回
        }
        // 抢占式置 RUNNING（条件更新防并发重复执行）
        LambdaUpdateWrapper<ExportTask> claim = new LambdaUpdateWrapper<>();
        claim.eq(ExportTask::getId, taskId)
                .eq(ExportTask::getStatus, ExportTask.STATUS_PENDING)
                .set(ExportTask::getStatus, ExportTask.STATUS_RUNNING);
        if (exportTaskMapper.update(null, claim) != 1) {
            return;
        }
        task.setStatus(ExportTask.STATUS_RUNNING);

        try {
            Map<String, String> params = parseParams(task.getParamsJson());
            byte[] bytes = dispatchGenerate(task.getTaskType(), task.getFileFormat(), params);
            if (bytes == null || bytes.length == 0) {
                throw new CustomException("导出内容为空");
            }
            Path file = writeTaskFile(task, bytes);

            ExportTask done = new ExportTask();
            done.setId(taskId);
            done.setStatus(ExportTask.STATUS_SUCCESS);
            done.setFileName(buildFileName(task, LocalDateTime.now()));
            done.setFileSize((long) bytes.length);
            done.setFinishTime(LocalDateTime.now());
            exportTaskMapper.updateById(done);
            log.info("[导出任务] 完成: id={}, type={}, file={}, size={}B",
                    taskId, task.getTaskType(), file, bytes.length);
            notifyTerminal(task, ExportTask.STATUS_SUCCESS, done.getFileName(), null);
        } catch (Exception e) {
            // 宽异常兜底：任务失败必须落状态而不是向上抛（异步线程无接收方）
            String msg = e instanceof CustomException && e.getMessage() != null
                    ? e.getMessage() : "导出失败，请稍后重试";
            log.error("[导出任务] 失败: id={}, type={}", taskId, task.getTaskType(), e);
            ExportTask failed = new ExportTask();
            failed.setId(taskId);
            failed.setStatus(ExportTask.STATUS_FAILED);
            failed.setErrorMsg(msg.length() > 500 ? msg.substring(0, 500) : msg);
            failed.setFinishTime(LocalDateTime.now());
            exportTaskMapper.updateById(failed);
            notifyTerminal(task, ExportTask.STATUS_FAILED, null, msg);
        }
    }

    /**
     * 任务终态实时推送（O-25）：向发起员工的壳层 SSE 通道发 "export-task" 事件，
     * 前端弹提示，无需打开导出中心轮询。推送失败只记日志，绝不影响任务状态本身。
     */
    private void notifyTerminal(ExportTask task, int status, String fileName, String errorMsg) {
        if (task.getEmployeeId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.getId());
            payload.put("taskType", task.getTaskType());
            payload.put("fileFormat", task.getFileFormat());
            payload.put("status", status);
            payload.put("fileName", fileName);
            payload.put("errorMsg", errorMsg);
            employeeSseManager.sendToUser(task.getEmployeeId(), "export-task", payload);
        } catch (Exception e) {
            // 宽异常兜底：推送通道任何问题都不反噬任务结果（离线员工下次打开导出中心仍可见终态）
            log.warn("[导出任务] 终态推送失败: id={}, employeeId={}", task.getId(), task.getEmployeeId(), e);
        }
    }

    @Override
    public Page<ExportTask> page(int page, int pageSize, String taskType, Integer status) {
        LambdaQueryWrapper<ExportTask> wrapper = new LambdaQueryWrapper<>();
        if (taskType != null && !taskType.isBlank()) {
            wrapper.eq(ExportTask::getTaskType, taskType);
        }
        if (status != null) {
            wrapper.eq(ExportTask::getStatus, status);
        }
        wrapper.orderByDesc(ExportTask::getCreateTime);
        return exportTaskMapper.selectPage(PageUtils.of(page, pageSize), wrapper);
    }

    @Override
    public ExportTask getDownloadable(Long id) {
        ExportTask task = exportTaskMapper.selectById(id);
        if (task == null) {
            throw new CustomException("导出任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != ExportTask.STATUS_SUCCESS) {
            throw new CustomException("任务未完成，暂不可下载");
        }
        if (!Files.exists(taskFile(task))) {
            throw new CustomException("导出文件已过期或不存在，请重新导出");
        }
        return task;
    }

    @Override
    public void delete(Long id) {
        ExportTask task = exportTaskMapper.selectById(id);
        if (task == null) {
            throw new CustomException("导出任务不存在");
        }
        try {
            Files.deleteIfExists(taskFile(task));
        } catch (Exception e) {
            // 文件删除失败不阻断记录删除（磁盘残留由清扫任务兜底）
            log.warn("[导出任务] 删除文件失败: id={}", id, e);
        }
        exportTaskMapper.deleteById(id);
    }

    @Override
    public void markFailed(Long taskId, String errorMsg) {
        ExportTask failed = new ExportTask();
        failed.setId(taskId);
        failed.setStatus(ExportTask.STATUS_FAILED);
        failed.setErrorMsg(errorMsg);
        failed.setFinishTime(LocalDateTime.now());
        exportTaskMapper.updateById(failed);
    }

    @Override
    public Path taskFile(ExportTask task) {
        String month = task.getCreateTime() != null
                ? task.getCreateTime().format(MONTH_DIR_FMT) : LocalDateTime.now().format(MONTH_DIR_FMT);
        String ext = ExportTask.FORMAT_PDF.equals(task.getFileFormat()) ? "pdf" : "xlsx";
        return baseDir().resolve(month).resolve(task.getId() + "." + ext);
    }

    @Override
    public void sweepStaleTasks() {
        // 前提：调用方（清理任务）已设置租户上下文并持分布式锁——
        // 无上下文时租户插件会把 SQL 注成 tenant_id=-1，故不做跨租户假设
        LocalDateTime now = LocalDateTime.now();
        // ① 卡死任务：排队/生成中超时 → 标记失败（文件不可能存在）
        LambdaUpdateWrapper<ExportTask> stuck = new LambdaUpdateWrapper<>();
        stuck.in(ExportTask::getStatus, ExportTask.STATUS_PENDING, ExportTask.STATUS_RUNNING)
                .lt(ExportTask::getCreateTime, now.minusHours(STUCK_TIMEOUT_HOURS))
                .set(ExportTask::getStatus, ExportTask.STATUS_FAILED)
                .set(ExportTask::getErrorMsg, "任务超时或应用重启中断，请重新导出")
                .set(ExportTask::getFinishTime, now);
        int stuckCount = exportTaskMapper.update(null, stuck);

        // ② 过期任务：终态超保留期 → 删文件 + 逻辑删除
        int expiredCount = 0;
        LambdaQueryWrapper<ExportTask> expired = new LambdaQueryWrapper<>();
        expired.in(ExportTask::getStatus, ExportTask.STATUS_SUCCESS, ExportTask.STATUS_FAILED)
                .lt(ExportTask::getFinishTime, now.minusDays(RETENTION_DAYS));
        for (ExportTask task : exportTaskMapper.selectList(expired)) {
            try {
                Files.deleteIfExists(taskFile(task));
            } catch (Exception e) {
                log.warn("[导出任务] 清扫删文件失败: id={}", task.getId(), e);
            }
            exportTaskMapper.deleteById(task.getId());
            expiredCount++;
        }
        if (stuckCount > 0 || expiredCount > 0) {
            log.info("[导出任务] 租户 {} 清扫完成: 卡死标记失败={}, 过期清理={}",
                    BaseContext.getCurrentTenantId(), stuckCount, expiredCount);
        }
    }

    // ==================== 私有工具 ====================

    /** 类型分发到对应导出实现 */
    private byte[] dispatchGenerate(String taskType, String format, Map<String, String> params) {
        return switch (taskType) {
            case ExportTask.TYPE_ORDERS -> exportDataService.orders(format, params);
            case ExportTask.TYPE_DISHES -> exportDataService.dishes(format, params);
            case ExportTask.TYPE_EMPLOYEES -> exportDataService.employees(format, params);
            case ExportTask.TYPE_USERS -> exportDataService.users(format, params);
            case ExportTask.TYPE_REPORT -> exportDataService.report(format, params);
            default -> throw new CustomException("不支持的导出类型");
        };
    }

    private Map<String, String> parseParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
    }

    /** 存储根目录：配置优先，缺省 {user.dir}/data/exports */
    private Path baseDir() {
        if (configuredDir != null && !configuredDir.isBlank()) {
            return Paths.get(configuredDir);
        }
        return Paths.get(System.getProperty("user.dir"), "data", "exports");
    }

    private Path writeTaskFile(ExportTask task, byte[] bytes) throws Exception {
        Path file = taskFile(task);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return file;
    }

    /** 下载文件名：{类型名}_{起}_{止}_{时间戳}.{ext}，日期段仅在有参数时拼接 */
    private String buildFileName(ExportTask task, LocalDateTime now) {
        String label = TYPE_LABELS.getOrDefault(task.getTaskType(), "导出数据");
        String ext = ExportTask.FORMAT_PDF.equals(task.getFileFormat()) ? "pdf" : "xlsx";
        StringBuilder name = new StringBuilder(label);
        try {
            Map<String, String> params = parseParams(task.getParamsJson());
            String start = params.get("startDate");
            String end = params.get("endDate");
            if (start != null) {
                name.append('_').append(start.replace("-", ""));
            }
            if (end != null && !end.equals(start)) {
                name.append('_').append(end.replace("-", ""));
            }
        } catch (Exception e) {
            // 参数解析失败只影响文件名美化，不影响任务成功
            log.warn("[导出任务] 文件名参数解析失败: id={}", task.getId());
        }
        name.append('_').append(now.format(FILE_STAMP_FMT)).append('.').append(ext);
        return name.toString();
    }
}
