package com.xinfei.module.cashier.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.controller.BaseControllerTest;
import com.xinfei.module.auth.model.Employee;
import com.xinfei.module.auth.service.EmployeeService;
import com.xinfei.module.cashier.model.CommissionRecord;
import com.xinfei.module.cashier.service.CommissionService;
import com.xinfei.module.order.model.Orders;
import com.xinfei.module.order.service.OrderService;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 员工提成（O-17 收银提成口径）集成测试：收银成功 → 按比例快照生成提成 → 汇总/明细接口。
 * <p>口径：基数 = 实收 - 找零；比例为空/0 不生成；0 元单不计提；比例快照不随后续修改变化。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"classpath:schema.sql", "classpath:schema-cashier.sql", "classpath:schema-payment-controller.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Transactional
public class CommissionCashierIntegrationTest extends BaseControllerTest {

    private static final long EMP_WITH_RATE = 993001L;
    private static final long EMP_NO_RATE = 993002L;
    private static final long ORDER_ID = 993101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CommissionService commissionService;

    @Autowired
    private com.xinfei.module.cashier.mapper.CommissionRecordMapper commissionRecordMapper;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    @Autowired
    private com.xinfei.test.TestSchemaPatcher schemaPatcher;

    @BeforeEach
    void setUp() {
        // 历史测试库 employee 表可能缺 V10 新列（test profile 不跑 Flyway），幂等补齐
        schemaPatcher.ensureColumn("employee", "commission_rate",
                "commission_rate decimal(5,2) NULL DEFAULT NULL COMMENT '提成比例(%)，NULL/0=不参与提成'");
        cleaner.cleanTables("cashier_record", "commission_record", "employee", "orders", "payment_order");
        testIdGuard.assertAbsent("employee", EMP_WITH_RATE, EMP_NO_RATE);
        // 元数据填充（create_user/create_time）与租户上下文
        BaseContext.setCurrentId(EMP_WITH_RATE);
        BaseContext.setCurrentTenantId(999L);
    }

