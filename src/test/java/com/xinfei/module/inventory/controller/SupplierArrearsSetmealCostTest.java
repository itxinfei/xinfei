package com.xinfei.module.inventory.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.controller.BaseControllerTest;
import com.xinfei.module.cost.model.DishCost;
import com.xinfei.module.cost.service.CostService;
import com.xinfei.module.inventory.model.Supplier;
import com.xinfei.module.inventory.model.SupplierSettlement;
import com.xinfei.module.inventory.service.SupplierService;
import com.xinfei.module.inventory.service.SupplierSettlementService;
import com.xinfei.module.setmeal.model.Setmeal;
import com.xinfei.module.setmeal.model.SetmealDish;
import com.xinfei.module.setmeal.service.SetmealDishService;
import com.xinfei.module.setmeal.service.SetmealService;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O-11 集成测试：供应商未结清欠款回填（supplier_settlement 真实数据源）
 * + 套餐 BOM 成本试算（dish_cost 口径，缺成本按 0 计并标注）。
 *
 * @author xinfei
 * @since 2026-10-24
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = {"classpath:schema.sql", "classpath:schema-inventory.sql", "classpath:schema-cost.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class SupplierArrearsSetmealCostTest extends BaseControllerTest {

    private static final long SUPPLIER_ID = 991001L;
    private static final long SUPPLIER_ID_CLEAN = 991002L;
    private static final long SETMEAL_ID = 991101L;
    private static final long DISH_WITH_COST = 991111L;
    private static final long DISH_NO_COST = 991112L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private SupplierSettlementService supplierSettlementService;

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private SetmealDishService setmealDishService;

    @Autowired
    private CostService costService;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    @BeforeEach
    void setUp() {
        cleaner.cleanTables("supplier", "supplier_settlement", "purchase_order",
                "setmeal", "setmeal_dish", "dish_cost");
        testIdGuard.assertAbsent("supplier", SUPPLIER_ID, SUPPLIER_ID_CLEAN);
        BaseContext.setCurrentId(1L);
        BaseContext.setCurrentTenantId(999L);
    }

    @Test
    @DisplayName("1. 供应商欠款 = PENDING 结算单 Σ(总额-已付)，PAID 不计、无结算单为 0")
    void testSupplierPageArrears() throws Exception {
        insertSupplier(SUPPLIER_ID, "欠款供应商");
        insertSupplier(SUPPLIER_ID_CLEAN, "无结算供应商");
        // PENDING 部分付款：100 - 30 = 70；PENDING 未付：50；PAID 已结清：不计
        insertSettlement(SUPPLIER_ID, "100.00", "30.00", "PENDING");
        insertSettlement(SUPPLIER_ID, "50.00", "0.00", "PENDING");
        insertSettlement(SUPPLIER_ID, "200.00", "200.00", "PAID");

        mockMvc.perform(get("/api/inventory/supplier/page")
                        .param("page", "1").param("pageSize", "10")
                        .sessionAttr("employee", 1L).sessionAttr("tenantId", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.records[?(@.id==" + SUPPLIER_ID + ")].arrears").value(120.0))
                .andExpect(jsonPath("$.data.records[?(@.id==" + SUPPLIER_ID_CLEAN + ")].arrears").value(0));
    }

    @Test
    @DisplayName("2. 套餐 BOM 成本 = Σ(菜品成本×份数)，缺成本按 0 计并在 missingDishCount 标注")
    void testSetmealCost() throws Exception {
        insertSetmealWithDishes();

        mockMvc.perform(get("/setmeal/" + SETMEAL_ID + "/cost")
                        .sessionAttr("employee", 1L).sessionAttr("tenantId", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.totalCost").value(10.0))
                .andExpect(jsonPath("$.data.profit").value(20.0))
                .andExpect(jsonPath("$.data.profitRate").value(66.7))
                .andExpect(jsonPath("$.data.missingDishCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[?(@.dishId==" + DISH_WITH_COST + ")].lineCost").value(10.0))
                .andExpect(jsonPath("$.data.caliber").isNotEmpty());
    }

    // ==================== helpers ====================

    private void insertSupplier(long id, String name) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setName(name);
        supplier.setStatus(1);
        supplier.setTenantId(999L);
        supplierService.save(supplier);
    }

    private void insertSettlement(long supplierId, String total, String paid, String status) {
        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setSupplierId(supplierId);
        settlement.setPeriod("2026-10");
        settlement.setTotalAmount(new BigDecimal(total));
        settlement.setPaidAmount(new BigDecimal(paid));
        settlement.setStatus(status);
        settlement.setTenantId(999L);
        supplierSettlementService.save(settlement);
    }

    /** 套餐 30 元：含成本菜品 ×2 份（单位成本 5）+ 无成本菜品 ×1 份 */
    private void insertSetmealWithDishes() {
        Setmeal setmeal = new Setmeal();
        setmeal.setId(SETMEAL_ID);
        setmeal.setCategoryId(1L);
        setmeal.setName("测试套餐");
        setmeal.setPrice(new BigDecimal("30.00"));
        setmeal.setStatus(1);
        setmeal.setTenantId(999L);
        setmealService.save(setmeal);

        insertSetmealDish(DISH_WITH_COST, "有成本菜品", 2);
        insertSetmealDish(DISH_NO_COST, "无成本菜品", 1);

        DishCost dishCost = new DishCost();
        dishCost.setDishId(DISH_WITH_COST);
        dishCost.setDishName("有成本菜品");
        dishCost.setMaterialCost(new BigDecimal("5.00"));
        dishCost.setTotalCost(new BigDecimal("5.00"));
        dishCost.setTenantId(999L);
        costService.saveOrUpdateDishCost(dishCost);
    }

    private void insertSetmealDish(long dishId, String name, int copies) {
        SetmealDish sd = new SetmealDish();
        sd.setSetmealId(SETMEAL_ID);
        sd.setDishId(dishId);
        sd.setName(name);
        sd.setPrice(new BigDecimal("12.00"));
        sd.setCopies(copies);
        sd.setTenantId(999L);
        setmealDishService.save(sd);
    }
}
