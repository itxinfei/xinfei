package com.xinfei.module.inventory.task;

import com.xinfei.common.BaseContext;
import com.xinfei.common.RedisLockUtil;
import com.xinfei.module.auth.model.Employee;
import com.xinfei.module.auth.service.EmployeeService;
import com.xinfei.module.inventory.model.Material;
import com.xinfei.module.inventory.service.MaterialService;
import com.xinfei.module.notification.sse.EmployeeSseManager;
import com.xinfei.module.tenant.model.Tenant;
import com.xinfei.module.tenant.service.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 库存预警实时推送任务（O-26）——员工 SSE 通道（O-25 建成）的首个预警事件源。
 * <p>
 * 检测口径与库存预警页一致：{@code stock_qty < min_stock} 且状态正常（MaterialService.checkWarning）。
 * 库存变更入口分散（订单扣减/采购入库/盘点调整/直接改库），故采用**定时差分扫描**而非在每个
 * 变更点埋事件：单点接入覆盖全部路径，边沿触发天然节流（无状态变迁不推送）。
 * 与 O-23 自动接单任务同族模式：分布式锁单节点执行 + 逐租户设置 BaseContext（租户插件注入）+
 * Redis 记录上次状态做差分；**Redis 无历史（首次部署/键过期）时只记录基线不推送**，防止
 * 重启后对存量预警误报一轮。
 * </p>
 * <p>推送对象：该租户全部在职员工的壳层 SSE 通道（离线员工由导出中心同款管理器静默跳过；
 * 离线补看需员工站内信中心，登记未做）。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Component
public class InventoryAlertTask {

    private static final long LOCK_TTL_SECONDS = 120L;
    private static final String LAST_KEY_PREFIX = "inventory:alert:last:";

    /** 预警/恢复清单在 SSE 载荷中的物料条数上限（防极端租户刷屏，超出部分以「等 N 项」收尾） */
    private static final int MAX_ITEMS_IN_PAYLOAD = 10;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeSseManager employeeSseManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisLockUtil redisLockUtil;

    /** 默认 5 分钟一轮：预警不需要分钟级时效，控制扫描成本 */
    @Scheduled(initialDelayString = "${xinfei.alert.initial-delay:10000}",
            fixedDelayString = "${xinfei.alert.sweep-interval:300000}")
    public void sweep() {
        redisLockUtil.executeWithLock("inventory:lock:alert-sweep", LOCK_TTL_SECONDS, () -> {
            List<Tenant> tenants = tenantService.listActiveTenants();
            if (tenants == null || tenants.isEmpty()) {
                return;
            }
            for (Tenant tenant : tenants) {
                Long originalTenantId = BaseContext.getCurrentTenantId();
                BaseContext.setCurrentTenantId(tenant.getId());
                try {
                    sweepTenant(tenant.getId());
                } catch (Exception e) {
                    log.error("[库存预警] 租户 {} 扫描失败: {}", tenant.getId(), e.getMessage());
                } finally {
                    if (originalTenantId != null) {
                        BaseContext.setCurrentTenantId(originalTenantId);
                    } else {
                        BaseContext.remove();
                    }
                }
            }
        });
    }

    /**
     * 手动触发口（InventoryAlertController）：只扫当前租户（请求上下文），与定时任务共用
     * 同一套状态键、差分与推送链路。
     */
    public void sweepCurrentTenant() {
        Long tenantId = BaseContext.getCurrentTenantId();
        if (tenantId == null) {
            log.warn("[库存预警] 手动扫描被拒绝：请求无租户上下文");
            return;
        }
        sweepTenant(tenantId);
    }