    @Test
    @DisplayName("1. 收银成功按比例快照生成提成（基数=实收-找零）")
    void testCashPaymentGeneratesCommission() throws Exception {
        insertEmployee(EMP_WITH_RATE, new BigDecimal("5.00"));
        insertEatInOrder(ORDER_ID, "100.00");

        // 现金收 120 找零 20 → 基数 100，5% → 提成 5.00
        mockMvc.perform(withCsrfToken(mockMvc, post("/cashier/cash-payment"))
                        .sessionAttr("employee", EMP_WITH_RATE).sessionAttr("tenantId", 999L)
                        .param("orderId", String.valueOf(ORDER_ID))
                        .param("orderNumber", "COMM" + ORDER_ID)
                        .param("amount", "100.00")
                        .param("actualAmount", "120.00")
                        .param("payType", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        List<CommissionRecord> records = commissionService.page(1, 10, EMP_WITH_RATE, null).getRecords();
        assertEquals(1, records.size(), "应生成一条提成记录");
        CommissionRecord cr = records.get(0);
        assertEquals(0, new BigDecimal("100.00").compareTo(cr.getBaseAmount()), "基数应为实收-找零");
        assertEquals(0, new BigDecimal("5.00").compareTo(cr.getCommissionAmount()), "5% 提成应为 5.00");
        assertEquals(0, new BigDecimal("5.00").compareTo(cr.getRate()));
        assertEquals(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")), cr.getPeriod());
        assertEquals(CommissionRecord.STATUS_PENDING, cr.getStatus());
        assertEquals(EMP_WITH_RATE, cr.getEmployeeId());
    }

    @Test
    @DisplayName("2. 未配置比例的员工收银不生成提成")
    void testNoCommissionWithoutRate() throws Exception {
        insertEmployee(EMP_NO_RATE, null);
        insertEatInOrder(ORDER_ID, "100.00");

        mockMvc.perform(withCsrfToken(mockMvc, post("/cashier/cash-payment"))
                        .sessionAttr("employee", EMP_NO_RATE).sessionAttr("tenantId", 999L)
                        .param("orderId", String.valueOf(ORDER_ID))
                        .param("orderNumber", "COMM" + ORDER_ID)
                        .param("amount", "100.00")
                        .param("actualAmount", "100.00")
                        .param("payType", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        assertEquals(0, commissionService.page(1, 10, null, null).getTotal(), "无比例不应生成提成");
    }

    @Test
    @DisplayName("3. 提成汇总接口按员工聚合")
    void testSummaryByEmployee() throws Exception {
        insertEmployee(EMP_WITH_RATE, new BigDecimal("10.00"));
        // 直接造两条提成记录验证聚合（生成链路已由用例 1 覆盖）
        insertCommission(EMP_WITH_RATE, "提成员工", 993201L, "50.00", "10.00", "5.00");
        insertCommission(EMP_WITH_RATE, "提成员工", 993202L, "30.00", "10.00", "3.00");

        mockMvc.perform(get("/commission/summary")
                        .sessionAttr("employee", EMP_WITH_RATE).sessionAttr("tenantId", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data[0].employeeId").value(EMP_WITH_RATE))
                .andExpect(jsonPath("$.data[0].orderCount").value(2))
                .andExpect(jsonPath("$.data[0].baseAmount").value(80.0))
                .andExpect(jsonPath("$.data[0].commissionAmount").value(8.0));
    }

    @Test
    @DisplayName("4. 提成明细分页支持期间筛选")
    void testPageWithPeriodFilter() throws Exception {
        insertEmployee(EMP_WITH_RATE, new BigDecimal("5.00"));
        String period = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        insertCommission(EMP_WITH_RATE, "提成员工", 993203L, "20.00", "5.00", "1.00");

        mockMvc.perform(get("/commission/page")
                        .sessionAttr("employee", EMP_WITH_RATE).sessionAttr("tenantId", 999L)
                        .param("page", "1").param("pageSize", "10")
                        .param("period", period))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].employeeId").value(EMP_WITH_RATE))
                .andExpect(jsonPath("$.data.records[0].commissionAmount").value(1.0));
    }

    @Test
    @DisplayName("5. 比例快照：生成后改员工比例不影响历史提成")
    void testRateSnapshot() {
        insertEmployee(EMP_WITH_RATE, new BigDecimal("5.00"));
        insertCommission(EMP_WITH_RATE, "提成员工", 993204L, "100.00", "5.00", "5.00");
        // 改比例到 20%
        Employee emp = employeeService.getById(EMP_WITH_RATE);
        emp.setCommissionRate(new BigDecimal("20.00"));
        employeeService.updateById(emp);
        // 历史记录仍是 5%
        CommissionRecord cr = commissionService.page(1, 10, EMP_WITH_RATE, null).getRecords().get(0);
        assertEquals(0, new BigDecimal("5.00").compareTo(cr.getRate()));
        assertEquals(0, new BigDecimal("5.00").compareTo(cr.getCommissionAmount()));
        assertTrue(cr.getBaseAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    // ==================== helpers ====================

    private void insertEmployee(long id, BigDecimal rate) {
        Employee emp = new Employee();
        emp.setId(id);
        emp.setUsername("comm_test_" + id);
        emp.setName("提成员工" + id);
        emp.setPassword("test-password-not-for-login");
        emp.setStatus(1);
        emp.setRole(2);
        emp.setCommissionRate(rate);
        emp.setTenantId(999L);
        employeeService.save(emp);
    }

    private void insertEatInOrder(long orderId, String amount) {
        Orders order = new Orders();
        order.setId(orderId);
        order.setNumber("COMM" + orderId);
        order.setUserId(0L);
        order.setStatus(Orders.STATUS_PENDING_PAY);
        order.setAmount(new BigDecimal(amount));
        order.setOrderTime(LocalDateTime.now());
        order.setUserName("堂食顾客");
        order.setSource("EAT_IN");
        order.setTenantId(999L);
        orderService.save(order);
    }

    private void insertCommission(long employeeId, String name, long cashierRecordId,
                                  String base, String rate, String commission) {
        CommissionRecord cr = new CommissionRecord();
        cr.setEmployeeId(employeeId);
        cr.setEmployeeName(name);
        cr.setCashierRecordId(cashierRecordId);
        cr.setOrderId(ORDER_ID);
        cr.setBaseAmount(new BigDecimal(base));
        cr.setRate(new BigDecimal(rate));
        cr.setCommissionAmount(new BigDecimal(commission));
        cr.setPeriod(java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        cr.setStatus(CommissionRecord.STATUS_PENDING);
        commissionRecordMapper.insert(cr);
    }
}
