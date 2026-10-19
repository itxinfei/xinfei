package com.xinfei.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 地图工具类（百度地图开放平台 Web 服务 API）。
 *
 * <p>提供地址→经纬度（地理编码）、经纬度→地址（逆地理编码）、两点路径规划（真实路网距离与时长）能力。
 * 用于地址簿自动回填经纬度、下单时配送范围匹配、配送追踪的真实 ETA。</p>
 *
 * <p><b>坐标系口径</b>：对外统一使用 GCJ-02——地理编码带 {@code ret_coordtype=gcj02ll}、逆地理与路径规划带
 * {@code coordtype=gcj02ll}，使百度（默认 BD-09）返回/接受 GCJ-02，与门店、地址簿既有坐标语义一致，
 * 无需在应用层转换，也不破坏「库内存原始坐标系」的约定。渲染到百度地图时的 BD-09 转换在前端
 * {@code front/js/coord.js} 完成。</p>
 *
 * <p><b>降级策略</b>：未配置服务端 AK（{@code xinfei.map.baidu.server-ak}）或调用失败时返回 {@code null}，
 * 不阻断主流程。AK 走 gitignored 的 profile 配置项，绝不写入被 Git 跟踪的文件。
 * 生产环境必须配置真实 AK（申请：https://lbsyun.baidu.com/apiconsole/center#/home）。</p>
 *
 * @author xinfei
 * @since 2026-08-31（2026-10-19 高德→百度改造 P3）
 */
@Slf4j
@Component
public class GeoUtils {

    @Value("${xinfei.map.baidu.server-ak:}")
    private String baiduServerAk;

    @Value("${xinfei.map.baidu.geocoding-url:https://api.map.baidu.com/geocoding/v3/}")
    private String geocodingUrl;

    @Value("${xinfei.map.baidu.direction-url-base:https://api.map.baidu.com/direction/v1/}")
    private String directionUrlBase;

    @Value("${xinfei.map.baidu.timeout-ms:3000}")
    private int timeoutMs;

    /** AK 是否可用（启动时检测一次，避免每次调用都判空）。 */
    private volatile boolean keyAvailable = false;

    /**
     * 初始化。
     */
    @PostConstruct
    public void init() {
        this.keyAvailable = StrUtil.isNotBlank(baiduServerAk);
        if (!keyAvailable) {
            log.warn("[Geo] 百度地图服务端 AK 未配置（xinfei.map.baidu.server-ak），地理编码/路径规划功能降级："
                    + "地址簿经纬度将为空、配送 ETA 回退直线估算。生产环境请务必配置。");
        }
    }

    /**
     * 判断 AK 是否已配置。
     */
    public boolean isAvailable() {
        return keyAvailable;
    }

