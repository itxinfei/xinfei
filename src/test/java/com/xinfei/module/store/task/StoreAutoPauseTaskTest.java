package com.xinfei.module.store.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StoreAutoPauseTask#isInBusinessHours} 纯函数单测（O-23）：
 * 正常区间/跨午夜区间/边界/未配置/坏格式各分支。
 *
 * @author xinfei
 * @since 2026-10-24
 */
class StoreAutoPauseTaskTest {

    @Test
    @DisplayName("1. 正常区间：营业内/外判定")
    void testNormalRange() {
        LocalTime ten = LocalTime.of(10, 0);
        LocalTime night = LocalTime.of(23, 0);
        assertTrue(StoreAutoPauseTask.isInBusinessHours("09:00-22:00", ten));
        assertFalse(StoreAutoPauseTask.isInBusinessHours("09:00-22:00", night));
    }

    @Test
    @DisplayName("2. 跨午夜区间：22:00-06:00，深夜/凌晨在营业")
    void testCrossMidnight() {
        assertTrue(StoreAutoPauseTask.isInBusinessHours("22:00-06:00", LocalTime.of(23, 30)));
        assertTrue(StoreAutoPauseTask.isInBusinessHours("22:00-06:00", LocalTime.of(5, 0)));
        assertFalse(StoreAutoPauseTask.isInBusinessHours("22:00-06:00", LocalTime.of(12, 0)));
    }

    @Test
    @DisplayName("3. 边界含端点：开始/结束时刻均在营业内")
    void testBoundaries() {
        assertTrue(StoreAutoPauseTask.isInBusinessHours("09:00-22:00", LocalTime.of(9, 0)));
        assertTrue(StoreAutoPauseTask.isInBusinessHours("09:00-22:00", LocalTime.of(22, 0)));
    }

    @Test
    @DisplayName("4. 未配置/空白/坏格式 → null（不介入，尊重现状）")
    void testUnparseableReturnsNull() {
        assertNull(StoreAutoPauseTask.isInBusinessHours(null, LocalTime.NOON));
        assertNull(StoreAutoPauseTask.isInBusinessHours("  ", LocalTime.NOON));
        assertNull(StoreAutoPauseTask.isInBusinessHours("九点到十点", LocalTime.NOON));
        assertNull(StoreAutoPauseTask.isInBusinessHours("09:00", LocalTime.NOON));
    }
}
