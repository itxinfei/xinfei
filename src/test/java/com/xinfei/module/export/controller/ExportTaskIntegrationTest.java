package com.xinfei.module.export.controller;

import com.xinfei.common.BaseContext;
import com.xinfei.controller.BaseControllerTest;
import com.xinfei.module.export.mapper.ExportTaskMapper;
import com.xinfei.module.export.model.ExportTask;
import com.xinfei.module.export.service.ExportTaskService;
import com.xinfei.test.TestDatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 异步导出任务中心集成测试（O-25）。
 * <p>覆盖：任务创建（API + 服务直调）、异步执行落文件、下载、删除、
 * 非法类型拒绝、排队任务不可下载、跨租户不可见。</p>
 * <p>导出文件写入 {@code target/test-export-files}（@SpringBootTest properties 覆盖 xinfei.export.dir），
 * 用例间靠 TestDatabaseCleaner（租户 999）+ 目录重建隔离。</p>
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class,
        properties = "xinfei.export.dir=target/test-export-files")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = {"classpath:schema.sql", "classpath:schema-export.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class ExportTaskIntegrationTest extends BaseControllerTest {

    private static final long EMPLOYEE_ID = 9001L;
    private static final long TENANT_ID = 999L;
    /** 隔离性测试用的另一测试租户（不与演示租户 1 / 测试租户 999 重叠），测试后按条件清理 */
    private static final long OTHER_TENANT_ID = 998L;
    private static final Path EXPORT_DIR = Paths.get("target/test-export-files");
    private static final String EXCEL_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExportTaskService exportTaskService;

    @Autowired
    private ExportTaskMapper exportTaskMapper;

    @Autowired
    private TestDatabaseCleaner cleaner;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        cleaner.cleanTables("export_task");
        BaseContext.setCurrentId(EMPLOYEE_ID);
        BaseContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        // 隔离性用例使用租户 998，cleanTables 只按 999 清理，这里按条件兜底清干净
        cleaner.cleanByCondition("export_task", "tenant_id IN (?, ?)", OTHER_TENANT_ID, TENANT_ID);
    }

    @Test
    @DisplayName("服务直调：创建→同步执行→文件落盘→分页可见")
    void createExecuteFlow() {
        ExportTask task = exportTaskService.create(ExportTask.TYPE_REPORT, ExportTask.FORMAT_EXCEL,
                Map.of("startDate", "2026-07-01", "endDate", "2026-07-01"));

        assertNotNull(task.getId());
        assertEquals(ExportTask.STATUS_PENDING, task.getStatus());

        exportTaskService.execute(task.getId());

        ExportTask done = exportTaskMapper.selectById(task.getId());
        assertNotNull(done);
        assertEquals(ExportTask.STATUS_SUCCESS, done.getStatus());
        assertNotNull(done.getFileName());
        assertTrue(done.getFileName().startsWith("经营报表"), "下载文件名应含类型中文: " + done.getFileName());
        assertNotNull(done.getFileSize());
        assertTrue(done.getFileSize() > 0);
        assertTrue(Files.exists(exportTaskService.taskFile(done)),
                "生成文件应存在于配置的测试目录: " + exportTaskService.taskFile(done));

        // 分页查询可见
        assertFalse(exportTaskService.page(1, 10, null, null).getRecords().isEmpty());
    }

    @Test
    @DisplayName("API：创建任务立即返回排队态，异步执行后可下载文件")
    void apiCreateThenAsyncExecuteThenDownload() throws Exception {
        MvcResult result = mockMvc.perform(withCsrfToken(mockMvc, post("/export/tasks"))
                        .sessionAttr("employee", EMPLOYEE_ID)
                        .sessionAttr("tenantId", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "taskType", "orders",
                                "fileFormat", "excel",
                                "params", Map.of("startDate", "2026-07-01", "endDate", "2026-07-02")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.status").value(ExportTask.STATUS_PENDING))
                .andExpect(jsonPath("$.data.taskType").value("orders"))
                .andReturn();

        Long taskId = extractTaskId(result);
        assertNotNull(taskId);

        // 异步执行由 exportTaskExecutor 完成（响应快照恒为排队态，终态轮询数据库）
        ExportTask latest = awaitTerminal(taskId, 15000L);
        assertEquals(ExportTask.STATUS_SUCCESS, latest.getStatus(),
                "订单导出（空数据）应成功，失败原因: " + latest.getErrorMsg());

        // 下载端点返回文件流
        mockMvc.perform(get("/export/tasks/" + taskId + "/file")
                        .sessionAttr("employee", EMPLOYEE_ID)
                        .sessionAttr("tenantId", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", EXCEL_CONTENT_TYPE));
    }

    @Test
    @DisplayName("users 类型：创建→执行→文件名含「用户数据」")
    void usersTypeFlow() {
        ExportTask task = exportTaskService.create(ExportTask.TYPE_USERS, ExportTask.FORMAT_EXCEL, Map.of());
        exportTaskService.execute(task.getId());
        ExportTask done = exportTaskMapper.selectById(task.getId());
        assertNotNull(done);
        assertEquals(ExportTask.STATUS_SUCCESS, done.getStatus(),
                "用户导出（空数据）应成功: " + done.getErrorMsg());
        assertTrue(done.getFileName().startsWith("用户数据"), "下载文件名应含类型中文: " + done.getFileName());
    }

    @Test
    @DisplayName("SSE 订阅端点：员工会话建立长连接（asyncStarted）")
    void sseSubscribeStartsAsync() throws Exception {
        MvcResult result = mockMvc.perform(get("/export/tasks/subscribe")
                        .sessionAttr("employee", EMPLOYEE_ID)
                        .sessionAttr("tenantId", TENANT_ID))
                .andReturn();
        // Spring 7 下挂起的 SseEmitter 不做 asyncDispatch（永不完成会等到超时）；
        // 只要请求进入异步即说明端点已注册 emitter，事件投递由浏览器 E2E 覆盖
        assertTrue(result.getRequest().isAsyncStarted(), "SSE 订阅应进入异步长连接");
    }

    @Test
    @DisplayName("API：非法类型被拒绝")
    void apiRejectsUnknownType() throws Exception {
        mockMvc.perform(withCsrfToken(mockMvc, post("/export/tasks"))
                        .sessionAttr("employee", EMPLOYEE_ID)
                        .sessionAttr("tenantId", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "taskType", "bogus", "fileFormat", "excel", "params", Map.of()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("排队中任务不可下载")
    void pendingTaskNotDownloadable() {
        ExportTask task = exportTaskService.create(ExportTask.TYPE_EMPLOYEES, ExportTask.FORMAT_PDF, Map.of());
        assertThrows(com.xinfei.common.CustomException.class,
                () -> exportTaskService.getDownloadable(task.getId()));
    }

    @Test
    @DisplayName("删除任务：记录逻辑删除 + 文件清除")
    void deleteRemovesRecordAndFile() {
        ExportTask task = exportTaskService.create(ExportTask.TYPE_DISHES, ExportTask.FORMAT_EXCEL, Map.of());
        exportTaskService.execute(task.getId());
        Path file = exportTaskService.taskFile(exportTaskMapper.selectById(task.getId()));
        assertTrue(Files.exists(file));

        exportTaskService.delete(task.getId());

        assertFalse(Files.exists(file), "删除任务应同时清除文件");
        // 逻辑删除后按 id 查不到（MP @TableLogic 过滤）
        assertNull(exportTaskMapper.selectById(task.getId()));
    }

    @Test
    @DisplayName("跨租户隔离：其他租户任务在列表与下载中不可见")
    void crossTenantTaskInvisible() throws Exception {
        // 在另一测试租户 998 下创建并执行任务
        BaseContext.setCurrentTenantId(OTHER_TENANT_ID);
        ExportTask otherTask = exportTaskService.create(ExportTask.TYPE_ORDERS, ExportTask.FORMAT_EXCEL, Map.of());
        exportTaskService.execute(otherTask.getId());
        assertEquals(ExportTask.STATUS_SUCCESS, exportTaskMapper.selectById(otherTask.getId()).getStatus());

        // 切回测试租户 999：分页不可见
        BaseContext.setCurrentTenantId(TENANT_ID);
        assertTrue(exportTaskService.page(1, 50, null, null).getRecords().stream()
                .noneMatch(t -> t.getId().equals(otherTask.getId())));

        // 下载端点同样拒绝
        mockMvc.perform(get("/export/tasks/" + otherTask.getId() + "/file")
                        .sessionAttr("employee", EMPLOYEE_ID)
                        .sessionAttr("tenantId", TENANT_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ==================== 工具 ====================

    /** 轮询任务至终态（无 Awaitility 依赖，手写 300ms 步进轮询） */
    private ExportTask awaitTerminal(Long taskId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        ExportTask last = null;
        while (System.currentTimeMillis() < deadline) {
            last = exportTaskMapper.selectById(taskId);
            if (last != null && last.getStatus() != null
                    && (last.getStatus() == ExportTask.STATUS_SUCCESS || last.getStatus() == ExportTask.STATUS_FAILED)) {
                return last;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return last;
    }

    private Long extractTaskId(MvcResult result) throws java.io.IOException {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> map = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() { });
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) map.get("data");
        Object id = data == null ? null : data.get("id");
        return id == null ? null : Long.valueOf(String.valueOf(id));
    }
}
