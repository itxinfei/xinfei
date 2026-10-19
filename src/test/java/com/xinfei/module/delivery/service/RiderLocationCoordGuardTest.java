package com.xinfei.module.delivery.service;

import com.xinfei.common.BaseContext;
import com.xinfei.module.delivery.service.DeliveryTrackingService;
import com.xinfei.test.TestIdGuard;
import com.xinfei.utils.GeoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1 骑手定位加固守卫（2026-10-19 百度地图改造第一段）：
 *  1) GeoUtils.normalizeCoordSystem 归一化：已知值大写化，空/未知回退；
 *  2) updateRiderLocation 落库标记坐标系（rider 当前位置与轨迹行一致）；
 *  3) 留存清理 cleanupLocationRecordsBefore 只删早于 cutoff 的行，且不跨租户误删。
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = {"classpath:schema.sql", "classpath:schema-rider.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RiderLocationCoordGuardTest {

    private static final long TENANT = 999L;
    private static final long OTHER_TENANT = 888L;
    private static final long RIDER_ID = 998840L;

    @Autowired
    private DeliveryTrackingService deliveryTrackingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestIdGuard testIdGuard;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentTenantId(TENANT);
        BaseContext.setCurrentId(1L);
        // 先按「主键 + 租户」有界清删本用例上次运行残留，绝不触碰演示/其它租户同主键行
        jdbcTemplate.update("DELETE FROM rider_location_record WHERE rider_id = ? AND tenant_id = ?",
                RIDER_ID, TENANT);
        jdbcTemplate.update("DELETE FROM rider WHERE id = ? AND tenant_id = ?", RIDER_ID, TENANT);
        testIdGuard.assertAbsent("rider", RIDER_ID);
        jdbcTemplate.update("INSERT INTO rider (id, name, status, tenant_id) VALUES (?, 'P1定位测试骑手', 1, ?)",
                RIDER_ID, TENANT);
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove();
        jdbcTemplate.update("DELETE FROM rider_location_record WHERE rider_id = ? AND tenant_id = ?",
                RIDER_ID, TENANT);
        jdbcTemplate.update("DELETE FROM rider WHERE id = ? AND tenant_id = ?", RIDER_ID, TENANT);
    }

    private static BigDecimal bd(double v) {
        return new BigDecimal(String.valueOf(v));
    }

    @Test
    void normalizeCoordSystemUppercasesKnownAndFallsBackOnUnknown() {
        assertEquals("WGS-84", GeoUtils.normalizeCoordSystem(null, "WGS-84"));
        assertEquals("WGS-84", GeoUtils.normalizeCoordSystem("  wgs-84 ", "WGS-84"));
        assertEquals("GCJ-02", GeoUtils.normalizeCoordSystem("gcj-02", "WGS-84"));
        assertEquals("BD-09", GeoUtils.normalizeCoordSystem("BD-09", "WGS-84"));
        // 未知/火星系拼写 → 回退到调用方给的 fallback
        assertEquals("WGS-84", GeoUtils.normalizeCoordSystem("mars-hill", "WGS-84"));
        assertEquals("", GeoUtils.normalizeCoordSystem("bogus", ""));
    }

    @Test
    void updateRiderLocationRecordsCoordinateSystemOnBothRiderAndHistory() {
        // 声明 GCJ-02（大小写混写应被归一化）→ rider 当前位置与最新轨迹行均记 GCJ-02
        deliveryTrackingService.updateRiderLocation(RIDER_ID, bd(116.5000), bd(39.9000), null, null, "gcj-02");
        assertEquals("GCJ-02",
                jdbcTemplate.queryForObject("SELECT coord_system FROM rider WHERE id = ?", String.class, RIDER_ID));
        assertEquals("GCJ-02", jdbcTemplate.queryForObject(
                "SELECT coord_system FROM rider_location_record WHERE rider_id = ? ORDER BY id DESC LIMIT 1",
                String.class, RIDER_ID));

        // 未知坐标系 → 服务端回退登记为 WGS-84，混系写入从此可判别
        deliveryTrackingService.updateRiderLocation(RIDER_ID, bd(116.5000), bd(39.9000), null, null, "not-a-system");
        assertEquals("WGS-84", jdbcTemplate.queryForObject(
                "SELECT coord_system FROM rider_location_record WHERE rider_id = ? ORDER BY id DESC LIMIT 1",
                String.class, RIDER_ID));
    }

    @Test
    void cleanupPurgesOnlyExpiredAndDoesNotCrossTenants() {
        LocalDateTime now = LocalDateTime.now();
        // 本租户：1 条新鲜 + 1 条 100 天前
        insertRecord(now, "WGS-84");
        insertRecord(now.minusDays(100), "WGS-84");
        // 其它租户一条 100 天前的过期轨迹，按 30 天窗口清理时不得被本租户上下文误删
        jdbcTemplate.update("INSERT INTO rider_location_record "
                        + "(rider_id, longitude, latitude, coord_system, record_time, tenant_id) "
                        + "VALUES (?, 116.1, 39.1, 'WGS-84', ?, ?)",
                RIDER_ID, java.sql.Timestamp.valueOf(now.minusDays(100)), OTHER_TENANT);

        int deleted = deliveryTrackingService.cleanupLocationRecordsBefore(now.minusDays(30));

        // 只删掉本租户那条过期记录
        assertEquals(1, deleted);
        Integer mineLeft = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rider_location_record WHERE rider_id = ? AND tenant_id = ?",
                Integer.class, RIDER_ID, TENANT);
        assertEquals(1, mineLeft.intValue());
        // 保留的是新鲜那条，非过期
        String leftTime = jdbcTemplate.queryForObject(
                "SELECT DATE(record_time) FROM rider_location_record WHERE rider_id = ? AND tenant_id = ?",
                String.class, RIDER_ID, TENANT);
        assertEquals(now.toLocalDate().toString(), leftTime);
        // 其它租户的过期行未被触碰
        Integer otherLeft = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rider_location_record WHERE rider_id = ? AND tenant_id = ?",
                Integer.class, RIDER_ID, OTHER_TENANT);
        assertEquals(1, otherLeft.intValue());
        // 清掉那条其它租户遗留行（有界），保持库干净
        jdbcTemplate.update("DELETE FROM rider_location_record WHERE rider_id = ? AND tenant_id = ?",
                RIDER_ID, OTHER_TENANT);
    }

    private void insertRecord(LocalDateTime recordTime, String coordSystem) {
        jdbcTemplate.update("INSERT INTO rider_location_record "
                        + "(rider_id, longitude, latitude, coord_system, record_time, tenant_id) "
                        + "VALUES (?, 116.5, 39.9, ?, ?, ?)",
                RIDER_ID, coordSystem, java.sql.Timestamp.valueOf(recordTime), TENANT);
    }
}
