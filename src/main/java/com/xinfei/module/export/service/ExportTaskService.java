package com.xinfei.module.export.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.module.export.model.ExportTask;

import java.util.Map;

/**
 * 异步导出任务服务（O-25 导出任务中心）
 * <p>提交即落库排队（PENDING），由 {@code ExportTaskRunner} 经 exportTaskExecutor
 * 异步执行 {@link #execute(Long)}；任务中心轮询 page、按任务下载、删除。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
public interface ExportTaskService {

    /**
     * 创建导出任务（同步落库，状态 PENDING；执行交给 Runner 异步触发）。
     *
     * @param taskType 任务类型（ExportTask.TYPE_*）
     * @param fileFormat 文件格式 excel/pdf
     * @param params 查询参数（值一律字符串，空值忽略）
     * @return 已落库任务（含自增ID）
     */
    ExportTask create(String taskType, String fileFormat, Map<String, String> params);

    /**
     * 执行任务：PENDING → RUNNING → SUCCESS/FAILED。幂等（非 PENDING 直接返回）。
     * 由 Runner 在异步线程调用；测试可同步直调。
     */
    void execute(Long taskId);

    /**
     * 分页查询当前租户任务（create_time 倒序）。
     *
     * @param page 页码（1 起）
     * @param pageSize 每页条数（上限 100）
     * @param taskType 类型过滤（可空）
     * @param status 状态过滤（可空）
     */
    Page<ExportTask> page(int page, int pageSize, String taskType, Integer status);

    /**
     * 取可下载任务（必须 SUCCESS 且文件存在），否则抛业务异常。
     */
    ExportTask getDownloadable(Long id);

    /**
     * 删除任务：删除磁盘文件 + 逻辑删除记录。
     */
    void delete(Long id);

    /**
     * 将任务标记为失败（队列拒绝等提交期失败；执行期失败由 execute 内部落状态）。
     */
    void markFailed(Long taskId, String errorMsg);

    /**
     * 任务文件磁盘路径（确定性推导，单一来源：下载与清扫共用）。
     */
    java.nio.file.Path taskFile(ExportTask task);

    /**
     * 清扫：启动/每日定时执行。
     * ① 卡死任务（PENDING/RUNNING 且 create_time 超过 1 小时）标记失败；
     * ② 过期任务（终态且 finish_time 超过 7 天）删文件 + 逻辑删除。
     */
    void sweepStaleTasks();
}
