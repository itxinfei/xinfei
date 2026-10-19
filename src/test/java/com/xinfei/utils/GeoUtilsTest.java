package com.xinfei.utils;

import com.xinfei.module.delivery.service.impl.DeliveryTrackingServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GeoUtils 百度改造 P3 单测（2026-10-19）。
 *
 * <p>覆盖不触网的三条面：① 未配 AK 时地理编码/逆地理/路径规划一律返回 null 降级、
 * {@code isAvailable} 为 false；② 服务端坐标系归一到 GCJ-02 与 Haversine 距离口径正确；
 * ③ 真实路网 ETA 在 AK 缺失时回落到直线启发式且返回合理分钟数（{@code DeliveryTrackingServiceImpl}
 * 直接 new，路径方法只用到纯数学与降级后的 geoUtils，不触达任何 mapper，故无需 Spring 上下文）。</p>
 */
class GeoUtilsTest {

    private GeoUtils degradedGeoUtils() {
        GeoUtils geo = new GeoUtils();
        // 空 AK：keyAvailable=false，任何外部调用前即短路返回 null
        ReflectionTestUtils.setField(geo, "baiduServerAk", "");
        ReflectionTestUtils.setField(geo, "timeoutMs", 3000);
        geo.init();
        return geo;
    }

    @Test
    void blankAkDisablesAllRemoteCapabilities() {
        GeoUtils geo = degradedGeoUtils();
        assertFalse(geo.isAvailable(), "空 AK 应判定不可用");
        assertNull(geo.geocode("北京市海淀区"), "未配 AK 地理编码应降级为 null");
        assertNull(geo.reverseGeocode(new BigDecimal("116.39"), new BigDecimal("39.90")),
                "未配 AK 逆地理应降级为 null");
        assertNull(geo.routePlanning(new BigDecimal("116.39"), new BigDecimal("39.90"),
                        new BigDecimal("116.41"), new BigDecimal("39.92"), "driving"),
                "未配 AK 路径规划应降级为 null");
    }

    @Test
    void nullCoordinatesShortCircuitWithoutNetwork() {
        GeoUtils geo = degradedGeoUtils();
        assertNull(geo.geocode(null));
        assertNull(geo.geocode("   "));
        assertNull(geo.reverseGeocode(null, null));
        assertNull(geo.routePlanning(null, null, null, null, "walking"));
    }

    @Test
    void normalizeCoordSystemHandlesCaseBlankAndUnknown() {
        assertEquals("GCJ-02", GeoUtils.normalizeCoordSystem("  gcj-02 ", "WGS-84"));
        assertEquals("BD-09", GeoUtils.normalizeCoordSystem("bd-09", "WGS-84"));
        assertEquals("WGS-84", GeoUtils.normalizeCoordSystem(null, "WGS-84"), "null 应回退 fallback");
        assertEquals("WGS-84", GeoUtils.normalizeCoordSystem("火星坐标", "WGS-84"), "未知值应回退 fallback");
    }

    @Test
    void toGcj02IsIdentityForGcj02AndShiftsWgs84WithinChina() {
        // GCJ-02 原样返回
        double[] same = GeoUtils.toGcj02(116.397428, 39.90923, "GCJ-02");
        assertEquals(116.397428, same[0], 1e-9);
        assertEquals(39.90923, same[1], 1e-9);

        // WGS-84 在北京应产生正向偏移（GCJ-02 相对 WGS-84 偏移数百米量级），但不离谱
        double[] shifted = GeoUtils.toGcj02(116.397428, 39.90923, "WGS-84");
        double dLng = shifted[0] - 116.397428;
        double dLat = shifted[1] - 39.90923;
        assertTrue(dLng > 0.001 && dLng < 0.01, "北京 WGS-84→GCJ-02 经度偏移应在合理区间, 实际=" + dLng);
        assertTrue(dLat > 0.0 && dLat < 0.01, "北京 WGS-84→GCJ-02 纬度偏移应在合理区间, 实际=" + dLat);
    }

    @Test
    void outOfChinaCoordinatesAreNotShifted() {
        // 柏林（境外）不做火星偏移
        double[] berlin = GeoUtils.toGcj02(13.404954, 52.520008, "WGS-84");
        assertEquals(13.404954, berlin[0], 1e-9);
        assertEquals(52.520008, berlin[1], 1e-9);
    }

    @Test
    void haversineMetersSanity() {
        assertEquals(0.0, GeoUtils.haversineMeters(116.40, 39.90, 116.40, 39.90), 1e-6,
                "同点距离应为 0");
        // 约 0.01 经度 ≈ 0.85km（北京纬度）
        double meters = GeoUtils.haversineMeters(116.40, 39.90, 116.41, 39.90);
        assertTrue(meters > 500 && meters < 1500, "0.01 经度距离应约 0.85km, 实际=" + meters);
    }

    @Test
    void routeEtaFallsBackToStraightLineWhenAkMissing() {
        GeoUtils geo = degradedGeoUtils();
        DeliveryTrackingServiceImpl service = new DeliveryTrackingServiceImpl();
        ReflectionTestUtils.setField(service, "geoUtils", geo);

        // AK 缺失 → routePlanning 返回 null → 回落直线启发式，仍返回正的 5 分钟档 ETA
        int minutes = service.estimateDeliveryTimeByRoute(
                new BigDecimal("116.390"), new BigDecimal("39.900"), "WGS-84",
                new BigDecimal("116.450"), new BigDecimal("39.940"), 1L);
        assertTrue(minutes > 0, "回落 ETA 应为正数");
        assertEquals(0, minutes % 5, "ETA 应四舍五入到 5 分钟档");

        // 坐标不全 → 退回基础时长口径（10+5=15）
        int baseOnly = service.estimateDeliveryTimeByRoute(
                null, null, "WGS-84", new BigDecimal("116.450"), new BigDecimal("39.940"), 1L);
        assertEquals(15, baseOnly);
    }

    @Test
    void routeResultConvertsSecondsToMinutes() {
        GeoUtils.RouteResult r = new GeoUtils.RouteResult(new BigDecimal("3000.0"), 660);
        assertNotNull(r.getDistanceMeters());
        assertEquals(660, r.getDurationSeconds());
        assertEquals(11, r.getDurationMinutes(), "660 秒应取整为 11 分钟");
    }
}
