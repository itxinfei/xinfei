package com.xinfei.module.delivery.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 配送平台 mock 模式生产启动检查（P0-3b，镜像 {@code PaymentKeySecurityCheck} 的 fail-closed 口径）。
 * <p>
 * 背景：{@code application.yml} 基础层 {@code xinfei.delivery.mock-mode=true} 会穿透所有 profile
 * （prod yml 曾未覆盖）；mock 模式下配送回调验签整体跳过，而回调端点匿名放行——
 * 生产误开等于任何人可伪造平台回调改订单/配送状态。
 * 现 prod/production profile 下 mock-mode=true 且未显式豁免（环境变量 ALLOW_MOCK_IN_PROD=true，
 * 仅限隔离演示/单机自部署）时拒绝启动；其余环境 mock-mode=true 打显式告警。
 * </p>
 *
 * @author xinfei
 * @since 2026-10-16
 */
@Slf4j
@Component
public class DeliveryMockSecurityCheck {

    @Resource
    private DeliveryPlatformConfigProperties deliveryConfig;

    @Resource
    private Environment environment;

    /**
     * 启动校验：prod profile 与 delivery mock-mode 互斥（fail-fast），其余环境仅告警。
     */
    @PostConstruct
    public void validate() {
        if (!deliveryConfig.isMockMode()) {
            return;
        }
        boolean prodProfile = false;
        String[] activeProfiles = environment != null ? environment.getActiveProfiles() : new String[0];
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                prodProfile = true;
                break;
            }
        }
        if (prodProfile) {
            String flag = System.getenv("ALLOW_MOCK_IN_PROD");
            if (!"true".equalsIgnoreCase(flag == null ? "" : flag.trim())) {
                throw new IllegalStateException("[配送] xinfei.delivery.mock-mode=true 在 prod 环境禁止启用："
                        + "mock 模式跳过平台回调验签且回调端点匿名放行，等于可伪造配送回调篡改订单状态；"
                        + "生产须配置 xinfei.delivery.mock-mode=false 并按平台填入凭证，"
                        + "如确为隔离演示/单机自部署须显式设置环境变量 ALLOW_MOCK_IN_PROD=true 表示知情豁免");
            }
            log.warn("[配送] 已显式设置 ALLOW_MOCK_IN_PROD=true：prod 环境下仍启用 mock 配送并跳过回调验签，"
                    + "仅限隔离演示/单机自部署，严禁用于对公网提供服务的真实生产环境！");
            return;
        }
        log.warn("[配送] mock-mode=true：平台调用与回调验签已跳过，仅限开发/演示环境，严禁用于生产！");
    }
}
