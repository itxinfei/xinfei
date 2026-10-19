package com.xinfei.module.delivery.controller;

import com.xinfei.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 前端地图引擎配置下发接口。
 *
 * <p>把「用哪家地图、浏览器端 AK 是多少」从写死在前端 HTML 里（旧 {@code DELIVERY_MAP_CONFIG}
 * 内联高德 Key）收敛到服务端单一来源：浏览器端 JS API 只需读取本接口即可决定加载哪套地图 SDK。
 * 未配置 Key 时 {@code enabled=false}，前端据此降级为「ETA + 距离」文字版，不加载任何地图 SDK。</p>
 *
 * <p>安全边界：本接口仅下发<b>浏览器端 AK</b>（百度要求配合 referer 域名白名单使用，本就设计为
 * 可暴露于前端）；<b>服务端 AK / SecretKey 绝不在此下发</b>，仅后端地理编码/路径规划自用（见 P3）。
 * AK 值走 {@code xinfei.map.baidu.browser-ak} 配置项，默认空，不入被 Git 跟踪的文件。</p>
 *
 * @author xinfei
 * @since 2026-10-19
 */
@Slf4j
@RestController
@RequestMapping("/api/map")
@Tag(name = "地图引擎配置")
public class MapConfigController {

    /** 百度浏览器端 JS API AK；空=未配置，前端禁用地图。 */
    @Value("${xinfei.map.baidu.browser-ak:}")
    private String baiduBrowserAk;

    /**
     * 下发前端地图引擎配置。
     *
     * @return engine（baidu/none）、enabled、ak（未配置为空串）
     */
    @GetMapping("/config")
    @Operation(summary = "获取前端地图引擎配置", description = "返回地图引擎类型与浏览器端 AK；未配置 Key 时 enabled=false，前端降级为文字版")
    public R<Map<String, Object>> config() {
        boolean enabled = baiduBrowserAk != null && !baiduBrowserAk.trim().isEmpty();
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("engine", enabled ? "baidu" : "none");
        cfg.put("enabled", enabled);
        cfg.put("ak", enabled ? baiduBrowserAk.trim() : "");
        return R.success(cfg);
    }
}
