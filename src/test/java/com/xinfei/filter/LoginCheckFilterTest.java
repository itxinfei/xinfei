package com.xinfei.filter;

import com.xinfei.common.BaseContext;
import com.xinfei.module.auth.service.AuthTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.FilterChain;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoginCheckFilter 单元测试（不启动 Spring 容器）。
 *
 * <p>MockMvc 全链路已在测试侧移除 LoginCheckFilter（见 MockMvcFilterExclusionAutoConfiguration），
 * 故过滤器自身"未登录→401 / 白名单放行 / 会话登录态校验"的行为在此独立覆盖。
 * 使用 spring-test 的 MockHttpServletRequest/Response + Mockito 模拟过滤器链。</p>
 *
 * @author xinfei
 * @since 2026-10-02
 */
class LoginCheckFilterTest {

    private final LoginCheckFilter filter = new LoginCheckFilter();

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod(method);
        req.setRequestURI(uri);
        return req;
    }

    @Test
    @DisplayName("受保护路径无会话：401 NOTLOGIN，且不放行")
    void protectedPath_withoutSession_returns401() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("GET", "/employee/page"), resp, chain);

        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("NOTLOGIN"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("登录白名单端点匿名访问：放行")
    void loginEndpoint_anonymous_passesThrough() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request("POST", "/user/login"), resp, chain);

        verify(chain).doFilter(any(), any());
        assertEquals(200, resp.getStatus());
    }

    @Test
    @DisplayName("员工会话（含租户）：放行并写入员工身份 request 属性")
    void employeeSession_passesThrough() throws Exception {
        MockHttpServletRequest req = request("GET", "/employee/me");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("employee", 990001L);
        session.setAttribute("tenantId", 999L);
        req.setSession(session);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertEquals(990001L, req.getAttribute("employeeId"));
    }

    @Test
    @DisplayName("用户会话（含租户）：放行")
    void userSession_passesThrough() throws Exception {
        MockHttpServletRequest req = request("GET", "/order/userPage");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", 994001L);
        session.setAttribute("tenantId", 999L);
        req.setSession(session);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("骑手会话（含租户）：放行并写入骑手身份 request 属性")
    void riderSession_passesThrough() throws Exception {
        MockHttpServletRequest req = request("GET", "/api/rider/orders");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("rider", 8001L);
        session.setAttribute("tenantId", 999L);
        req.setSession(session);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertEquals(8001L, req.getAttribute("riderId"));
    }

    @Test
    @DisplayName("用户会话缺租户ID：登录态不完整，401 不放行")
    void userSessionWithoutTenantId_returns401() throws Exception {
        MockHttpServletRequest req = request("GET", "/order/userPage");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", 994001L);
        req.setSession(session);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    // ---------- X-Auth-Token 分支（miniapp 前置改造 5.1，2026-10-22） ----------

    /** 构造携带 mock 根上下文（仅提供 AuthTokenService）与 X-Auth-Token 头的请求 */
    private MockHttpServletRequest requestWithAuthToken(String method, String uri, String headerValue,
                                                        AuthTokenService service) {
        WebApplicationContext wac = mock(WebApplicationContext.class);
        when(wac.getBean(AuthTokenService.class)).thenReturn(service);
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, wac);
        MockHttpServletRequest req = new MockHttpServletRequest(servletContext, method, uri);
        req.addHeader("X-Auth-Token", headerValue);
        return req;
    }

    @Test
    @DisplayName("X-Auth-Token 有效（员工）：放行并写入员工身份 request 属性")
    void tokenHeader_validEmployee_passesThrough() throws Exception {
        AuthTokenService service = mock(AuthTokenService.class);
        when(service.validate("tok-emp"))
                .thenReturn(new AuthTokenService.AuthTokenPayload("employee", 990101L, 999L, "admin"));
        MockHttpServletRequest req = requestWithAuthToken("GET", "/employee/me", "tok-emp", service);
        // filter 的 finally 会清 ThreadLocal，改在链内捕获上下文断言
        final Long[] ctx = new Long[2];
        FilterChain chain = (r, res) -> {
            ctx[0] = BaseContext.getCurrentId();
            ctx[1] = BaseContext.getCurrentTenantId();
        };

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertEquals(990101L, req.getAttribute("employeeId"));
        assertEquals("admin", req.getAttribute("roleKey"));
        assertEquals(990101L, ctx[0]);
        assertEquals(999L, ctx[1]);
    }

    @Test
    @DisplayName("X-Auth-Token 有效（顾客）：放行并写入 userId request 属性")
    void tokenHeader_validUser_setsUserIdAttribute() throws Exception {
        AuthTokenService service = mock(AuthTokenService.class);
        when(service.validate("tok-user"))
                .thenReturn(new AuthTokenService.AuthTokenPayload("user", 994001L, 999L, null));
        MockHttpServletRequest req = requestWithAuthToken("GET", "/user/info", "tok-user", service);
        final Long[] ctx = new Long[1];
        FilterChain chain = (r, res) -> ctx[0] = BaseContext.getCurrentId();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertEquals(994001L, req.getAttribute("userId"));
        assertEquals(994001L, ctx[0]);
    }

    @Test
    @DisplayName("X-Auth-Token 有效（骑手）：放行并写入骑手身份 request 属性")
    void tokenHeader_validRider_setsRiderIdAttribute() throws Exception {
        AuthTokenService service = mock(AuthTokenService.class);
        when(service.validate("tok-rider"))
                .thenReturn(new AuthTokenService.AuthTokenPayload("rider", 8001L, 999L, null));
        MockHttpServletRequest req = requestWithAuthToken("GET", "/api/rider/me", "tok-rider", service);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        assertEquals(8001L, req.getAttribute("riderId"));
    }

    @Test
    @DisplayName("X-Auth-Token 无效：401 不放行")
    void tokenHeader_invalid_returns401() throws Exception {
        AuthTokenService service = mock(AuthTokenService.class);
        when(service.validate("bad")).thenReturn(null);
        MockHttpServletRequest req = requestWithAuthToken("GET", "/employee/me", "bad", service);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }
}
