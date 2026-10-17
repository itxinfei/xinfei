package com.xinfei.common.tenant;

/**
 * <p>
 * 租户隔离违规异常（任务 3：违规显式化）。
 * </p>
 *
 * <p>
 * 触发场景：租户强校验开启（{@code xinfei.tenant.enforcement-mode=strict}）时，
 * Service 层方法执行前 BaseContext.tenantId 为空，由 {@code TenantAspect} 抛出。
 * {@code GlobalExceptionHandler} 将其映射为 HTTP 403 Forbidden。
 * </p>
 *
 * <p>
 * 设计意图：
 * 原机制下租户上下文丢失由 TenantLineInnerInterceptor fail-closed 兜底
 * （追加 tenant_id=-1 → 静默空集），用户只看到"无数据"，无法区分"真的没数据"
 * 与"隔离故障"。本异常把静默失败升级为显式 403，便于前端提示与后端排障。
 * </p>
 *
 * <p>
 * 默认 enforcement-mode=warn（仅打 WARN 日志，保持零破坏）；
 * strict 模式为渐进启用能力，建议在测试环境验证后再上生产。
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-10-17
 */
public class TenantViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造租户隔离违规异常。
     *
     * @param message 违规详情（含触发方法与缺失上下文说明）
     */
    public TenantViolationException(String message) {
        super(message);
    }

    /**
     * 构造租户隔离违规异常（携带原因链）。
     *
     * @param message 违规详情
     * @param cause   原始异常
     */
    public TenantViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}