package com.xinfei.module.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.common.BaseContext;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.common.ObjectMapperHolder;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.enums.StockRecordType;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.module.inventory.model.Material;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.module.inventory.model.MaterialCategory;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.module.inventory.model.StockRecord;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.module.inventory.service.MaterialCategoryService;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.module.inventory.service.MaterialService;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.module.inventory.service.ReplenishService;
import org.springframework.transaction.annotation.Transactional;
import com.xinfei.module.inventory.service.StockRecordService;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

import static com.xinfei.module.inventory.model.Material.STATUS_NORMAL;
import org.springframework.transaction.annotation.Transactional;

/**
 * 智能补货服务实现
 * <p>
 * 核心算法：加权日均消耗 × 趋势因子 + 安全库存 + 补货周期 - 当前库存
 * - 加权日均消耗：近7天 weight=1.5，其余 weight=1.0
 * - 趋势因子（O-20）：近7天日均 / 前7天日均（8~14天前），夹在 [0.3, 2.0]；
 *   任一窗口无数据时为 1（无趋势信息沿用基础速率），需求上行/下行不再按平推错配
 * - 安全库存：日均消耗 × 2（应急2天）
 * - 建议采购量：日均消耗 × 补货周期 + 安全库存 - 当前库存
 * </p>
 * <p>
 * Redis 缓存：Key=inventory:replenish:suggest:{tenantId}:days={days}:cycle={replenishCycle}，TTL=10分钟
 * </p>
 *
 * @author xinfei
 * @since 2026-08-23
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class ReplenishServiceImpl implements ReplenishService {

    /** Redis 缓存 Key 前缀 */
    private static final String REDIS_KEY_PREFIX = "inventory:replenish:suggest:";

    /** 缓存 TTL：10 分钟 */
    private static final long CACHE_TTL_SECONDS = 600L;

    /** 安全库存倍数：日均消耗 × 2 天 */
    private static final BigDecimal SAFETY_STOCK_DAYS = new BigDecimal("2");

    /** 近7天权重 */
    private static final BigDecimal WEIGHT_RECENT = new BigDecimal("1.5");

    /** 其余天权重 */
    private static final BigDecimal WEIGHT_NORMAL = new BigDecimal("1.0");

    /** 趋势因子下限：需求骤降时最多下调至基础速率的 30%（防止单周异常清零误杀补货） */
    private static final BigDecimal TREND_FLOOR = new BigDecimal("0.3");

    /** 趋势因子上限：需求上行时最多上调至基础速率的 2 倍（防止短期脉冲过度囤货） */
    private static final BigDecimal TREND_CAP = new BigDecimal("2.0");

    /** 趋势对比窗口：近 7 天 vs 前 7 天（8~14 天前） */
    private static final int TREND_WINDOW_DAYS = 7;

    /** 统计天数默认值 */
    private static final int DEFAULT_DAYS = 30;

    /** 补货周期默认值 */
    private static final int DEFAULT_REPLENISH_CYCLE = 14;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private StockRecordService stockRecordService;

    @Autowired
    private MaterialCategoryService materialCategoryService;

    /**
     * Redis 模板，可选注入（Redis 不可用时降级为直接计算）
     */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取 replenish dashboard。
     * @param tenantId 参数 tenantId
     * @return 返回结果
     */
    @Override
    public Map<String, Object> getReplenishDashboard(Long tenantId) {
        log.info("[智能补货] 获取补货看板数据，tenantId={}", tenantId);

        List<Map<String, Object>> suggestions = getSmartReplenishSuggest(tenantId, DEFAULT_DAYS,
                DEFAULT_REPLENISH_CYCLE);

        Map<String, Object> dashboard = new HashMap<String, Object>();

        if (CollectionUtils.isEmpty(suggestions)) {
            dashboard.put("totalSuggestCount", 0);
            dashboard.put("urgentCount", 0);
            dashboard.put("criticalCount", 0);
            dashboard.put("warningCount", 0);
            dashboard.put("totalAmount", BigDecimal.ZERO);
            dashboard.put("top5", new ArrayList<Map<String, Object>>());
            dashboard.put("suggestions", new ArrayList<Map<String, Object>>());
            return dashboard;
        }

        int urgentCount = 0;
        int criticalCount = 0;
        int warningCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map<String, Object> item : suggestions) {
            String urgency = (String) item.get("urgency");
            int urgencyLevel = parseUrgencyLevel(urgency);

            if (urgencyLevel == 3) {
                urgentCount++;
            } else if (urgencyLevel == 2) {
                criticalCount++;
            } else if (urgencyLevel == 1) {
                warningCount++;
            }

            // 补货总金额 = suggestQty × unitPrice
            BigDecimal suggestQty = getBigDecimal(item, "suggestQty");
            BigDecimal unitPrice = getBigDecimal(item, "unitPrice");
            if (suggestQty != null && unitPrice != null) {
                totalAmount = totalAmount.add(suggestQty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
            }
        }

        // TOP5 即将断货（按 estimatedDays 升序取前5）
        List<Map<String, Object>> top5 = getTop5ByEstimatedDays(suggestions);

        dashboard.put("totalSuggestCount", suggestions.size());
        dashboard.put("urgentCount", urgentCount);
        dashboard.put("criticalCount", criticalCount);
        dashboard.put("warningCount", warningCount);
        dashboard.put("totalAmount", totalAmount);
        dashboard.put("top5", top5);
        dashboard.put("suggestions", suggestions);

        return dashboard;
    }

    /**
     * 获取 smart replenish suggest。
     * @param tenantId 参数 tenantId
     * @param days 参数 days
     * @param replenishCycle 参数 replenishCycle
     * @return 返回结果
     */
    @Override
    public List<Map<String, Object>> getSmartReplenishSuggest(Long tenantId, int days, int replenishCycle) {
        log.info("[智能补货] 获取补货建议，tenantId={}, days={}, replenishCycle={}", tenantId, days, replenishCycle);

        if (days <= 0) {
            days = DEFAULT_DAYS;
        }
        if (replenishCycle <= 0) {
            replenishCycle = DEFAULT_REPLENISH_CYCLE;
        }

        // 尝试从 Redis 缓存读取
        List<Map<String, Object>> cached = tryGetFromCache(tenantId, days, replenishCycle);
        if (cached != null) {
            log.info("[智能补货] 命中 Redis 缓存，tenantId={}", tenantId);
            return cached;
        }

        // 查询所有启用的食材（租户隔离）
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            qw.eq(Material::getTenantId, tenantId);
        }
        qw.eq(Material::getStatus, STATUS_NORMAL);
        List<Material> allMaterials = materialService.list(qw);

        if (CollectionUtils.isEmpty(allMaterials)) {
            List<Map<String, Object>> empty = new ArrayList<Map<String, Object>>();
            tryPutToCache(tenantId, days, replenishCycle, empty);
            return empty;
        }

        // 查询近 N 天所有出库记录（一次性查询，按物料分组后在内存中计算日均消耗，避免逐物料查流水的 N+1）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(days);
        LambdaQueryWrapper<StockRecord> outQw = new LambdaQueryWrapper<>();
        outQw.eq(StockRecord::getType, StockRecordType.OUT.getValue());
        outQw.ge(StockRecord::getCreatedTime, since);
        outQw.le(StockRecord::getCreatedTime, now);
        if (tenantId != null) {
            outQw.eq(StockRecord::getTenantId, tenantId);
        }
        List<StockRecord> outRecords = stockRecordService.list(outQw);
        Map<Long, List<StockRecord>> outRecordsByMaterial = outRecords.stream()
                .filter(r -> r.getMaterialId() != null)
                .collect(Collectors.groupingBy(StockRecord::getMaterialId));

        // 批量查询物料分类名（等价抽取）
        Map<Long, String> categoryNameMap = loadCategoryNameMap(allMaterials);

        // 计算补货建议（等价抽取）
        List<Map<String, Object>> suggestList = buildSuggestList(allMaterials, days, tenantId, replenishCycle,
                categoryNameMap, outRecordsByMaterial, now);

        // 排序：按紧急度等级降序（紧急在前），同等级按 estimatedDays 升序
        sortByUrgency(suggestList);

        // 写入 Redis 缓存
        tryPutToCache(tenantId, days, replenishCycle, suggestList);

        log.info("[智能补货] 补货建议计算完成，tenantId={}, 共{}种食材需补货", tenantId, suggestList.size());
        return suggestList;
    }

    /**
     * 批量加载物料分类名映射（等价抽取，降低方法长度）。
     *
     * @param allMaterials 食材列表
     * @return 分类ID -> 分类名
     */
    private Map<Long, String> loadCategoryNameMap(List<Material> allMaterials) {
        Map<Long, String> categoryNameMap = new HashMap<Long, String>();
        Set<Long> categoryIds = allMaterials.stream()
                .map(Material::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (!categoryIds.isEmpty()) {
            List<MaterialCategory> categories = materialCategoryService.listByIds(categoryIds);
            for (MaterialCategory c : categories) {
                if (c != null && c.getName() != null) {
                    categoryNameMap.put(c.getId(), c.getName());
                }
            }
        }
        return categoryNameMap;
    }

    /**
     * 计算补货建议明细列表（等价抽取，降低方法长度）。
     *
     * @param allMaterials 食材列表
     * @param days 统计天数
     * @param tenantId 租户ID
     * @param replenishCycle 补货周期
     * @param categoryNameMap 分类名映射
     * @param outRecordsByMaterial 近 N 天出库流水按物料ID分组的映射（内存消费，避免逐物料查库）
     * @param now 统计基准时间（与流水查询窗口一致）
     * @return 补货建议列表
     */
    private List<Map<String, Object>> buildSuggestList(List<Material> allMaterials, int days, Long tenantId,
            int replenishCycle, Map<Long, String> categoryNameMap,
            Map<Long, List<StockRecord>> outRecordsByMaterial, LocalDateTime now) {
        List<Map<String, Object>> suggestList = new ArrayList<Map<String, Object>>();
        BigDecimal replenishCycleDays = new BigDecimal(replenishCycle);

        for (Material m : allMaterials) {
            BigDecimal stockQty = m.getStockQty() != null ? m.getStockQty() : BigDecimal.ZERO;
            List<StockRecord> materialRecords = outRecordsByMaterial.get(m.getId());
            Map<String, BigDecimal> dailyOutMap = aggregateDailyOut(
                    materialRecords != null ? materialRecords : new ArrayList<StockRecord>());
            // 日均消耗 = 加权基础速率 × 趋势因子（O-20：需求上行/下行不再按平推错配）
            BigDecimal dailyUsage = weightedDailyUsage(dailyOutMap, now);
            BigDecimal trendRatio = trendRatio(dailyOutMap, now);
            if (trendRatio.compareTo(BigDecimal.ONE) != 0) {
                dailyUsage = dailyUsage.multiply(trendRatio).setScale(4, RoundingMode.HALF_UP);
            }

            // 安全库存 = 日均消耗 × 2
            BigDecimal safetyStock = dailyUsage.multiply(SAFETY_STOCK_DAYS).setScale(2, RoundingMode.HALF_UP);

            // 预计可用天数
            int estimatedDays = calcEstimatedDays(stockQty, dailyUsage);

            // 紧急度等级
            String urgency = calcUrgency(estimatedDays);

            // 建议采购量 = 日均消耗 × 补货周期 + 安全库存 - 当前库存
            BigDecimal suggestQty = dailyUsage.multiply(replenishCycleDays)
                    .add(safetyStock)
                    .subtract(stockQty)
                    .setScale(2, RoundingMode.HALF_UP);

            // 只有需要补货的食材才列入建议（suggestQty > 0）
            if (suggestQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Map<String, Object> item = new HashMap<String, Object>();
            item.put("materialId", m.getId());
            item.put("materialName", m.getName());
            item.put("categoryName", categoryNameMap.get(m.getCategoryId()));
            item.put("unit", m.getUnit());
            item.put("stockQty", stockQty);
            item.put("dailyUsage", dailyUsage);
            // 口径透明（O-20）：趋势因子随建议输出，前端可标注「按需求趋势已上/下调」
            item.put("trendRatio", trendRatio);
            item.put("safetyStock", safetyStock);
            item.put("estimatedDays", estimatedDays);
            item.put("urgency", urgency);
            item.put("suggestQty", suggestQty);
            item.put("unitPrice", m.getUnitPrice());
            item.put("supplierId", m.getSupplierId());
            item.put("supplierName", m.getSupplierName());
            item.put("replenishCycle", replenishCycle);
            suggestList.add(item);
        }
        return suggestList;
    }

    /**
     * 计算 weighted daily usage。
     * @param materialId 参数 materialId
     * @param days 参数 days
     * @param tenantId 参数 tenantId
     * @return 返回结果
     */
    @Override
    public BigDecimal calcWeightedDailyUsage(Long materialId, int days, Long tenantId) {
        if (days <= 0) {
            days = DEFAULT_DAYS;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(days);

        // 查询该食材近 N 天出库记录
        LambdaQueryWrapper<StockRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(StockRecord::getMaterialId, materialId);
        qw.eq(StockRecord::getType, StockRecordType.OUT.getValue());
        qw.ge(StockRecord::getCreatedTime, since);
        qw.le(StockRecord::getCreatedTime, now);
        if (tenantId != null) {
            qw.eq(StockRecord::getTenantId, tenantId);
        }
        List<StockRecord> records = stockRecordService.list(qw);

        return weightedDailyUsageFromRecords(records, now);
    }

    /**
     * 基于一批出库流水计算加权日均消耗（近7天 weight=1.5，其余 weight=1.0）。
     * <p>纯内存计算：供 {@link #calcWeightedDailyUsage} 查库后复用，
     * 也供补货建议在一次性查出全部流水后按物料分组消费（消 N+1）。</p>
     *
     * @param records 该物料的出库流水
     * @param now     加权判断基准时间
     * @return 加权日均消耗
     */
    private BigDecimal weightedDailyUsageFromRecords(List<StockRecord> records, LocalDateTime now) {
        return weightedDailyUsage(aggregateDailyOut(records), now);
    }

    /**
     * 按天汇总出库流水：key=日期字符串(yyyy-MM-dd), value=当天出库总量。
     * <p>包级静态纯函数，供趋势因子单测直接构造输入。</p>
     *
     * @param records 出库流水（可为 null/空，返回空 Map）
     * @return 按天汇总 Map
     */
    static Map<String, BigDecimal> aggregateDailyOut(List<StockRecord> records) {
        Map<String, BigDecimal> dailyOutMap = new HashMap<String, BigDecimal>();
        if (CollectionUtils.isEmpty(records)) {
            return dailyOutMap;
        }
        for (StockRecord r : records) {
            LocalDateTime createdTime = r.getCreatedTime();
            if (createdTime == null || r.getQty() == null) {
                continue;
            }
            // 使用日期字符串作为 key（JDK 1.8 兼容）
            String dateKey = formatLocalDate(createdTime.toLocalDate());
            BigDecimal existing = dailyOutMap.get(dateKey);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            BigDecimal qty = r.getQty() != null ? r.getQty() : BigDecimal.ZERO;
            dailyOutMap.put(dateKey, existing.add(qty));
        }
        return dailyOutMap;
    }

    /**
     * 基于按天汇总 Map 计算加权日均消耗（近7天 weight=1.5，其余 weight=1.0）。
     */
    private BigDecimal weightedDailyUsage(Map<String, BigDecimal> dailyOutMap, LocalDateTime now) {
        if (dailyOutMap == null || dailyOutMap.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalWeighted = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : dailyOutMap.entrySet()) {
            String dateKey = entry.getKey();
            BigDecimal dailyOut = entry.getValue();

            // 判断该日期距离今天的天数
            int daysAgo = calcDaysAgo(dateKey, now);
            BigDecimal weight = daysAgo <= 7 ? WEIGHT_RECENT : WEIGHT_NORMAL;

            totalWeighted = totalWeighted.add(dailyOut.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal weightedDailyUsage = totalWeighted.divide(totalWeight, 4, RoundingMode.HALF_UP);
        log.debug("[智能补货] 加权日均消耗={}, 加权总量={}, 权重总量={}",
                weightedDailyUsage, totalWeighted, totalWeight);
        return weightedDailyUsage;
    }

    /**
     * 近 7 天日均出库（趋势因子分子）。
     * <p>窗口内无任何数据返回 null（区分「零消耗」与「无数据」，前者参与趋势、后者不参与）。</p>
     */
    static BigDecimal recentDailyAvg(Map<String, BigDecimal> dailyOutMap, LocalDateTime now) {
        return windowDailyAvg(dailyOutMap, now, 0, TREND_WINDOW_DAYS);
    }

    /**
     * 前 7 天（8~14 天前）日均出库（趋势因子分母）。窗口内无任何数据返回 null。
     */
    static BigDecimal priorDailyAvg(Map<String, BigDecimal> dailyOutMap, LocalDateTime now) {
        return windowDailyAvg(dailyOutMap, now, TREND_WINDOW_DAYS + 1, TREND_WINDOW_DAYS * 2);
    }

    /**
     * 计算指定天数区间 [minDaysAgo, maxDaysAgo) 的日均出库；窗口内无数据返回 null。
     */
    private static BigDecimal windowDailyAvg(Map<String, BigDecimal> dailyOutMap, LocalDateTime now,
            int minDaysAgo, int maxDaysAgo) {
        if (dailyOutMap == null || dailyOutMap.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        int dataDays = 0;
        for (Map.Entry<String, BigDecimal> entry : dailyOutMap.entrySet()) {
            int daysAgo = calcDaysAgoStatic(entry.getKey(), now);
            if (daysAgo >= minDaysAgo && daysAgo <= maxDaysAgo) {
                total = total.add(entry.getValue());
                dataDays += 1;
            }
        }
        // 窗口内没有任何出库日：无数据（null），不与「零消耗」混淆
        if (dataDays == 0) {
            return null;
        }
        return total.divide(new BigDecimal(dataDays), 4, RoundingMode.HALF_UP);
    }

    /**
     * 趋势因子（O-20）：近 7 天日均 / 前 7 天日均，夹在 [0.3, 2.0]。
     * <p>无趋势信息（任一窗口无数据）返回 1；前窗有数据但零消耗且近窗有消耗，
     * 视为新品/新菜上行，按上限放大。</p>
     */
    static BigDecimal trendRatio(Map<String, BigDecimal> dailyOutMap, LocalDateTime now) {
        BigDecimal recent = recentDailyAvg(dailyOutMap, now);
        BigDecimal prior = priorDailyAvg(dailyOutMap, now);
        if (recent == null || prior == null) {
            return BigDecimal.ONE;
        }
        if (prior.compareTo(BigDecimal.ZERO) <= 0) {
            return recent.compareTo(BigDecimal.ZERO) > 0 ? TREND_CAP : BigDecimal.ONE;
        }
        BigDecimal ratio = recent.divide(prior, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(TREND_FLOOR) < 0) {
            return TREND_FLOOR;
        }
        if (ratio.compareTo(TREND_CAP) > 0) {
            return TREND_CAP;
        }
        return ratio;
    }

    /** 静态版天数差（与实例方法 calcDaysAgo 同口径，供纯函数使用） */
    private static int calcDaysAgoStatic(String dateKey, LocalDateTime now) {
        java.time.LocalDate today = now.toLocalDate();
        java.time.LocalDate target = java.time.LocalDate.parse(dateKey);
        long diff = java.time.temporal.ChronoUnit.DAYS.between(target, today);
        return diff >= 0 ? (int) diff : 0;
    }

    /**
     * 计算 estimated days。
     * @param stockQty 参数 stockQty
     * @param dailyUsage 参数 dailyUsage
     * @return 返回结果
     */
    @Override
    public int calcEstimatedDays(BigDecimal stockQty, BigDecimal dailyUsage) {
        if (stockQty == null) {
            stockQty = BigDecimal.ZERO;
        }
        if (dailyUsage == null || dailyUsage.compareTo(BigDecimal.ZERO) <= 0) {
            // 日均消耗为0或空，表示该食材暂无消耗记录，视为无限期可用
            return Integer.MAX_VALUE;
        }
        BigDecimal result = stockQty.divide(dailyUsage, 0, RoundingMode.FLOOR);
        return result.intValue();
    }

    /**
     * 计算 urgency。
     * @param estimatedDays 参数 estimatedDays
     * @return 返回结果
     */
    @Override
    public String calcUrgency(int estimatedDays) {
        if (estimatedDays <= 1) {
            return "3"; // 紧急：≤1天
        } else if (estimatedDays <= 3) {
            return "2"; // 紧迫：≤3天
        } else if (estimatedDays <= 7) {
            return "1"; // 关注：≤7天
        }
        return "0"; // 充足：>7天
    }

    // ==================== 私有方法 ====================

    /**
     * 从 Redis 缓存中尝试读取补货建议数据
     * <p>
     * 缓存格式：JSON 数组字符串（写入侧序列化的是 List，读取侧必须按 List 反序列化）
     * Key: inventory:replenish:suggest:{tenantId}:days={days}:cycle={replenishCycle}
     * （days/replenishCycle 是计算维度，必须参与 Key，否则不同参数会串用缓存）
     * </p>
     *
     * @return 缓存中的数据列表，未命中或 Redis 不可用时返回 null
     */
    private List<Map<String, Object>> tryGetFromCache(Long tenantId, int days, int replenishCycle) {
        if (redisTemplate == null || tenantId == null) {
            return null;
        }
        String redisKey = buildCacheKey(tenantId, days, replenishCycle);
        try {
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (cached == null) {
                return null;
            }
            String json;
            if (cached instanceof String) {
                json = (String) cached;
            } else {
                json = ObjectMapperHolder.getDefault().writeValueAsString(cached);
            }
            tools.jackson.databind.ObjectMapper mapper = ObjectMapperHolder.getDefault();
            // 写入侧序列化 List<Map>（JSON 数组），按 List.class 反序列化；
            // 原 readValue(json, Map.class) 对数组必抛 MismatchedInputException 导致缓存永不命中
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JacksonException e) {
            log.warn("[智能补货] Redis 缓存反序列化失败，按未命中处理并降级重算：{}", e.getMessage());
            return null;
        } catch (Exception e) {
            // 宽异常兜底：Redis 故障等不应影响主流程，仅记录降级日志
            log.warn("[智能补货] Redis 读取缓存失败，降级为直接计算：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建补货建议缓存 Key（包含 tenantId/days/replenishCycle 三个计算维度）
     */
    private String buildCacheKey(Long tenantId, int days, int replenishCycle) {
        return REDIS_KEY_PREFIX + tenantId + ":days=" + days + ":cycle=" + replenishCycle;
    }

    /**
     * 将补货建议数据写入 Redis 缓存
     *
     * @param tenantId       租户ID
     * @param days           统计天数
     * @param replenishCycle 补货周期
     * @param data           补货建议数据
     */
    private void tryPutToCache(Long tenantId, int days, int replenishCycle, List<Map<String, Object>> data) {
        if (redisTemplate == null || tenantId == null) {
            return;
        }
        String redisKey = buildCacheKey(tenantId, days, replenishCycle);
        try {
            tools.jackson.databind.ObjectMapper mapper = ObjectMapperHolder.getDefault();
            String json = mapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(redisKey, json, CACHE_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            log.debug("[智能补货] 补货建议数据已缓存，redisKey={}, size={}", redisKey, data.size());
        } catch (JacksonException e) {
            log.warn("[智能补货] JSON 序列化失败，跳过缓存：{}", e.getMessage());
        } catch (Exception e) {
            // 宽异常兜底：有意捕获 Exception，避免单个失败影响主流程
            log.warn("[智能补货] Redis 写入缓存失败，降级：{}", e.getMessage());
        }
    }

    /**
     * 按紧急度等级降序、预计可用天数升序对列表排序
     * 使用冒泡排序（JDK 1.8 兼容，且数据量通常不大）
     */
    private void sortByUrgency(List<Map<String, Object>> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                Map<String, Object> a = list.get(j);
                Map<String, Object> b = list.get(j + 1);

                int urgencyA = parseUrgencyLevel((String) a.get("urgency"));
                int urgencyB = parseUrgencyLevel((String) b.get("urgency"));

                // 同等级按 estimatedDays 升序（断货风险大的在前）——等价重构，用布尔表达式消除嵌套
                boolean sameUrgency = urgencyB == urgencyA;
                int daysA = sameUrgency ? (Integer) a.get("estimatedDays") : 0;
                int daysB = sameUrgency ? (Integer) b.get("estimatedDays") : 0;
                boolean shouldSwap = urgencyB > urgencyA || (sameUrgency && daysB < daysA);

                if (shouldSwap) {
                    Map<String, Object> tmp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, tmp);
                }
            }
        }
    }

    /**
     * 按预计可用天数升序取前5（即将断货TOP5）
     */
    private List<Map<String, Object>> getTop5ByEstimatedDays(List<Map<String, Object>> suggestions) {
        if (CollectionUtils.isEmpty(suggestions)) {
            return new ArrayList<Map<String, Object>>();
        }

        // 先按 estimatedDays 升序排序（副本）
        List<Map<String, Object>> sorted = new ArrayList<Map<String, Object>>(suggestions);
        sortByEstimatedDays(sorted);

        int limit = Math.min(5, sorted.size());
        List<Map<String, Object>> top5 = new ArrayList<Map<String, Object>>(limit);
        for (int i = 0; i < limit; i++) {
            top5.add(sorted.get(i));
        }
        return top5;
    }

    /**
     * 按预计可用天数升序排序（冒泡排序）
     */
    private void sortByEstimatedDays(List<Map<String, Object>> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size() - 1 - i; j++) {
                Map<String, Object> a = list.get(j);
                Map<String, Object> b = list.get(j + 1);

                int daysA = (Integer) a.get("estimatedDays");
                int daysB = (Integer) b.get("estimatedDays");
                if (daysB < daysA) {
                    Map<String, Object> tmp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, tmp);
                }
            }
        }
    }

    /**
     * 解析紧急度等级为整数
     */
    private int parseUrgencyLevel(String urgency) {
        if (urgency == null) {
            return 0;
        }
        try {
            return Integer.parseInt(urgency);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 安全获取 Map 中的 BigDecimal 值
     */
    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        if (val == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 格式化 LocalDate 为 yyyy-MM-dd 字符串（JDK 1.8 兼容）
     */
    private static String formatLocalDate(java.time.LocalDate date) {
        return String.valueOf(date); // JDK 1.8 的 LocalDate.toString() 已返回 yyyy-MM-dd
    }

    /**
     * 计算日期字符串距离今天的天数
     *
     * @param dateKey 日期字符串 yyyy-MM-dd
     * @param now     当前时间
     * @return 天数差（≥0）
     */
    private int calcDaysAgo(String dateKey, LocalDateTime now) {
        java.time.LocalDate today = now.toLocalDate();
        java.time.LocalDate target = java.time.LocalDate.parse(dateKey);
        long diff = java.time.temporal.ChronoUnit.DAYS.between(target, today);
        return diff >= 0 ? (int) diff : 0;
    }
}