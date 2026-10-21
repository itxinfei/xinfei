package com.xinfei;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * <p>
 * 心飞外卖项目启动类
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-08-25
 */
@Slf4j
@SpringBootApplication
@EnableTransactionManagement
// 缓存开关只在 RedisConfig（带 @ConditionalOnProperty(spring.data.redis.host)）上声明 @EnableCaching，
// 此处不再重复：Boot 4 已把缓存自动配置移出核心模块，全局重复声明会让 @WebMvcTest 切片
// 在没有 CacheManager 的情况下强制要求它，切片测试直接起不来。
@EnableScheduling
@EnableAsync
public class XinfeiApplication {
    /**
     * 处理 main。
     * @param args 参数 args
     */
    public static void main(String[] args) {
        SpringApplication.run(XinfeiApplication.class, args);
        log.info("心飞外卖--项目启动成功...");
        // 修改点：触发 DevTools 重启以生效 DeliveryOrder 字段映射修复
    }
}
