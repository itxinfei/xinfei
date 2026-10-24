package com.xinfei.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * <p>
 * 跨域配置类
 * </p>
 *
 * @author 心飞为你飞
 * @since 2026-07-09
 */
@Configuration
public class CorsConfig {

    /**
     * 配置跨域过滤器。
     * <p>必须以最高优先级显式注册：CORS 预检是不带凭据的 OPTIONS 请求，若排在
     * LoginCheckFilter（order=3）之后，会先被登录墙拦成 401 且响应不带任何
     * Access-Control-Allow-* 头，浏览器直接判定预检失败（H5 联调实测根因）。</p>
     *
     * @return 跨域过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        // 仅允许受信域名访问，防止任意源携带凭证进行 CSRF 攻击
        // 开发环境允许常用开发服务器端口
        config.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:8080",
            "http://localhost:3000",
            "http://localhost:5173",
            "http://127.0.0.1:8080",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization",
            "X-Requested-With",
            "Accept",
            "Origin",
            "X-CSRF-Token",
            // 小程序/H5 token 会话通道（LoginCheckFilter 凭此头恢复登录态），
            // 漏在白名单里时带该头的预检会被浏览器拦截
            "X-Auth-Token"
        ));
        // 允许前端读取响应头中的 CSRF Token 与会话标识
        config.setExposedHeaders(Arrays.asList("X-CSRF-Token", "X-Auth-Token"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setName("corsFilter");
        registration.addUrlPatterns("/*");
        // 先于 SecurityHeader(0)/TraceId(1)/Csrf(2)/LoginCheck(3)，保证预检不被业务过滤器拦截
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
