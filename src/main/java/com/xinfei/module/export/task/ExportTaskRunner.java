package com.xinfei.module.export.task;

import com.xinfei.module.export.service.ExportTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 异步导出任务执行器（O-25）
 * <p>独立 Bean 承载 @Async 代理（避免自调用代理失效）：提交线程的 BaseContext
 * （员工ID/租户ID）由 exportTaskExecutor 的 TaskDecorator 传递，执行完成后由其统一清理。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Component
public class ExportTaskRunner {

    @Resource
    private ExportTaskService exportTaskService;

    /**
     * 异步执行单个导出任务。队列拒绝由调用方捕获并落 FAILED 状态。
     */
    @Async("exportTaskExecutor")
    public void run(Long taskId) {
        try {
            exportTaskService.execute(taskId);
        } catch (Exception e) {
            // execute 内部已宽兜底，此处为最后防线（防止异常吞没线程池日志）
            log.error("[导出任务] 执行器异常: taskId={}", taskId, e);
        }
    }
}
