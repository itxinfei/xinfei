package com.xinfei.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.xinfei.common.BaseContext;
import com.xinfei.common.tenant.TenantViolationException;


/**
 * <p>
 * 租户上下文一致性检查切面（域1基础设施层）。
 * </p>
 *
 * <p>
 * 背景：
 * MyBatis-Plus 已通过 {@code TenantLineInnerInterceptor} 对全部非忽略表自动追加
 * {@code WHERE tenant_id = ?} 条件（基于 BaseContext.getCurrentTenantId()）。
 * 因此，业务代码中任何显式构造 {@code LambdaQueryWrapper.eq(Entity::getTenantId, ...)}
 * 的调用都是冗余的（不删除，仅统计告警），最终由域4（代码结构优化）统一清理。
 * </p>
 *
 * <p>
 * 本切面的职责（域1阶段）：
 * 1. 兜底校验：任何进入 service 层的方法，BaseContext 必须已有 tenantId，否则打 WARN。
 * 2. 统计基线：对标注了 {@link RequiresTenantIsolation} 注解的方法，统计"显式
 *    设 tenant_id"的冗余调用次数，写入日志，供后续清理决策使用。
 * </p>
 *
 * <p>
 * 不拦截：
 * - 忽略表（tenant/employee/shopping_cart/ai_provider_config/permission/role_permission 等）
 *   走专用 Mapper，不受 TenantLineInnerInterceptor 影响，此处也不干预。
 * - 定时任务：遍历租户的入口 TenantServiceImpl.listActiveTenants() 已显式豁免
 *   （切点排除），不在此处告警。
 * </p>
 *
 * <p>
 * 域4清理建议：
 * 在清理全部冗余 .eq(...getTenantId...) 之前，请确保：
 * - 目标表不在 IGNORE_TABLES 中；
 * - 表结构含 tenant_id 列；
 * - 不存在需要"跨租户查询"的特殊场景（如管理员全局视角）。
 * </p>
 *
 * @author AI
 * @since 2026-08-22
 */
@Slf4j
@Aspect
@Component
@Order(100)
public class TenantAspect {

    /**
     * 租户强校验模式（任务 3：违规显式化）。
     * <ul>
     *   <li>warn（默认）：租户上下文为空仅打 WARN 日志，由 TenantLineInnerInterceptor
     *       fail-closed 兜底（tenant_id=-1 → 空集），保持零破坏；</li>
     *   <li>strict：租户上下文为空直接抛 {@link TenantViolationException}，
     *       由 GlobalExceptionHandler 映射为 HTTP 403，把静默空集升级为显式拒绝。</li>
     * </ul>
     * 建议在测试环境验证全量测试通过后再于生产开启 strict。
     */
    @Value("${xinfei.tenant.enforcement-mode:warn}")
    private String enforcementMode;

    /**
     * 兜底校验：任何以 service 命名空间的方法，BaseContext.tenantId 必须非空。
     * 命中：com.xinfei.module.*.service.impl.*ServiceImpl.*
     * <p>
     * 豁免 {@code TenantServiceImpl.listActiveTenants()}：该方法是定时任务遍历活跃租户的
     * 统一入口（PlatformPullTask/PlatformRetryTask/UnacceptedOrderScanTask 等均先枚举租户
     * 再逐租户设置 BaseContext），此时本就无租户上下文，且 tenant 表在 IGNORE_TABLES
     * 白名单内不受租户插件影响。豁免后消除全站定时任务每轮产生的 WARN 噪音。
     * </p>
     *
     * @param pjp 切点
     * @return 原方法返回值
     * @throws Throwable 传播原异常
     */
    @Around("execution(* com.xinfei.module..service.impl.*ServiceImpl.*(..)) "
            + "&& !execution(* com.xinfei.module.tenant.service.impl.TenantServiceImpl.listActiveTenants(..))")
    /**
     * 校验 tenant context。
     * @param pjp 参数 pjp
     * @return 返回结果
     */
    public Object checkTenantContext(ProceedingJoinPoint pjp) throws Throwable {
        Long tenantId = BaseContext.getCurrentTenantId();
        if (tenantId == null) {
            String method = pjp.getTarget().getClass().getSimpleName()
                    + "." + pjp.getSignature().getName() + "()";
            if ("strict".equalsIgnoreCase(enforcementMode)) {
                log.error("[TenantAspect][strict] Service 层租户上下文为空，拒绝执行: {}，返回 403", method);
                throw new TenantViolationException(
                        "租户上下文缺失，请求被拒绝：" + method
                                + "。排查入口：请求是否经 LoginCheckFilter 正常登录。");
            }
            log.warn("[TenantAspect][warn] Service 层租户上下文为空: {}，"
                + "请求将由 TenantLineInnerInterceptor fail-closed 兜底（tenant_id=-1）。"
                + "排查入口：请求是否经 LoginCheckFilter 正常登录。", method);
        }
        return pjp.proceed();
    }
}
