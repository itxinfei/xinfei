package com.xinfei.common;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 全站统一的 Jackson 约定：Long/BigInteger 序列化为字符串（防前端 JS 精度丢失）、
 * java.time 统一格式、反序列化遇未知属性不报错。
 *
 * <p>Jackson 3 的 mapper 不可变，原先「{@code extends ObjectMapper} + 构造器内 mutate」的写法
 * 已不成立，改为 builder 工厂 {@link #create()}。</p>
 *
 * @author xinfei
 * @since 2026-07-09
 */
public final class JacksonObjectMapper {

    /**
     * 默认日期格式
     */
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

    /**
     * 默认日期时间格式
     */
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 默认时间格式
     */
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    /**
     * 日期时间格式化器（线程安全，可复用于序列化/反序列化）
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);

    /**
     * 时间格式化器（线程安全）
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT);

    private JacksonObjectMapper() {
    }

    /**
     * 构建带全站约定的 ObjectMapper。
     *
     * <p>返回类型用 {@link JsonMapper} 而非 {@code ObjectMapper}：Jackson 3 的
     * {@code JacksonJsonHttpMessageConverter} 构造器要求 JsonMapper。</p>
     *
     * @return 配置好的不可变 mapper
     */
    public static JsonMapper create() {
        SimpleModule simpleModule = new SimpleModule()
                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER))
                .addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter
                        .ofPattern(DEFAULT_DATE_FORMAT)))
                .addDeserializer(LocalTime.class, new LocalTimeDeserializer(TIME_FORMATTER))

                .addSerializer(BigInteger.class, ToStringSerializer.instance)
                .addSerializer(Long.class, ToStringSerializer.instance)
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter
                        .ofPattern(DEFAULT_DATE_FORMAT)))
                .addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMATTER));

        return JsonMapper.builder()
                // 反序列化时，未知属性不报异常
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(simpleModule)
                .build();
    }
}
