package com.xinfei.module.delivery.controller;

import com.xinfei.common.R;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 地图引擎配置下发契约测试（2026-10-19 百度地图改造 P2）。
 *
 * <p>验证 {@code /api/map/config} 的开关语义：仅当浏览器端 AK 配置存在且非空白时
 * {@code enabled=true} 并回传去空白后的 AK；未配置则 {@code engine=none / enabled=false / ak=""}，
 * 前端据此降级为文字版。纯逻辑，反射注入 {@code @Value} 字段，不依赖 Spring 上下文。</p>
 */
class MapConfigControllerTest {

    private Map<String, Object> invoke(String ak) {
        MapConfigController controller = new MapConfigController();
        ReflectionTestUtils.setField(controller, "baiduBrowserAk", ak);
        R<Map<String, Object>> r = controller.config();
        return r.getData();
    }

    @Test
    void blankOrNullAkDisablesMap() {
        for (String ak : new String[]{null, "", "   "}) {
            Map<String, Object> cfg = invoke(ak);
            assertFalse((Boolean) cfg.get("enabled"), "空/空白 AK 应禁用地图: [" + ak + "]");
            assertEquals("none", cfg.get("engine"));
            assertEquals("", cfg.get("ak"));
        }
    }

    @Test
    void configuredAkEnablesMapAndIsTrimmed() {
        Map<String, Object> cfg = invoke("  ABCdef123  ");
        assertTrue((Boolean) cfg.get("enabled"));
        assertEquals("baidu", cfg.get("engine"));
        // 回传的 AK 去除首尾空白，但保留原样大小写
        assertEquals("ABCdef123", cfg.get("ak"));
    }
}
