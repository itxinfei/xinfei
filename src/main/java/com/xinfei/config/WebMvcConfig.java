package com.xinfei.config;

import com.xinfei.common.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * <p>
 * Web MVC配置类，配置静态资源映射和消息转换器。
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-07-09
 * 修改点(2026-07-10)：从 extends WebMvcConfigurationSupport 改为 implements WebMvcConfigurer，
 * 避免禁用Spring Boot MVC自动配置（否则springdoc-openapi和WebJars自动配置会失效）。
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 上传根目录（与 CommonController 同源：xinfei.path 优先） */
    @Value("${xinfei.path:}")
    private String configPath;

    /**
     * 设置静态资源映射
     * 映射前端页面、后端管理页面资源
     * Swagger UI 和 WebJars 资源由 springdoc-openapi 和 Spring Boot 自动配置处理，无需手动添加
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始进行静态资源映射...");
        // 根路径引导页由 Boot 默认的 classpath:/static/ 映射（static/index.html）承载，
        // 不在此显式映射 classpath:/ —— Spring 7 会拒绝该位置（等于暴露整个 classpath）。
        registry.addResourceHandler("/backend/**").addResourceLocations("classpath:/backend/");
        registry.addResourceHandler("/front/**").addResourceLocations("classpath:/front/");
        // 骑手端 H5（独立目录）
        registry.addResourceHandler("/rider/**").addResourceLocations("classpath:/rider/");
        // 三端共享 JS（img-path.js 等，Task 1 注入依赖）
        registry.addResourceHandler("/shared/**").addResourceLocations("classpath:/shared/");
        // 运行时公开图：仅 public 段静态直出；private 永不映射（走 /common/download 鉴权）
        String uploadRoot = com.xinfei.utils.ImageStoragePathResolver.resolveRoot(configPath);
        registry.addResourceHandler("/uploads/public/**")
                .addResourceLocations("file:" + uploadRoot + "public/");
    }

    /**
     * 修改点：通过 WebMvcConfigurer 方式扩展消息转换器
     * 追加自定义Jackson转换器以支持Long类型序列化为字符串、Java 8时间类型格式化。
     * 注意：不再声明ObjectMapper Bean，避免与RedisConfig的redisObjectMapper冲突。
     *
     * @param converters 消息转换器列表
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("扩展消息转换器...");
        // Jackson 3 版转换器（Spring 7 提供 JacksonJsonHttpMessageConverter，
        // 原 MappingJackson2HttpMessageConverter 属 Jackson 2 血统，已随兼容层一并退场）
        JacksonJsonHttpMessageConverter messageConverter =
                new JacksonJsonHttpMessageConverter(JacksonObjectMapper.create());
        // 插入到 StringHttpMessageConverter（默认位于列表首位，负责纯文本/字符串响应，
        // 如 springdoc 的 /v3/api-docs 字符串响应）之后、默认 Jackson 转换器之前。
        // 这样业务对象由自定义 Jackson 处理（Long→String 防精度丢失），
        // 而字符串型响应由 StringHttpMessageConverter 原样输出，避免被二次 JSON 序列化（双重转义）。
        int stringConverterIndex = -1;
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof StringHttpMessageConverter) {
                stringConverterIndex = i;
                break;
            }
        }
        if (stringConverterIndex >= 0) {
            converters.add(stringConverterIndex + 1, messageConverter);
        } else {
            converters.add(0, messageConverter);
        }
    }
}
