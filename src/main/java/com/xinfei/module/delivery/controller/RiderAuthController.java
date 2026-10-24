package com.xinfei.module.delivery.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.common.LogMaskUtils;
import com.xinfei.common.PasswordUtils;
import com.xinfei.common.R;
import com.xinfei.common.RateLimit;
import com.xinfei.common.RateLimitType;
import com.xinfei.common.SecurityConstants;
import com.xinfei.common.annotation.RequireRider;
import com.xinfei.module.auth.service.AuthTokenService;
import com.xinfei.module.auth.service.LoginAttemptService;
import com.xinfei.module.user.service.SmsCodeService;
import com.xinfei.module.delivery.mapper.RiderMapper;
import com.xinfei.module.delivery.model.Rider;
import com.xinfei.module.delivery.model.RiderRememberToken;
import com.xinfei.module.delivery.service.DeliveryTrackingService;
import com.xinfei.module.delivery.service.RiderRememberTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 骑手端认证控制器。
 * <p>
 * 提供骑手「手机号 + 密码」登录、登出、当前信息与上下线切换。
 * 登录成功后会话写 rider / tenantId，由 {@code LoginCheckFilter} 识别为骑手身份，
 * 业务接口通过 {@link RequireRider} 限定仅骑手可访问。
 * </p>
 *
 * @author xinfei
 * @since 2026-09-23
 */
@Slf4j
@RestController
@RequestMapping("/api/rider")
@Tag(name = "骑手认证", description = "骑手登录/登出/会话/上下线")
public class RiderAuthController {

    @Autowired
    private RiderMapper riderMapper;

    /** 非 cookie 会话通道（miniapp 前置改造 5.1）：登录成功颁发 X-Auth-Token，登出吊销 */
    @Autowired
    private AuthTokenService authTokenService;

    /** 验证码存储（miniapp 前置改造 5.2）：Redis 优先、session 兜底，与 C 端发码同源跨端校验 */
    @Autowired
    private SmsCodeService smsCodeService;

    @Autowired
    private DeliveryTrackingService deliveryTrackingService;

    @Autowired
    private RiderRememberTokenService riderRememberTokenService;

    /** 登录失败次数记录与账号锁定（P0-5：防暴力破解，与员工端共用 LoginAttemptService） */
    @Autowired
    private LoginAttemptService loginAttemptService;

    /** 登录被锁定时的统一提示文案 */
    private static final String LOGIN_LOCKED_MESSAGE = "登录失败次数过多，账号已被临时锁定，请"
            + SecurityConstants.LOGIN_LOCK_DURATION + "分钟后重试";

    /** 短信验证码有效期 5 分钟，与 C 端发码逻辑一致 */
    private static final long SMS_CODE_EXPIRE_MS = 5L * 60 * 1000;

