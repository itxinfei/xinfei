package com.xinfei.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * <p>
 * 租户表清单注册中心（任务 3：白名单外化）。
 * </p>
 *
 * <p>
 * 职责：启动期加载 {@code classpath:tenant-tables.yml} 中的 {@code tenant.ignore-tables}
 * 白名单，供 {@code TenantLineInnerInterceptor} 判断是否跳过租户条件追加。
 * </p>
 *
 * <p>
 * 回退策略（零破坏升级）：
 * tenant-tables.yml 缺失、加载失败或清单为空时，回退到内置默认清单
 * （与原 MybatisPlusConfig.IGNORE_TABLES 硬编码保持一致），保证既有部署不受影响。
 * </p>
 *
 * <p>
 * 外化收益：新增无 tenant_id 列的表时，只需修改 yml 并重启，无需改 Java 代码重新编译。
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-10-17
 */
@Slf4j
@Component
public class TenantTableRegistry {

    /**
     * 内置默认白名单（与原 MybatisPlusConfig.IGNORE_TABLES 一致）。
     * 仅在 tenant-tables.yml 加载失败或清单为空时作为回退使用。
     */
    private static final Set<String> FALLBACK_IGNORE_TABLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "tenant", "employee", "shopping_cart", "ai_provider_config", "ai_prompt_template",
                    "ai_prompt_template_history", "dish_evaluation", "permission", "role_permission",
                    "region", "store_sync_log", "rider_remember_token", "sms_send_log"
            )));

    /**
     * 配置文件路径
     */
    private static final String CONFIG_LOCATION = "tenant-tables.yml";

    /**
     * 生效的白名单（小写归一，匹配时大小写不敏感）
     */
    private Set<String> ignoreTables = Collections.emptySet();

    /**
     * 是否使用了回退清单（供启动日志区分配置来源）
     */
    private boolean fallbackUsed = false;

    /**
     * 启动期加载 tenant-tables.yml。
     * <p>
     * YamlPropertiesFactoryBean 将列表展开为 {@code tenant.ignore-tables[0]=tenant} 形式，
     * 此处遍历提取。加载失败不抛异常（回退兜底），保证应用可启动。
     * </p>
     */
    @PostConstruct
    public void load() {
        try {
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(new ClassPathResource(CONFIG_LOCATION));
            Properties props = factory.getObject();
            if (props == null || props.isEmpty()) {
                log.warn("tenant-tables.yml 为空，回退到内置默认白名单（{} 张表）", FALLBACK_IGNORE_TABLES.size());
                applyFallback();
                return;
            }
            Set<String> loaded = new HashSet<>();
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("tenant.ignore-tables[")) {
                    String value = props.getProperty(key);
                    if (value != null && !value.trim().isEmpty()) {
                        loaded.add(value.trim().toLowerCase());
                    }
                }
            }
            if (loaded.isEmpty()) {
                log.warn("tenant-tables.yml 中 tenant.ignore-tables 清单为空，回退到内置默认白名单（{} 张表）",
                        FALLBACK_IGNORE_TABLES.size());
                applyFallback();
                return;
            }
            this.ignoreTables = Collections.unmodifiableSet(loaded);
            this.fallbackUsed = false;
            log.info("租户白名单已从 {} 加载：{} 张表免租户隔离", CONFIG_LOCATION, loaded.size());
        } catch (Exception e) {
            log.warn("加载 {} 失败（{}），回退到内置默认白名单（{} 张表）",
                    CONFIG_LOCATION, e.getMessage(), FALLBACK_IGNORE_TABLES.size());
            applyFallback();
        }
    }

    /**
     * 应用回退清单。
     */
    private void applyFallback() {
        this.ignoreTables = FALLBACK_IGNORE_TABLES;
        this.fallbackUsed = true;
    }

    /**
     * 判断表是否在租户隔离白名单中（大小写不敏感）。
     *
     * @param tableName 表名
     * @return true 表示白名单表，TenantLineInnerInterceptor 不追加 tenant_id 条件
     */
    public boolean isIgnored(String tableName) {
        if (tableName == null) {
            return false;
        }
        return ignoreTables.contains(tableName.toLowerCase());
    }

    /**
     * 获取当前生效的白名单（只读视图）。
     *
     * @return 白名单表名集合
     */
    public Set<String> getIgnoreTables() {
        return ignoreTables;
    }

    /**
     * 是否使用了回退清单。
     *
     * @return true 表示 yml 未生效，正在使用内置默认清单
     */
    public boolean isFallbackUsed() {
        return fallbackUsed;
    }
}