    /**
     * 单租户差分：当前预警集 vs Redis 上次集 → 进入/恢复两类变迁推送。
     */
    private void sweepTenant(Long tenantId) {
        List<Material> warnings = materialService.checkWarning();
        Set<Long> current = warnings.stream().map(Material::getId).collect(Collectors.toSet());

        String key = LAST_KEY_PREFIX + tenantId;
        Set<Long> last = parseLast(redisTemplate.opsForValue().get(key));
        AlertDiff diff = diff(last, current);

        // 无论是否有变迁都回写基线（值即现状，TTL 自愈孤儿键）
        redisTemplate.opsForValue().set(key, joinIds(current));

        if (diff.entered.isEmpty() && diff.recovered.isEmpty()) {
            return;
        }
        Map<Long, Material> byId = warnings.stream()
                .collect(Collectors.toMap(Material::getId, m -> m, (a, b) -> a));
        if (!diff.entered.isEmpty()) {
            push(tenantId, "entered", pick(byId, diff.entered));
        }
        if (!diff.recovered.isEmpty()) {
            // 恢复物料已不在预警集，按 ID 反查不到名称，仅推数量（避免为名字多查一轮）
            push(tenantId, "recovered", Collections.emptyList(), diff.recovered.size());
        }
        log.info("[库存预警] 租户 {} 推送完成: 进入 {} 项, 恢复 {} 项", tenantId, diff.entered.size(), diff.recovered.size());
    }

    private void push(Long tenantId, String kind, List<Material> items) {
        push(tenantId, kind, items, items.size());
    }

    private void push(Long tenantId, String kind, List<Material> items, int count) {
        List<Map<String, Object>> payloadItems = new ArrayList<>();
        for (Material m : items.stream().limit(MAX_ITEMS_IN_PAYLOAD).collect(Collectors.toList())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("name", m.getName());
            item.put("stockQty", m.getStockQty());
            item.put("minStock", m.getMinStock());
            payloadItems.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind);
        payload.put("count", count);
        payload.put("items", payloadItems);
        for (Long employeeId : activeEmployeeIds(tenantId)) {
            employeeSseManager.sendToUser(employeeId, "inventory-alert", payload);
        }
    }

    /**
     * 进入预警的物料按「库存/安全库存」比值升序取前 N（最危急的先进弹条）。
     */
    private List<Material> pick(Map<Long, Material> byId, List<Long> ids) {
        return ids.stream().map(byId::get).filter(m -> m != null)
                .sorted(Comparator.comparing(m -> m.getStockQty().divide(
                        m.getMinStock().max(java.math.BigDecimal.ONE), 4, java.math.RoundingMode.HALF_UP)))
                .limit(MAX_ITEMS_IN_PAYLOAD)
                .collect(Collectors.toList());
    }

    /** 租户内全部在职员工 ID（employee 表在租户插件忽略列表，手动隔离） */
    private List<Long> activeEmployeeIds(Long tenantId) {
        LambdaQueryWrapper<Employee> qw = new LambdaQueryWrapper<>();
        qw.eq(Employee::getTenantId, tenantId);
        qw.eq(Employee::getStatus, 1);
        qw.select(Employee::getId);
        List<Employee> list = employeeService.list(qw);
        return list == null ? List.of() : list.stream().map(Employee::getId).collect(Collectors.toList());
    }

    // ==================== 纯函数（包级静态，供单测） ====================

    /** 差分结果：entered=本次新进入预警，recovered=本次恢复到安全线以上 */
    record AlertDiff(List<Long> entered, List<Long> recovered) { }

    /**
     * 集合差分。last 为 null 表示基线未建立（首次部署/键过期）：返回空 diff 不推送（保守，
     * 与 O-23「无历史记录不自动恢复」同一口径），由调用方记录基线。
     */
    static AlertDiff diff(Set<Long> last, Set<Long> current) {
        Set<Long> cur = current == null ? Set.of() : current;
        if (last == null) {
            return new AlertDiff(List.of(), List.of());
        }
        Set<Long> entered = new HashSet<>(cur);
        entered.removeAll(last);
        Set<Long> recovered = new HashSet<>(last);
        recovered.removeAll(cur);
        return new AlertDiff(new ArrayList<>(entered), new ArrayList<>(recovered));
    }

    /** Redis 值 → ID 集合（坏值/空值按「无基线」处理） */
    static Set<Long> parseLast(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "[]".equals(s) || "null".equals(s)) {
            return null;
        }
        try {
            Set<Long> out = new HashSet<>();
            for (String part : s.replace("[", "").replace("]", "").split(",")) {
                String p = part.trim();
                if (!p.isEmpty()) {
                    out.add(Long.valueOf(p));
                }
            }
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** ID 集合 → Redis 值（升序稳定序列，便于人工核查） */
    static String joinIds(Set<Long> ids) {
        List<Long> sorted = new ArrayList<>(ids == null ? Set.of() : ids);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(sorted.get(i));
        }
        return sb.append(']').toString();
    }
}
