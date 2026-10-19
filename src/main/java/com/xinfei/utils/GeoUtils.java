package com.xinfei.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpUtil;
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
 * 地图工具类（高德 Web 服务 API）
 *
 * <p>提供地址→经纬度（地理编码）、经纬度→地址（逆地理编码）能力。
 * 用于地址簿自动回填经纬度、下单时配送范围匹配。</p>
 *
 * <p><b>降级策略</b>：未配置 amap.key 或调用失败时返回 null，不阻断主流程。
 * 生产环境必须配置真实 Key（申请：https://lbs.amap.com/api/webservice/guide/create-project/get-key）。</p>
 *
 * @author xinfei
 * @since 2026-08-31
 */
@Slf4j
@Component
public class GeoUtils {

    @Value("${xinfei.geo.amap.key:}")
    private String amapKey;

    @Value("${xinfei.geo.amap.geocode-url:https://restapi.amap.com/v3/geocode/geo}")
    private String geocodeUrl;

    @Value("${xinfei.geo.amap.regeocode-url:https://restapi.amap.com/v3/geocode/regeo}")
    private String regeocodeUrl;

    @Value("${xinfei.geo.amap.timeout-ms:3000}")
    private int timeoutMs;

    /** Key 是否可用（启动时检测一次，避免每次调用都判空） */
    private volatile boolean keyAvailable = false;

    /**
     * 初始化。
     */
    @PostConstruct
    public void init() {
        this.keyAvailable = StrUtil.isNotBlank(amapKey);
        if (!keyAvailable) {
            log.warn("[Geo] 高德地图 Key 未配置（xinfei.geo.amap.key），地理编码功能降级，地址簿经纬度将为空。生产环境请务必配置。");
        }
    }

    /**
     * 判断 Key 是否已配置
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

    /**
     * 地址→经纬度（地理编码）
     *
     * <p>高德返回的坐标为 GCJ-02 火星坐标系，与门店 {@code StoreInfo.longitude/latitude} 一致，
     * 可直接用于 {@code DeliveryEnhancedService.isInRange} 范围校验。</p>
     *
     * @param fullAddress 完整地址（省+市+区+详细地址）
     * @return [longitude, latitude]，调用失败或未配置 Key 时返回 null
     */
    public BigDecimal[] geocode(String fullAddress) {
        if (!keyAvailable || StrUtil.isBlank(fullAddress)) {
            return null;
        }
        try {
            // 高德地理编码 GET 请求：address + key + output=json
            String response = HttpUtil.get(geocodeUrl + "?address=" + URLUtil.encode(fullAddress)
                    + "&key=" + amapKey + "&output=JSON", timeoutMs);
            JSONObject json = JSONUtil.parseObj(response);
            String status = json.getStr("status");
            // status=1 表示请求成功；count>0 表示有结果
            if (!"1".equals(status) || json.getInt("count", 0) <= 0) {
                log.warn("[Geo] 地理编码失败，address={}, status={}, info={}", fullAddress, status, json.getStr("info"));
                return null;
            }
            JSONObject first = json.getJSONArray("geocodes").getJSONObject(0);
            // location 格式："116.397428,39.90923"（经度,纬度）
            String location = first.getStr("location");
            if (StrUtil.isBlank(location) || !location.contains(",")) {
                return null;
            }
            String[] lngLat = location.split(",");
            return new BigDecimal[]{
                    new BigDecimal(lngLat[0]).setScale(6, RoundingMode.HALF_UP),
                    new BigDecimal(lngLat[1]).setScale(6, RoundingMode.HALF_UP)
            };
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.warn("[Geo] 地理编码异常，address={}, 原因={}", fullAddress, e.getMessage());
            return null;
        }
    }
}