    /**
     * 骑手登录（手机号 + 密码）。
     * @param request HTTP 请求
     * @param response HTTP 响应（勾选「记住我」时下发持久登录 cookie）
     * @param body 含 phone、password、rememberMe
     * @return 骑手信息（密码不返回）
     */
    @PostMapping("/login")
    @RateLimit(maxRequestsPerSecond = 5, type = RateLimitType.IP)
    @Operation(summary = "骑手登录", description = "手机号 + 密码登录骑手端")
    public R<Rider> login(HttpServletRequest request, HttpServletResponse response,
                          @RequestBody Map<String, Object> body) {
        String phone = body == null ? null : (String) body.get("phone");
        String password = body == null ? null : (String) body.get("password");
        boolean rememberMe = body != null && Boolean.TRUE.equals(body.get("rememberMe"));
        if (phone == null || phone.trim().isEmpty() || password == null || password.isEmpty()) {
            return R.error("手机号和密码不能为空");
        }
        String normalizedPhone = phone.trim();

        // P0-5：账号锁定检查前置于查库，命中锁定直接拒绝，不暴露手机号是否注册过
        if (loginAttemptService.isLocked(LoginAttemptService.Scope.RIDER, normalizedPhone)) {
            log.warn("骑手账号锁定中仍尝试登录：phone={}", LogMaskUtils.maskPhone(normalizedPhone));
            return R.error(LOGIN_LOCKED_MESSAGE);
        }

        // 登录前无租户上下文，selectByPhone 内部关闭了租户过滤
        Rider rider = riderMapper.selectByPhone(normalizedPhone);
        // 账号不存在或未设置密码（历史档案骑手），统一返回相同提示，避免账号枚举
        if (rider == null || rider.getPassword() == null || rider.getPassword().isEmpty()) {
            loginAttemptService.recordFailure(LoginAttemptService.Scope.RIDER, normalizedPhone);
            return R.error("手机号或密码错误");
        }
        if (!PasswordUtils.matches(password, rider.getPassword())) {
            loginAttemptService.recordFailure(LoginAttemptService.Scope.RIDER, normalizedPhone);
            return R.error("手机号或密码错误");
        }

        // 登录成功，清除失败计数
        loginAttemptService.clearOnSuccess(LoginAttemptService.Scope.RIDER, normalizedPhone);

        // 建立骑手会话
        request.getSession().setAttribute("rider", rider.getId());
        request.getSession().setAttribute("tenantId", rider.getTenantId());
        // 防止 Session Fixation：登录后切换 Session ID（须在写入属性后调用）
        request.changeSessionId();
        // 修改点(2026-10-22 5.1 Token 会话通道)：颁发 X-Auth-Token 供无 cookie 客户端（小程序）回带；
        // 颁发失败（Redis 降级）不下发头，登录主流程不受影响
        String authToken = authTokenService.issue(AuthTokenService.TYPE_RIDER, rider.getId(), rider.getTenantId(), null);
        if (authToken != null) {
            response.setHeader(AuthTokenService.HEADER_NAME, authToken);
        }
        log.info("骑手登录成功：riderId={}, tenantId={}, rememberMe={}",
                rider.getId(), rider.getTenantId(), rememberMe);

        // 记住我：颁发持久令牌 cookie，session 过期后由 LoginCheckFilter 凭 cookie 自动登录
        if (rememberMe) {
            RiderRememberToken rememberToken =
                    riderRememberTokenService.issue(rider.getId(), rider.getTenantId());
            Cookie cookie = new Cookie(RiderRememberTokenService.COOKIE_NAME, rememberToken.getToken());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(RiderRememberTokenService.TTL_DAYS * 24 * 60 * 60);
            response.addCookie(cookie);
        }

        Rider fresh = deliveryTrackingService.getRiderById(rider.getId());
        return R.success(fresh != null ? fresh : rider);
    }

