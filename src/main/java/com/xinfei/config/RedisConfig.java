package com.xinfei.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Array;

/**
 * <p>
 * Redis配置类，自定义RedisTemplate泛型类型并配置Spring Cache的RedisCacheManager。
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-07-09
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.data.redis", name = "host")
@EnableCaching
public class RedisConfig {

    /**
     * 默认缓存过期时间（分钟）
     */
    private static final long DEFAULT_CACHE_TTL_MINUTES = 30;

    /**
     * 允许反序列化的包路径白名单
     */
    private static final String[] ALLOWED_PACKAGES = {
            "com.xinfei.",
            "java.util.",
            "java.time.",
            "java.lang."
    };

    /**
     * 主 ObjectMapper（@Primary），沿用 Spring Boot 标准 Jackson 配置。
     * <p>
     * 必须显式声明：Actuator 的 JmxEndpointAutoConfiguration 通过
     * {@code ObjectProvider<ObjectMapper>.getIfAvailable()} 取 mapper，
     * 若容器中唯一的 ObjectMapper 是 {@link #redisObjectMapper()}（启用了 NON_FINAL
     * DefaultTyping），JMX 调用 /actuator/health 时 convertValue 到 Map&lt;String,Object&gt;
     * 会因 details 中的 String/Long 等 final 值缺少 '@class' 抛 InvalidTypeIdException，
     * IDEA Endpoints 面板即报“无法检索 health 端点数据”。
     * 设为 @Primary 后 JMX 取到标准 mapper，Redis 侧仍走专用 mapper 互不影响。
     *
     * <p>返回类型必须是 {@link JsonMapper} 而非 {@code ObjectMapper}：Boot 4 的
     * {@code JacksonHttpMessageConvertersConfiguration} 按 {@code JsonMapper} 类型注入并带
     * {@code @ConditionalOnMissingBean(JsonMapper)}，声明成 ObjectMapper 会让它误判「容器里没有」
     * 而再建一个 {@code jacksonJsonMapper}，两个 primary 直接导致上下文启动失败。</p>
     *
     * @return 标准 ObjectMapper
     */
    @Bean
    @Primary
    public JsonMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    /**
     * Redis 专用 ObjectMapper Bean
     * 避免重复创建，集中管理序列化策略
     * <p>
     * 安全注意：启用 DefaultTyping 用于 Redis 反序列化时保留类型信息，
     * 同时使用白名单限制允许的包路径，防止反序列化漏洞。
     *
     * @return 配置好的 ObjectMapper
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        // 使用白名单限制允许反序列化的包路径，防止反序列化漏洞
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType(Array.class)
                .allowIfSubType("com.xinfei.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.lang.")
                .build();

        // Jackson 3 的 mapper 不可变：原「new ObjectMapper() + setVisibility/disable/
        // activateDefaultTyping」的 mutate 写法改为 builder 链式配置。
        // WRITE_DATES_AS_TIMESTAMPS 已从 SerializationFeature 移到 DateTimeFeature。
        return JsonMapper.builder()
                .changeDefaultVisibility(visibilityChecker ->
                        visibilityChecker.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .build();
    }

    /**
     * 配置RedisTemplate
     * key使用String序列化，value使用Jackson JSON序列化
     *
     * @param connectionFactory Redis连接工厂
     * @param objectMapper      统一配置的ObjectMapper
     * @return 配置好的RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                        @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用 GenericJacksonJsonRedisSerializer + 自定义 ObjectMapper
        // ObjectMapper 已启用 DefaultTyping 确保反序列化时类型正确
        GenericJacksonJsonRedisSerializer jsonSerializer = new GenericJacksonJsonRedisSerializer(objectMapper);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // key 使用 String 序列化
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 使用 JSON 序列化
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置Spring Cache的RedisCacheManager
     * 支持不同缓存名称配置不同的TTL过期时间
     *
     * @param connectionFactory Redis连接工厂
     * @param objectMapper      统一配置的ObjectMapper
     * @return CacheManager实例
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                      @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        GenericJacksonJsonRedisSerializer jsonSerializer = new GenericJacksonJsonRedisSerializer(objectMapper);

        // 默认缓存配置：30分钟过期
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(DEFAULT_CACHE_TTL_MINUTES))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // 为不同缓存名称配置不同的TTL
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>(16);

        // 套餐缓存：15分钟过期（套餐数据变更不频繁）
        cacheConfigurations.put("setmeal", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // 系统配置缓存：1小时过期（配置变更极少）
        cacheConfigurations.put("systemConfig", defaultConfig.entryTtl(Duration.ofHours(1)));

        // 权限缓存：1小时过期（权限变更不频繁）
        cacheConfigurations.put("permissions", defaultConfig.entryTtl(Duration.ofHours(1)));

        // 其他缓存使用默认30分钟过期

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
