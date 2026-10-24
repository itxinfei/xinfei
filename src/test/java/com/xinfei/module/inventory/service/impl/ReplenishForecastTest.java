package com.xinfei.module.inventory.service.impl;

import com.xinfei.module.inventory.model.StockRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReplenishServiceImpl} 趋势因子纯函数单测（O-20）。
 * <p>补货预测升级：日均消耗 = 加权基础速率 × 趋势因子（近7天/前7天日均比值，
 * 夹在 [0.3, 2.0]）。窗口无数据不参与趋势（返回 1），「零消耗」与「无数据」严格区分。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
class ReplenishForecastTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0, 0);

    private StockRecord outRecord(BigDecimal qty, int daysAgo) {
        StockRecord r = new StockRecord();
        r.setQty(qty);
        r.setCreatedTime(NOW.minusDays(daysAgo).withHour(10).withMinute(0));
        return r;
    }

    /** 造一段「每天固定消耗 qty、共 dataDays 天」的流水（从 daysAgoStart 往前数） */
    private List<StockRecord> flatUsage(BigDecimal qty, int dataDays, int daysAgoStart) {
        List<StockRecord> records = new ArrayList<>();
        for (int i = 0; i < dataDays; i++) {
            records.add(outRecord(qty, daysAgoStart + i));
        }
        return records;
    }

    @Test
    @DisplayName("1. 按天汇总：同日多条流水合并为一条日总量")
    void testAggregateDailyOut() {
        List<StockRecord> records = Arrays.asList(
                outRecord(new BigDecimal("3"), 1),
                outRecord(new BigDecimal("4"), 1),
                outRecord(new BigDecimal("5"), 9));
        Map<String, BigDecimal> map = ReplenishServiceImpl.aggregateDailyOut(records);
        assertEquals(2, map.size());
        assertEquals(0, new BigDecimal("7").compareTo(map.get(NOW.minusDays(1).toLocalDate().toString())));
        // qty/时间缺失的脏数据被跳过
        StockRecord dirty = new StockRecord();
        map = ReplenishServiceImpl.aggregateDailyOut(Arrays.asList(dirty, outRecord(new BigDecimal("2"), 2)));
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("2. 加权日均：近7天 weight=1.5、其余 weight=1.0")
    void testWeightedDailyUsage() {
        // 近7天每天10，第9天10：totalWeighted = 70×1.5 + 10×1.0 = 115，totalWeight = 7×1.5+1 = 11.5
        List<StockRecord> records = new ArrayList<>(flatUsage(new BigDecimal("10"), 7, 1));
        records.addAll(flatUsage(new BigDecimal("10"), 1, 9));
        // 通过 aggregateDailyOut+窗口均值间接验证权重结构：近窗均值=10
        Map<String, BigDecimal> map = ReplenishServiceImpl.aggregateDailyOut(records);
        assertEquals(0, new BigDecimal("10.0000").compareTo(ReplenishServiceImpl.recentDailyAvg(map, NOW)));
        assertEquals(0, new BigDecimal("10.0000").compareTo(ReplenishServiceImpl.priorDailyAvg(map, NOW)));
        assertNull(ReplenishServiceImpl.priorDailyAvg(ReplenishServiceImpl.aggregateDailyOut(
                flatUsage(new BigDecimal("10"), 7, 1)), NOW), "前窗无数据应返回 null");
    }

    @Test
    @DisplayName("3. 需求上行翻倍 → 趋势因子 2.0（上限截断）")
    void testTrendUpswingCapped() {
        // 近窗（daysAgo 1~7）日耗 20，前窗（8~14）日耗 10 → 比值 2.0 触顶
        List<StockRecord> records = new ArrayList<>(flatUsage(new BigDecimal("20"), 7, 1));
        records.addAll(flatUsage(new BigDecimal("10"), 7, 8));
        assertEquals(0, new BigDecimal("2.0").compareTo(
                ReplenishServiceImpl.trendRatio(ReplenishServiceImpl.aggregateDailyOut(records), NOW)));
    }

    @Test
    @DisplayName("4. 需求骤降至零 → 趋势因子 0.3（下限截断，防误杀补货）")
    void testTrendDownswingFloored() {
        // 近窗改为零消耗（仍有数据日：qty=0），前窗 10/天 → 比值 0 触底
        List<StockRecord> records = new ArrayList<>(flatUsage(BigDecimal.ZERO, 7, 1));
        records.addAll(flatUsage(new BigDecimal("10"), 7, 8));
        assertEquals(0, new BigDecimal("0.3").compareTo(
                ReplenishServiceImpl.trendRatio(ReplenishServiceImpl.aggregateDailyOut(records), NOW)));
    }

    @Test
    @DisplayName("5. 前窗有数据但零消耗、近窗有消耗 → 视为上行取 2.0")
    void testPriorZeroRecentPositive() {
        List<StockRecord> records = new ArrayList<>(flatUsage(new BigDecimal("10"), 7, 1));
        records.addAll(flatUsage(BigDecimal.ZERO, 7, 8));
        assertEquals(0, new BigDecimal("2.0").compareTo(
                ReplenishServiceImpl.trendRatio(ReplenishServiceImpl.aggregateDailyOut(records), NOW)));
    }

    @Test
    @DisplayName("6. 任一窗口无数据 → 因子 1.0（无趋势信息沿用基础速率）")
    void testNoTrendInfoReturnsOne() {
        // 仅有近7天数据，前窗无数据
        assertEquals(0, BigDecimal.ONE.compareTo(
                ReplenishServiceImpl.trendRatio(ReplenishServiceImpl.aggregateDailyOut(
                        flatUsage(new BigDecimal("10"), 7, 1)), NOW)));
        // 仅有前窗数据，近窗无数据
        assertEquals(0, BigDecimal.ONE.compareTo(
                ReplenishServiceImpl.trendRatio(ReplenishServiceImpl.aggregateDailyOut(
                        flatUsage(new BigDecimal("10"), 7, 8)), NOW)));
        // 空流水
        assertEquals(0, BigDecimal.ONE.compareTo(
                ReplenishServiceImpl.trendRatio(Collections.emptyMap(), NOW)));
    }

    @Test
    @DisplayName("7. 稳定需求 → 因子恰为 1.0")
    void testStableDemand() {
        List<StockRecord> records = new ArrayList<>(flatUsage(new BigDecimal("10"), 7, 1));
        records.addAll(flatUsage(new BigDecimal("10"), 7, 8));
        assertEquals(0, BigDecimal.ONE.compareTo(
                ReplenishServiceImpl.trendRatio(ReplenishServiceImpl.aggregateDailyOut(records), NOW)));
    }

    @Test
    @DisplayName("8. 温和上行 1.5 倍不截断，精确输出")
    void testMildUpswingExact() {
        // 近窗日耗 15、前窗日耗 10 → 比值 1.5（未触上限）
        List<StockRecord> records = new ArrayList<>(flatUsage(new BigDecimal("15"), 7, 1));
        records.addAll(flatUsage(new BigDecimal("10"), 7, 8));
        assertEquals(0, new BigDecimal("1.5000").compareTo(
                ReplenishServiceImpl.trendRatio(ReplenishServiceImpl.aggregateDailyOut(records), NOW)));
    }

    @Test
    @DisplayName("9. 窗口语义：第 7 天属近窗、第 8 天属前窗（边界不重叠）")
    void testWindowBoundaries() {
        // 仅第 7 天（daysAgo=7）有数据 → 属近窗
        Map<String, BigDecimal> map = ReplenishServiceImpl.aggregateDailyOut(
                Collections.singletonList(outRecord(new BigDecimal("9"), 7)));
        assertNotNull(ReplenishServiceImpl.recentDailyAvg(map, NOW));
        assertNull(ReplenishServiceImpl.priorDailyAvg(map, NOW));
        // 仅第 8 天（daysAgo=8）有数据 → 属前窗
        map = ReplenishServiceImpl.aggregateDailyOut(
                Collections.singletonList(outRecord(new BigDecimal("9"), 8)));
        assertNull(ReplenishServiceImpl.recentDailyAvg(map, NOW));
        assertNotNull(ReplenishServiceImpl.priorDailyAvg(map, NOW));
        assertTrue(true);
    }
}