    /**
     * 骑手登出。
     * @param request HTTP 请求
     * @param response HTTP 响应（清除记住登录 cookie）
     * @return 结果
     */
    @PostMapping("/logout")
    @Operation(summary = "骑手登出", description = "退出骑手端并销毁会话")
    public R<String> logout(HttpServletRequest request, HttpServletResponse response) {
        // 修改点(2026-10-22 5.1)：吊销非 cookie 会话通道的 X-Auth-Token（小程序登出无 session 可销毁）
        authTokenService.revoke(request.getHeader(AuthTokenService.HEADER_NAME));
        Long riderId = (Long) request.getSession().getAttribute("rider");
        if (riderId != null) {
            riderRememberTokenService.revokeByRider(riderId);
        }
        request.getSession().invalidate();
        BaseContext.remove();
        // 删除记住登录 cookie
        Cookie cookie = new Cookie(RiderRememberTokenService.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return R.success("退出成功");
    }

    /**
     * 骑手自助重置密码（手机号 + 短信验证码 + 新密码）。
     * <p>验证码由 C 端统一发码接口 {@code POST /user/sendMsg} 下发并存入 HttpSession，
     * 5 分钟内有效、校验通过后立即作废。</p>
     *
     * @param request HTTP 请求
     * @param body 含 phone、code、newPassword
     * @return 结果
     */
    @PostMapping("/forgot-password")
    @RateLimit(maxRequestsPerSecond = 1, type = RateLimitType.IP)
    @Operation(summary = "骑手重置密码", description = "凭手机短信验证码自助设置新密码")
    public R<String> forgotPassword(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String phone = body == null ? null : (String) body.get("phone");
        String code = body == null ? null : (String) body.get("code");
        String newPassword = body == null ? null : (String) body.get("newPassword");
        if (phone == null || phone.trim().isEmpty() || code == null || code.trim().isEmpty()
                || newPassword == null || newPassword.isEmpty()) {
            return R.error("手机号、验证码和新密码不能为空");
        }
        if (newPassword.length() < 6 || newPassword.length() > 20) {
            return R.error("新密码长度须为6-20位");
        }

        // 修改点(2026-10-22 5.2)：校验改走 SmsCodeService——Redis 优先（sms:code:<phone>，由
        // /user/sendMsg 写入，跨会话/跨端可校验），未命中回落 session 原逻辑；提示文案保持不变
        R<String> codeError = smsCodeService.verifyAndConsume(
                phone.trim(), code, request.getSession(false), "验证码不存在或已失效，请重新获取");
        if (codeError != null) {
            return codeError;
        }

        // selectByPhone 内部关闭了租户过滤
        Rider rider = riderMapper.selectByPhone(phone.trim());
        if (rider == null) {
            return R.error("该手机号尚未注册为骑手");
        }
        // 验证码通过即证明手机号所有者身份：恢复该骑手的租户上下文，
        // 否则 resetRiderPassword 内部 selectById 受租户插件 fail-closed 过滤查不到
        BaseContext.setCurrentId(rider.getId());
        BaseContext.setCurrentTenantId(rider.getTenantId());
        boolean ok = deliveryTrackingService.resetRiderPassword(rider.getId(), newPassword);
        if (!ok) {
            return R.error("密码重置失败，请稍后重试");
        }
        // 旧记住登录令牌随密码重置一并作废
        riderRememberTokenService.revokeByRider(rider.getId());
        log.info("骑手自助重置密码成功：riderId={}", rider.getId());
        return R.success("密码重置成功，请使用新密码登录");
    }

    /**
     * 获取当前登录骑手信息。
     * @return 骑手信息
     */
    @GetMapping("/me")
    @RequireRider
    @Operation(summary = "当前骑手信息", description = "获取当前登录骑手资料")
    public R<Rider> me() {
        Rider rider = deliveryTrackingService.getRiderById(BaseContext.getCurrentId());
        if (rider == null) {
            return R.error("骑手信息不存在");
        }
        return R.success(rider);
    }

    /**
     * 骑手上班（上线，可接单/抢单）。
     * @return 结果
     */
    @PostMapping("/online")
    @RequireRider
    @Operation(summary = "骑手上线", description = "切换为在线，可被派单或抢单")
    public R<String> online() {
        boolean ok = deliveryTrackingService.updateRiderStatus(BaseContext.getCurrentId(), Rider.STATUS_ONLINE);
        return ok ? R.success("已上线") : R.error("状态更新失败");
    }

    /**
     * 骑手下班（下线，不再接单）。
     * @return 结果
     */
    @PostMapping("/offline")
    @RequireRider
    @Operation(summary = "骑手下线", description = "切换为离线，不再接收新订单")
    public R<String> offline() {
        boolean ok = deliveryTrackingService.updateRiderStatus(BaseContext.getCurrentId(), Rider.STATUS_OFFLINE);
        return ok ? R.success("已下线") : R.error("状态更新失败");
    }

    /**
     * 骑手上报实时位置（骑手端每 12s 定时上报，用于顾客配送追踪页地图与距离展示）。
     * <p>骑手ID 只从会话取，不信任请求体，防止冒充他人上报。</p>
     *
     * @param body 含 longitude、latitude，可选 speed、heading
     * @return 结果
     */
    @PostMapping("/location")
    @RequireRider
    @RateLimit(maxRequestsPerSecond = 5)
    @Operation(summary = "骑手上报位置", description = "骑手端定时上报GPS坐标，供顾客端配送追踪使用")
    public R<String> reportLocation(@RequestBody Map<String, Object> body) {
        BigDecimal longitude = toBigDecimal(body == null ? null : body.get("longitude"));
        BigDecimal latitude = toBigDecimal(body == null ? null : body.get("latitude"));
        if (longitude == null || latitude == null) {
            return R.error("经纬度不能为空");
        }
        if (longitude.abs().compareTo(new BigDecimal("180")) > 0
                || latitude.abs().compareTo(new BigDecimal("90")) > 0) {
            return R.error("经纬度格式不正确");
        }
        BigDecimal speed = toBigDecimal(body == null ? null : body.get("speed"));
        BigDecimal heading = toBigDecimal(body == null ? null : body.get("heading"));
        // 坐标系由前端声明（骑手端上报原始 WGS-84）；空/未知值由服务端归一化回退 WGS-84
        String coordSystem = body == null ? null : String.valueOf(body.getOrDefault("coordSystem", ""));
        boolean ok = deliveryTrackingService.updateRiderLocation(
                BaseContext.getCurrentId(), longitude, latitude, speed, heading, coordSystem);
        return ok ? R.success("位置已更新") : R.error("位置上报失败");
    }

    /**
     * 将请求体中的数字（JSON 反序列化为 Integer/Double 等）统一转为 BigDecimal；
     * null 或无法解析时返回 null。
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