    /** 合法坐标系标识集合（统一大写）。 */
    public static final Set<String> KNOWN_COORD_SYSTEMS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("WGS-84", "GCJ-02", "BD-09")));

    /**
     * 归一化坐标系标识：去空白转大写；为空或未知值时回退 {@code fallback}。
     * 用于服务端统一登记位置来源系，杜绝混系写入无从判别。
     *
     * @param raw      原始输入（可能任意大小写/空白/null）
     * @param fallback 回退值（应自身为合法值）
     * @return 合法坐标系标识
     */
    public static String normalizeCoordSystem(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return KNOWN_COORD_SYSTEMS.contains(s) ? s : fallback;
    }

    /**
     * Haversine 大圆距离（米）。全站唯一距离口径，供配送范围判定与距离/ETA 复用，
     * 避免各处重复实现导致地球半径/精度漂移。
     *
     * @param lng1 起点经度
     * @param lat1 起点纬度
     * @param lng2 终点经度
     * @param lat2 终点纬度
     * @return 两点间大圆距离（米）
     */
    public static double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static final double GEO_PI = 3.1415926535897932384626;
    private static final double GEO_A = 6378245.0;
    private static final double GEO_EE = 0.00669342162296594323;

    /**
     * WGS-84 → GCJ-02（国测局火星坐标）；境外坐标不偏移。服务端地理换算唯一口径，
     * 与前端 {@code front/js/coord.js} 的 wgs84ToGcj02 完全一致（eviltransform 通用公式）。
     * 用于把骑手上报的原始 WGS-84 位置对齐到地址簿/门店的 GCJ-02 语义后再调用百度路网 API。
     *
     * @param lng WGS-84 经度
     * @param lat WGS-84 纬度
     * @return {@code [经度, 纬度]}（GCJ-02）
     */
    public static double[] wgs84ToGcj02(double lng, double lat) {
        if (outOfChina(lng, lat)) {
            return new double[]{lng, lat};
        }
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * GEO_PI;
        double magic = Math.sin(radLat);
        magic = 1 - GEO_EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((GEO_A * (1 - GEO_EE)) / (magic * sqrtMagic) * GEO_PI);
        dLng = (dLng * 180.0) / (GEO_A / sqrtMagic * Math.cos(radLat) * GEO_PI);
        return new double[]{lng + dLng, lat + dLat};
    }

    /**
     * 按源坐标系归一到 GCJ-02。GCJ-02 原样返回；WGS-84/未知按 WGS-84 处理转换；BD-09 不在此支持
     * （后端不接收 BD-09，前端渲染才转 BD-09）。
     *
     * @param lng        经度
     * @param lat        纬度
     * @param fromSystem 源坐标系（{@link #normalizeCoordSystem} 口径）；空/未知按 WGS-84
     * @return {@code [经度, 纬度]}（GCJ-02）
     */
    public static double[] toGcj02(double lng, double lat, String fromSystem) {
        String s = normalizeCoordSystem(fromSystem, "WGS-84");
        if ("GCJ-02".equals(s)) {
            return new double[]{lng, lat};
        }
        // WGS-84（含 BD-09 兜底：后端理论上不应出现 BD-09，按 WGS-84 处理不阻断）
        return wgs84ToGcj02(lng, lat);
    }

    private static boolean outOfChina(double lng, double lat) {
        return (lng < 72.004 || lng > 137.8347) || (lat < 0.8293 || lat > 55.8271);
    }

    private static double transformLat(double lng, double lat) {
        double ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat
                + 0.1 * lng * lat + 0.2 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * GEO_PI) + 20.0 * Math.sin(2.0 * lng * GEO_PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lat * GEO_PI) + 40.0 * Math.sin(lat / 3.0 * GEO_PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(lat / 12.0 * GEO_PI) + 320 * Math.sin(lat * GEO_PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double lng, double lat) {
        double ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng
                + 0.1 * lng * lat + 0.1 * Math.sqrt(Math.abs(lng));
        ret += (20.0 * Math.sin(6.0 * lng * GEO_PI) + 20.0 * Math.sin(2.0 * lng * GEO_PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(lng * GEO_PI) + 40.0 * Math.sin(lng / 3.0 * GEO_PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(lng / 12.0 * GEO_PI) + 300 * Math.sin(lng / 30.0 * GEO_PI)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * 地址→经纬度（地理编码，GCJ-02）。
     *
     * <p>百度默认返回 BD-09，这里通过 {@code ret_coordtype=gcj02ll} 直接取得 GCJ-02，
     * 与门店 {@code StoreInfo.longitude/latitude}、地址簿既有坐标语义一致，
     * 可直接用于 {@code DeliveryEnhancedService.isInRange} 范围校验。</p>
     *
     * @param fullAddress 完整地址（省+市+区+详细地址）
     * @return {@code [longitude, latitude]}（GCJ-02），调用失败或未配置 AK 时返回 null
     */
    public BigDecimal[] geocode(String fullAddress) {
        if (!keyAvailable || StrUtil.isBlank(fullAddress)) {
            return null;
        }
        try {
            String url = geocodingUrl + "?address=" + URLUtil.encode(fullAddress)
                    + "&output=json&ret_coordtype=gcj02ll&ak=" + baiduServerAk;
            String response = HttpUtil.get(url, timeoutMs);
            JSONObject json = JSONUtil.parseObj(response);
            // 百度以 status=0 表示成功（与高德 status="1" 不同）
            if (json.getInt("status", -1) != 0) {
                log.warn("[Geo] 地理编码失败，address={}, status={}, message={}",
                        fullAddress, json.getInt("status", -1), json.getStr("message"));
                return null;
            }
            JSONObject result = json.getJSONObject("result");
            JSONObject location = result == null ? null : result.getJSONObject("location");
            if (location == null) {
                return null;
            }
            BigDecimal lng = location.getBigDecimal("lng");
            BigDecimal lat = location.getBigDecimal("lat");
            if (lng == null || lat == null) {
                return null;
            }
            return new BigDecimal[]{
                    lng.setScale(6, RoundingMode.HALF_UP),
                    lat.setScale(6, RoundingMode.HALF_UP)
            };
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.warn("[Geo] 地理编码异常，address={}, 原因={}", fullAddress, e.getMessage());
            return null;
        }
    }

    /**
     * 经纬度→地址（逆地理编码）。
     *
     * @param lng 经度（GCJ-02）
     * @param lat 纬度（GCJ-02）
     * @return 格式化地址字符串；调用失败、未配置 AK 或坐标为空时返回 null
     */
    public String reverseGeocode(BigDecimal lng, BigDecimal lat) {
        if (!keyAvailable || lng == null || lat == null) {
            return null;
        }
        try {
            // 百度 location 参数顺序为「纬度,经度」；coordtype 声明入参坐标系
            String url = geocodingUrl + "?location=" + lat.toPlainString() + "," + lng.toPlainString()
                    + "&output=json&coordtype=gcj02ll&ak=" + baiduServerAk;
            String response = HttpUtil.get(url, timeoutMs);
            JSONObject json = JSONUtil.parseObj(response);
            if (json.getInt("status", -1) != 0) {
                log.warn("[Geo] 逆地理编码失败，lng={}, lat={}, status={}, message={}",
                        lng, lat, json.getInt("status", -1), json.getStr("message"));
                return null;
            }
            JSONObject result = json.getJSONObject("result");
            if (result == null) {
                return null;
            }
            String address = result.getStr("formatted_address");
            return StrUtil.isBlank(address) ? result.getStr("address") : address;
        } catch (Exception e) {
            log.warn("[Geo] 逆地理编码异常，lng={}, lat={}, 原因={}", lng, lat, e.getMessage());
            return null;
        }
    }

    /** 百度方向 API 支持的出行方式（白名单，避免任意拼 URL）。 */
    private static final Set<String> ROUTE_MODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("walking", "bicycling", "driving", "transit")));

    /**
     * 两点路径规划（真实路网距离与时长）。
     *
     * <p>起终点坐标按 GCJ-02 传入（百度 {@code coordtype=gcj02ll}）。用于配送真实 ETA：
     * 取第一条推荐线路的 {@code distance}（米）与 {@code duration}（秒）。</p>
     *
     * @param originLng 起点经度（GCJ-02）
     * @param originLat 起点纬度（GCJ-02）
     * @param destLng   终点经度（GCJ-02）
     * @param destLat   终点纬度（GCJ-02）
     * @param mode      出行方式（walking/bicycling/driving/transit）；空或非法回退 walking
     * @return 路径结果；未配置 AK、坐标为空或调用失败时返回 null
     */
    public RouteResult routePlanning(BigDecimal originLng, BigDecimal originLat,
            BigDecimal destLng, BigDecimal destLat, String mode) {
        if (!keyAvailable || originLng == null || originLat == null || destLng == null || destLat == null) {
            return null;
        }
        String normalizedMode = mode == null ? "walking" : mode.trim().toLowerCase(Locale.ROOT);
        if (!ROUTE_MODES.contains(normalizedMode)) {
            normalizedMode = "walking";
        }
        try {
            // 百度方向 API 坐标顺序为「纬度,经度」
            String origin = originLat.toPlainString() + "," + originLng.toPlainString();
            String destination = destLat.toPlainString() + "," + destLng.toPlainString();
            String url = directionUrlBase + normalizedMode + "/?origin=" + origin
                    + "&destination=" + destination
                    + "&coordtype=gcj02ll&output=json&ak=" + baiduServerAk;
            String response = HttpUtil.get(url, timeoutMs);
            JSONObject json = JSONUtil.parseObj(response);
            if (json.getInt("status", -1) != 0) {
                log.warn("[Geo] 路径规划失败，mode={}, status={}, message={}",
                        normalizedMode, json.getInt("status", -1), json.getStr("message"));
                return null;
            }
            JSONObject result = json.getJSONObject("result");
            JSONArray routes = result == null ? null : result.getJSONArray("routes");
            if (routes == null || routes.isEmpty()) {
                return null;
            }
            JSONObject first = routes.getJSONObject(0);
            BigDecimal distance = first.getBigDecimal("distance");
            Integer duration = first.getInt("duration");
            if (distance == null || duration == null) {
                return null;
            }
            return new RouteResult(distance.setScale(1, RoundingMode.HALF_UP), duration);
        } catch (Exception e) {
            log.warn("[Geo] 路径规划异常，mode={}, 原因={}", normalizedMode, e.getMessage());
            return null;
        }
    }

    /**
     * 路径规划结果（真实路网距离与时长）。
     */
    public static class RouteResult {
        /** 路线距离（米）。 */
        private final BigDecimal distanceMeters;
        /** 预计耗时（秒）。 */
        private final int durationSeconds;

        public RouteResult(BigDecimal distanceMeters, int durationSeconds) {
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
        }

        public BigDecimal getDistanceMeters() {
            return distanceMeters;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        /** 预计耗时（分钟，四舍五入取整）。 */
        public int getDurationMinutes() {
            return (int) Math.round(durationSeconds / 60.0);
        }
    }
}
