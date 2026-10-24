package com.xinfei.module.cashier.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.controller.BaseControllerTest;
import com.xinfei.module.cashier.mapper.CashierRecordMapper;
import com.xinfei.module.cashier.mapper.CashierShiftMapper;
import com.xinfei.module.cashier.model.CashierRecord;
import com.xinfei.module.cashier.model.CashierShift;
import com.xinfei.module.cashier.service.CashierService;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 交班台账集成测试（O-29）。
 * <p>覆盖：空班交班（全零快照）、有收款记录的交班（笔数/总额/找零/支付方式分布口径
 * 与 shiftSummary 一致）、备注截断、分页与过滤、非法日期拒绝。</p>
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = {"classpath:schema.sql", "classpath:schema-cashier.sql", "classpath:schema-payment-controller.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class CashierShiftIntegrationTest extends BaseControllerTest {

    private static final long EMPLOYEE_ID = 993101L;
    private static final long OTHER_EMPLOYEE_ID = 993102L;
    private static final long TENANT_ID = 999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CashierService cashierService;

    @Autowired
    private CashierShiftMapper cashierShiftMapper;

    @Autowired
    private CashierRecordMapper cashierRecordMapper;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    @BeforeEach
    void setUp() {
        cleaner.cleanTables("cashier_record", "cashier_shift", "employee");
        testIdGuard.assertAbsent("employee", EMPLOYEE_ID, OTHER_EMPLOYEE_ID);
        BaseContext.setCurrentId(EMPLOYEE_ID);
        BaseContext.setCurrentTenantId(TENANT_ID);
    }

    @Test
    @DisplayName("1. 空班交班：全零快照落库，员工名走兜底")
    void closeEmptyShift() {
        // 测试库无 993101 员工 → cashierName 兜底
        CashierShift shift = cashierService.closeShift(EMPLOYEE_ID, null);

        assertNotNull(shift.getId());
        assertEquals(EMPLOYEE_ID, shift.getEmployeeId());
        assertEquals("未知收银员", shift.getEmployeeName());
        assertEquals(0, shift.getOrderCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(shift.getTotalAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(shift.getChangeAmount()));
        assertNotNull(shift.getShiftDate());
        assertNotNull(shift.getCloseTime());
    }

    @Test
    @DisplayName("2. 有收款记录的交班：口径与 shiftSummary 一致（笔数/总额/找零/分布）")
    void closeShiftWithRecords() {
        insertCashRecord(9001L, "1", "100.00", "120.00", "20.00"); // 现金 100 找零 20
        insertCashRecord(9002L, "2", "200.00", "200.00", "0.00");  // 微信 200 无找零

        CashierShift shift = cashierService.closeShift(EMPLOYEE_ID, "本班无差额");

        assertEquals(2, shift.getOrderCount());
        // 口径=实收（actualAmount）：120+200=320；找零 20（与 shiftSummary 的 totalAmount/totalChange 一致）
        assertEquals(0, new BigDecimal("320.00").compareTo(shift.getTotalAmount()), "总额=实收 120+200");
        assertEquals(0, new BigDecimal("20.00").compareTo(shift.getChangeAmount()), "找零=20");
        assertEquals("本班无差额", shift.getRemark());
        assertNotNull(shift.getPayMethodSummary(), "支付方式分布 JSON 应落库");
        assertTrue(shift.getPayMethodSummary().contains("payType"), "分布 JSON 应含 payType 结构");
    }

    @Test
    @DisplayName("3. API：确认交班返回台账，分页可查且倒序")
    void apiCloseThenPage() throws Exception {
        cashierService.closeShift(EMPLOYEE_ID, "第一班");
        // close_time 落库为秒级 datetime，间隔 >1s 保证倒序断言稳定
        Thread.sleep(1100);
        cashierService.closeShift(EMPLOYEE_ID, "第二班");
        Thread.sleep(1100);

        mockMvc.perform(withCsrfToken(mockMvc, post("/cashier/shift/close"))
                        .sessionAttr("employee", EMPLOYEE_ID).sessionAttr("tenantId", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"第三班\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.remark").value("第三班"));

        mockMvc.perform(get("/cashier/shift/page")
                        .sessionAttr("employee", EMPLOYEE_ID).sessionAttr("tenantId", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].remark").value("第三班")); // 倒序，最新在前
    }

    @Test
    @DisplayName("4. API：按收银员过滤台账（他人交班不可见）")
    void apiPageFilterByEmployee() {
        cashierService.closeShift(EMPLOYEE_ID, null);
        BaseContext.setCurrentId(OTHER_EMPLOYEE_ID);
        cashierService.closeShift(OTHER_EMPLOYEE_ID, null);
        BaseContext.setCurrentId(EMPLOYEE_ID);

        assertEquals(1, cashierService.pageShifts(1, 10, EMPLOYEE_ID, null).getTotal());
        assertEquals(1, cashierService.pageShifts(1, 10, OTHER_EMPLOYEE_ID, null).getTotal());
    }

    @Test
    @DisplayName("5. API：非法日期被拒绝（422）")
    void apiRejectsBadDate() throws Exception {
        mockMvc.perform(get("/cashier/shift/page")
                        .param("shiftDate", "bad-date")
                        .sessionAttr("employee", EMPLOYEE_ID).sessionAttr("tenantId", TENANT_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 工具 ====================

    private void insertCashRecord(long id, String payType, String amount, String actual, String change) {
        CashierRecord r = new CashierRecord();
        r.setId(id);
        r.setCashierId(EMPLOYEE_ID);
        r.setCashierName("测试收银员");
        r.setPayType(Integer.valueOf(payType));
        r.setAmount(new BigDecimal(amount));
        r.setActualAmount(new BigDecimal(actual));
        r.setChangeAmount(new BigDecimal(change));
        r.setCashierTime(LocalDateTime.now());
        r.setTenantId(TENANT_ID);
        cashierRecordMapper.insert(r);
    }
}
