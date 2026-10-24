package com.xinfei.module.dashboard.controller;

import com.xinfei.common.BaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工作台待办计数（待评价/待退款）与订单时段分布聚合接口测试
 * <p>夹具：schema-dashboard.sql（租户 999）
 * <ul>
 *   <li>待评价：订单 99001（已完成、菜品未评价）计入；99002（已评价）、99003（已取消）不计</li>
 *   <li>待退款：refund_record 99101（pending）计入；99102（success）不计</li>
 *   <li>时段分布：今日 10 点 2 单（99001/99003）、11 点 1 单（99002）</li>
 * </ul>
 * 注意：overview 的 KPI 部分走 Redis 缓存可能含其它测试残留，
 * 断言只针对实时合并的 pendingEvaluation/pendingRefund 字段。
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "classpath:schema-dashboard.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class DashboardTodoAndHourlyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);
        BaseContext.setCurrentTenantId(999L);
    }

    @Test
    void testOverviewContainsTodoCounts() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview")
                        .sessionAttr("employee", 1L)
                        .sessionAttr("tenantId", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.pendingEvaluation").value(1))
                .andExpect(jsonPath("$.data.pendingRefund").value(1));
    }

    @Test
    void testAllContainsTodoCountsAndHourly() throws Exception {
        mockMvc.perform(get("/api/dashboard/all").param("hotDishLimit", "5")
                        .sessionAttr("employee", 1L)
                        .sessionAttr("tenantId", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.overview.pendingEvaluation").value(1))
                .andExpect(jsonPath("$.data.overview.pendingRefund").value(1))
                .andExpect(jsonPath("$.data.hourly.length()").value(24));
    }

    @Test
    void testHourlyDistributionToday() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/hourly-distribution")
                        .sessionAttr("employee", 1L)
                        .sessionAttr("tenantId", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                // 固定返回 24 个小时槽位（0-23），无订单的小时补 0
                .andExpect(jsonPath("$.data.length()").value(24))
                .andExpect(jsonPath("$.data[0].hour").value("00:00"))
                .andExpect(jsonPath("$.data[23].hour").value("23:00"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // 夹具中今日订单分布在 10 点（99001/99003）与 11 点（99002）
        assertTrue(body.contains("\"10:00\""), "应包含 10:00 时段槽位");
        assertTrue(body.contains("\"11:00\""), "应包含 11:00 时段槽位");
    }

    @Test
    void testHourlyDistributionLast7Days() throws Exception {
        mockMvc.perform(get("/api/dashboard/hourly-distribution").param("days", "7")
                        .sessionAttr("employee", 1L)
                        .sessionAttr("tenantId", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.length()").value(24));
    }
}
