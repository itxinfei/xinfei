package com.xinfei.test.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MockMvc 测试：排除 CSRF / 登录校验两个 Servlet Filter 的自动装配。
 *
 * <p>背景：主代码 {@code FilterRegistrationConfig}（2026-10-02 P0 修复）把
 * CsrfFilter、LoginCheckFilter 等由原 {@code @WebFilter} 改为 {@code FilterRegistrationBean}
 * 显式注册，以保证真实运行时的过滤器顺序与 profile 语义。副作用是 {@code @AutoConfigureMockMvc}
 * 会收集这些 FilterRegistrationBean 并加入 MockMvc 过滤器链，导致存量 Controller 测试
 * （约定"登录态/租户由 BaseContext + TestExecutionListener 显式模拟、MockMvc 不过过滤器"）
 * 成片出现 403（CSRF）/401（未登录）。</p>
 *
 * <p>本配置仅存在于测试类路径（不打进主应用），通过在 BeanDefinition 阶段移除
 * {@code csrfFilterRegistration}、{@code loginCheckFilterRegistration} 两个注册器，
 * 让 MockMvc 回到"不挂载这两个过滤器"的历史行为；主代码的 P0 修复对真实运行完全保留。
 * SecurityHeaderFilter / TraceIdFilter 不拦截请求，仍保留在链中。</p>
 *
 * <p>过滤器自身"未登录→401 / CSRF 缺失→403"的拦截行为，由针对过滤器的独立单元测试覆盖，
 * 不依赖 MockMvc 全链路。</p>
 *
 * @author xinfei
 * @since 2026-10-02
 */
@Configuration(proxyBeanMethods = false)
public class MockMvcFilterExclusionAutoConfiguration {

    /** 主代码 FilterRegistrationConfig 中 CSRF 注册器的 Bean 名（@Bean 方法名）。 */
    private static final String CSRF_REGISTRATION_BEAN = "csrfFilterRegistration";

    /** 主代码 FilterRegistrationConfig 中登录校验注册器的 Bean 名（@Bean 方法名）。 */
    private static final String LOGIN_CHECK_REGISTRATION_BEAN = "loginCheckFilterRegistration";

    /** 设为 true 时保留 CSRF/登录校验过滤器链（供全栈鉴权测试使用） */
    private static final String KEEP_AUTH_FILTERS_PROPERTY = "xinfei.test.keep-auth-filters";

    /**
     * 修改点(2026-10-22 5.1 Token 会话通道)：默认仍移除两个过滤器（存量测试零回归）；
     * 测试上下文带属性 {@code xinfei.test.keep-auth-filters=true}（如 @TestPropertySource）时
     * 保留过滤器链，供需要走完整 Servlet 过滤器链的全栈测试（如 AuthTokenChannelTest）使用。
     * 移除动作延后到 BFPP 阶段以便读取 Environment（registry 阶段 Environment 尚未注入）。
     */
    @Bean
    public static BeanFactoryPostProcessor testServletFilterExclusionPostProcessor() {
        return beanFactory -> {
            // Spring 7 的 ConfigurableListableBeanFactory 不再暴露 getEnvironment()，改由容器取 Environment 单例
            String keep = null;
            try {
                keep = beanFactory.getBean(org.springframework.core.env.Environment.class)
                        .getProperty(KEEP_AUTH_FILTERS_PROPERTY);
            } catch (Exception e) {
                // Environment 单例尚未注册：按默认行为（移除过滤器）处理
            }
            if ("true".equalsIgnoreCase(keep)) {
                return;
            }
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            removeIfPresent(registry, CSRF_REGISTRATION_BEAN);
            removeIfPresent(registry, LOGIN_CHECK_REGISTRATION_BEAN);
        };
    }

    private static void removeIfPresent(BeanDefinitionRegistry registry, String beanName) {
        if (registry.containsBeanDefinition(beanName)) {
            registry.removeBeanDefinition(beanName);
        }
    }
}
