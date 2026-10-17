package com.xinfei.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.xinfei.common.BaseContext;
import com.xinfei.common.tenant.TenantTableRegistry;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>
 * MyBatis-Plus配置类，配置多租户插件和分页插件。
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-07-09
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    /**
     * 租户白名单注册中心（任务 3：白名单外化）。
     * <p>
     * 原实现将 IGNORE_TABLES 硬编码于本类，新增免隔离表需改代码重新编译；
     * 现由 {@link TenantTableRegistry} 从 tenant-tables.yml 加载，配置即生效。
     * 各表白名单理由见 tenant-tables.yml 注释；yml 缺失时回退内置默认清单（零破坏）。
     * </p>
     */
    private final TenantTableRegistry tenantTableRegistry;

    /**
     * 构造注入租户白名单注册中心。
     *
     * @param tenantTableRegistry 租户表清单注册中心
     */
    public MybatisPlusConfig(TenantTableRegistry tenantTableRegistry) {
        this.tenantTableRegistry = tenantTableRegistry;
    }

    /**
     * 处理 mybatis plus interceptor。
     * @return 返回结果
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            /**
             * 获取 tenant id。
             * @return 返回结果
             */
            @Override
            public Expression getTenantId() {
                Long tenantId = BaseContext.getCurrentTenantId();
                if (tenantId == null) {
                    // fail-closed：租户上下文为空时返回无效ID，避免生成 tenant_id = null 的 SQL
                    // 正常业务接口均有登录态，租户上下文非空；告警逻辑见 ignoreTable 中的兜底 warn
                    return new LongValue(-1L);
                }
                return new LongValue(tenantId);
            }

            /**
             * 获取 tenant id column。
             * @return 返回结果
             */
            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            /**
             * 处理 ignore table。
             * @param tableName 参数 tableName
             * @return 返回结果
             */
            @Override
            public boolean ignoreTable(String tableName) {
                // 仅忽略无 tenant_id 列的表，不跳过所有过滤（白名单来源：tenant-tables.yml，任务 3 外化）
                // 修复说明：原实现在此处判断 tenantId == null 时返回 true（跳过过滤），
                // 与 getTenantId 的 fail-closed 逻辑自相矛盾，导致租户上下文为空时隔离完全失效。
                // 现修复为仅做白名单判断，租户为空时由 getTenantId 返回 -1L 兜底，实现 fail-closed。
                boolean ignored = tenantTableRegistry.isIgnored(tableName);
                if (!ignored && BaseContext.getCurrentTenantId() == null) {
                    // 兜底告警：表不在白名单但租户上下文为空，getTenantId 将返回 -1L 导致查询返回空集，
                    // 用于及时发现白名单漏网或匿名访问未走预期路径的表
                    log.warn("租户上下文为空且表[{}]不在白名单，SQL 将追加 tenant_id=-1（返回空集），请检查是否需加入白名单",
                            tableName);
                }
                return ignored;
            }
        }));
        mybatisPlusInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        mybatisPlusInterceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return mybatisPlusInterceptor;
    }
}
