package com.xinfei.module.inventory.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InventoryAlertTask} 纯函数单测（O-26）：差分/序列化/反序列化各分支。
 *
 * @author xinfei
 * @since 2026-10-24
 */
class InventoryAlertTaskTest {

    @Test
    @DisplayName("1. 基线未建立（last=null）：不推送，由调用方记录基线")
    void testBaselineMissing() {
        InventoryAlertTask.AlertDiff diff = InventoryAlertTask.diff(null, Set.of(1L, 2L));
        assertTrue(diff.entered().isEmpty());
        assertTrue(diff.recovered().isEmpty());
    }

    @Test
    @DisplayName("2. 无变迁：不推送")
    void testNoChange() {
        InventoryAlertTask.AlertDiff diff = InventoryAlertTask.diff(Set.of(1L, 2L), Set.of(2L, 1L));
        assertTrue(diff.entered().isEmpty());
        assertTrue(diff.recovered().isEmpty());
    }

    @Test
    @DisplayName("3. 进入预警：current 有 last 无")
    void testEntered() {
        InventoryAlertTask.AlertDiff diff = InventoryAlertTask.diff(Set.of(1L), Set.of(1L, 2L, 3L));
        assertEquals(List.of(2L, 3L), diff.entered());
        assertTrue(diff.recovered().isEmpty());
    }

    @Test
    @DisplayName("4. 恢复：last 有 current 无")
    void testRecovered() {
        InventoryAlertTask.AlertDiff diff = InventoryAlertTask.diff(Set.of(1L, 2L), Set.of(1L));
        assertTrue(diff.entered().isEmpty());
        assertEquals(List.of(2L), diff.recovered());
    }

    @Test
    @DisplayName("5. 同时进入与恢复（边沿双向）")
    void testBothDirections() {
        InventoryAlertTask.AlertDiff diff = InventoryAlertTask.diff(Set.of(1L, 2L), Set.of(2L, 3L));
        assertEquals(List.of(3L), diff.entered());
        assertEquals(List.of(1L), diff.recovered());
    }

    @Test
    @DisplayName("6. 空集边界：last=[] 视为已建立基线（空集），current 非空即全量进入")
    void testEmptyBaselineEstablished() {
        // parseLast 对 "[]" 返回 null（视为无基线）——语义对齐「首次部署不误报」
        assertNull(InventoryAlertTask.parseLast("[]"));
        // 但若上层以空集合作为基线传入 diff，则按正常差分处理
        InventoryAlertTask.AlertDiff diff = InventoryAlertTask.diff(Set.of(), Set.of(1L));
        assertEquals(List.of(1L), diff.entered());
    }

    @Test
    @DisplayName("7. 序列化/反序列化往返：坏值按无基线处理")
    void testSerializeRoundTrip() {
        String encoded = InventoryAlertTask.joinIds(Set.of(3L, 1L, 2L));
        assertEquals("[1,2,3]", encoded);
        assertEquals(Set.of(1L, 2L, 3L), InventoryAlertTask.parseLast(encoded));
        assertNull(InventoryAlertTask.parseLast(null));
        assertNull(InventoryAlertTask.parseLast("bad,[1"));
        assertNull(InventoryAlertTask.parseLast(""));
    }
}
