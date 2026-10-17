package com.xinfei.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
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
@ConditionalOnProperty(prefix = "spring.redis", name = "host")
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
        ObjectMapper sessionOm = new ObjectMapper();
        sessionOm.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        sessionOm.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        sessionOm.registerModule(new JavaTimeModule());

        // 白名单限制允许反序列化的包路径，防止反序列化漏洞（口径同 RedisConfig.redisObjectMapper）
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType("com.xinfei.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.lang.")
                .build();

        // EVERYTHING：final 类型（Long/Integer/String）也携带 @class，
        // 防止 Long 被 Jackson 默认推断为 Integer 导致 ClassCastException
        sessionOm.activateDefaultTyping(ptv,
                ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);

        log.info("Spring Session Redis 序列化器已配置（JSON + EVERYTHING typing）");
        return new GenericJackson2JsonRedisSerializer(sessionOm);
    }
}