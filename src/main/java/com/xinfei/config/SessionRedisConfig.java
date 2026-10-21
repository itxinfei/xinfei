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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * <p>
 * Spring Session Redis 序列化配置（R-01 会话 Redis 化）。
 * </p>
 * <p>
 * Spring Session 默认使用 JDK 序列化存储 HttpSession 属性，存在跨版本兼容性差、
 * Redis 中不可读等问题。本配置提供名为 {@code springSessionDefaultRedisSerializer} 的
 * Bean，Spring Session 自动装配后改用 JSON 序列化。
 * </p>
 * <p>
 * <b>关键设计——DefaultTyping.EVERYTHING：</b>会话属性中大量使用 {@code Long}（employee /
 * tenantId / rider / user / smsCode 时间戳），而 {@code Long} 是 final 类。若使用常规的
 * {@code NON_FINAL} typing，Long 值不携带 {@code @class} 类型信息，反序列化时 Jackson 会将
 * 小数值推断为 {@code Integer}，导致 {@code (Long) session.getAttribute("employee")} 抛出
 * {@code ClassCastException}。改用 {@code EVERYTHING} typing 后所有值均携带类型标识，
 * Long 反序列化保持为 Long，彻底消除类型漂移。
 * </p>
 * <p>
 * 安全：复用 {@link RedisConfig} 同款白名单（com.xinfei. / java.util. / java.time. /
 * java.lang.），防止反序列化漏洞。
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-10-17
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.data.redis", name = "host")
public class SessionRedisConfig {

    /**
     * Spring Session 默认序列化器 Bean。
     * <p>Bean 名称固定为 {@code springSessionDefaultRedisSerializer}，
     * Spring Session 的 {@code RedisHttpSessionConfiguration} 通过 {@code @Autowired
     * @Qualifier("springSessionDefaultRedisSerializer")} 自动注入。</p>
     *
     * @return JSON 序列化器（EVERYTHING typing，保留 Long/String 类型信息）
     */
    @Bean(name = "springSessionDefaultRedisSerializer")
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // 白名单限制允许反序列化的包路径，防止反序列化漏洞（口径同 RedisConfig.redisObjectMapper）
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType("com.xinfei.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.lang.")
                .build();

        // Jackson 3 mapper 不可变，改 builder；WRITE_DATES_AS_TIMESTAMPS 已移到 DateTimeFeature。
        //
        // Jackson 3 的 DefaultTyping 已移除 EVERYTHING（只剩 JAVA_LANG_OBJECT /
        // OBJECT_AND_NON_CONCRETE / NON_CONCRETE_AND_ARRAYS / NON_FINAL / NON_FINAL_AND_ENUMS /
        // NON_FINAL_AND_RECORDS）。实测 NON_FINAL 对 Map<String,Object> 里声明为 Object 的属性
        // 仍会写出 ["java.lang.Long",1234567890123]，往返后读回是 Long 而非 Integer，
        // 即原注释担心的「Long 被推断成 Integer 导致 ClassCastException」在 NON_FINAL 下已不出现。
        ObjectMapper sessionOm = JsonMapper.builder()
                .changeDefaultVisibility(visibilityChecker ->
                        visibilityChecker.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .build();

        log.info("Spring Session Redis 序列化器已配置（JSON + NON_FINAL typing）");
        return new GenericJacksonJsonRedisSerializer(sessionOm);
    }
}