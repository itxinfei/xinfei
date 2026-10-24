package com.xinfei.module.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinfei.common.BaseContext;
import com.xinfei.common.CustomException;
import com.xinfei.enums.OrderStatus;
import com.xinfei.module.auth.model.Employee;
import com.xinfei.module.auth.service.EmployeeService;
import com.xinfei.module.category.model.Category;
import com.xinfei.module.category.service.CategoryService;
import com.xinfei.module.dish.model.Dish;
import com.xinfei.module.dish.service.DishService;
import com.xinfei.module.export.util.ExportUtil;
import com.xinfei.module.order.model.Orders;
import com.xinfei.module.order.service.OrderService;
import com.xinfei.module.report.service.ReportService;
import com.xinfei.module.user.model.User;
import com.xinfei.module.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出数据构建服务实现（O-25：逻辑自 ExportController 原样迁移，行为不变）
 * <p>同步端点与异步任务共用；查询一律 fail-closed 强制租户过滤 + LIMIT 防全量加载。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Service
public class ExportDataServiceImpl implements com.xinfei.module.export.service.ExportDataService {

    /** 勾选导出单次 ID 数量上限（GET URL 长度与内存边界） */
    private static final int MAX_EXPORT_IDS = 1000;

    @Resource
    private OrderService orderService;

    @Resource
    private DishService dishService;

    @Resource
    private CategoryService categoryService;

    @Resource
    private EmployeeService employeeService;

    @Resource
    private UserService userService;

    @Resource
    private ReportService reportService;

    // ==================== 导出类型入口 ====================

    @Override
    public byte[] orders(String format, Map<String, String> params) {
        LocalDate start = parseDate(params.get("startDate"));
        LocalDate end = parseDate(params.get("endDate"));
        Integer status = parseInt(params.get("status"));
        String number = params.get("number");
        List<Orders> orders = queryOrders(start, end, status, number, params.get("ids"));

        if (com.xinfei.module.export.model.ExportTask.FORMAT_PDF.equals(format)) {
            LinkedHashMap<String, String> columns = new LinkedHashMap<>();
            columns.put("number", "订单号");
            columns.put("userName", "用户名");
            columns.put("phone", "手机号");
            columns.put("amount", "实收金额");
            columns.put("status", "订单状态");
            columns.put("orderTime", "下单时间");

            List<Map<String, Object>> dataList = buildOrderDataList(orders, false);
            double totalAmount = orders.stream()
                    .filter(o -> o.getAmount() != null)
                    .mapToDouble(o -> o.getAmount().doubleValue())
                    .sum();

            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("订单总数", String.valueOf(orders.size()));
            summary.put("总金额", "¥" + String.format("%.2f", totalAmount));
            summary.put("日期范围", (start != null ? start.toString() : "不限")
                    + " ~ " + (end != null ? end.toString() : "不限"));

            return ExportUtil.generatePdfBytes("心飞外卖 - 订单数据报表", columns, dataList, summary);
        }
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        columns.put("number", "订单号");
        columns.put("userName", "用户名");
        columns.put("phone", "手机号");
        columns.put("address", "地址");
        columns.put("amount", "实收金额");
        columns.put("status", "订单状态");
        columns.put("payMethod", "支付方式");
        columns.put("orderTime", "下单时间");

        List<Map<String, Object>> dataList = buildOrderDataList(orders, true);
        return ExportUtil.generateExcelBytes(columns, dataList);
    }

    @Override
    public byte[] dishes(String format, Map<String, String> params) {
        Long categoryId = parseLong(params.get("categoryId"));
        String name = params.get("name");
        String code = params.get("code");
        Integer status = parseInt(params.get("status"));
        List<Map<String, Object>> dataList = buildDishDataList(categoryId, name, code, status, params.get("ids"));

        if (com.xinfei.module.export.model.ExportTask.FORMAT_PDF.equals(format)) {
            LinkedHashMap<String, String> columns = new LinkedHashMap<>();
            columns.put("name", "菜品名称");
            columns.put("categoryName", "分类");
            columns.put("price", "价格");
            columns.put("status", "状态");
            columns.put("createTime", "创建时间");

            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("菜品总数", String.valueOf(dataList.size()));

            return ExportUtil.generatePdfBytes("心飞外卖 - 菜品数据报表", columns, dataList, summary);
        }
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        columns.put("name", "菜品名称");
        columns.put("categoryName", "分类");
        columns.put("price", "价格");
        columns.put("status", "状态");
        columns.put("description", "描述");
        columns.put("createTime", "创建时间");
        return ExportUtil.generateExcelBytes(columns, dataList);
    }

    @Override
    public byte[] employees(String format, Map<String, String> params) {
        String name = params.get("name");
        Integer status = parseInt(params.get("status"));
        List<Employee> employees = queryEmployees(name, status, params.get("ids"));

        if (com.xinfei.module.export.model.ExportTask.FORMAT_PDF.equals(format)) {
            LinkedHashMap<String, String> columns = new LinkedHashMap<>();
            columns.put("name", "姓名");
            columns.put("username", "账号");
            columns.put("phone", "手机号");
            columns.put("sex", "性别");
            columns.put("status", "状态");

            List<Map<String, Object>> dataList = buildEmployeeDataList(employees);
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("员工总数", String.valueOf(employees.size()));

            return ExportUtil.generatePdfBytes("心飞外卖 - 员工数据报表", columns, dataList, summary);
        }
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        columns.put("name", "姓名");
        columns.put("username", "账号");
        columns.put("phone", "手机号");
        columns.put("sex", "性别");
        columns.put("status", "状态");
        columns.put("createTime", "入职时间");

        List<Map<String, Object>> dataList = buildEmployeeDataList(employees);
        return ExportUtil.generateExcelBytes(columns, dataList);
    }

    @Override
    public byte[] users(String format, Map<String, String> params) {
        String name = params.get("name");
        String phone = params.get("phone");
        Integer status = parseInt(params.get("status"));
        List<User> users = queryUsers(name, phone, status, params.get("ids"));

        if (com.xinfei.module.export.model.ExportTask.FORMAT_PDF.equals(format)) {
            LinkedHashMap<String, String> columns = new LinkedHashMap<>();
            columns.put("name", "姓名");
            columns.put("phone", "手机号");
            columns.put("sex", "性别");
            columns.put("status", "状态");
            columns.put("createTime", "注册时间");

            List<Map<String, Object>> dataList = buildUserDataList(users);
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("用户总数", String.valueOf(dataList.size()));

            return ExportUtil.generatePdfBytes("心飞外卖 - 用户数据报表", columns, dataList, summary);
        }
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        columns.put("name", "姓名");
        columns.put("phone", "手机号");
        columns.put("sex", "性别");
        columns.put("idNumber", "身份证号");
        columns.put("status", "状态");
        columns.put("createTime", "注册时间");

        List<Map<String, Object>> dataList = buildUserDataList(users);
        return ExportUtil.generateExcelBytes(columns, dataList);
    }

    @Override
    public byte[] report(String format, Map<String, String> params) {
        String startDate = params.get("startDate");
        String endDate = params.get("endDate");
        if (!hasText(startDate) || !hasText(endDate)) {
            throw new CustomException("经营报表导出必须指定开始与结束日期");
        }
        Long tenantId = BaseContext.getCurrentTenantId();
        return reportService.exportDailyReport(startDate, endDate, tenantId, format);
    }

    // ==================== 数据查询方法（自 ExportController 迁移） ====================

    /**
     * 查询订单列表
     * ids 非空时仅按勾选 ID 导出（仍强制租户过滤）；否则按日期/订单号/状态筛选
     */
    private List<Orders> queryOrders(LocalDate startDate, LocalDate endDate, Integer status,
                                     String number, String idsParam) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        // #11 fail-closed：强制租户过滤，无租户上下文拒绝导出
        Long tenantId = BaseContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new CustomException("无导出权限，租户上下文缺失");
        }
        wrapper.eq(Orders::getTenantId, tenantId);
        List<Long> ids = parseIds(idsParam);
        if (!ids.isEmpty()) {
            wrapper.in(Orders::getId, ids);
        } else {
            if (startDate != null) {
                wrapper.ge(Orders::getOrderTime, LocalDateTime.of(startDate, LocalTime.MIN));
            }
            if (endDate != null) {
                wrapper.le(Orders::getOrderTime, LocalDateTime.of(endDate, LocalTime.MAX));
            }
            if (status != null) {
                wrapper.eq(Orders::getStatus, status);
            }
            if (hasText(number)) {
                wrapper.like(Orders::getNumber, number.trim());
            }
            // #12 限制最大导出行数，防止全量加载 OOM
            wrapper.last("LIMIT 100000");
        }
        wrapper.orderByDesc(Orders::getOrderTime);
        return orderService.list(wrapper);
    }

    /**
     * 查询菜品列表
     * ids 非空时仅按勾选 ID 导出；否则按分类/名称/商品码/状态筛选
     */
    private List<Dish> queryDishes(Long categoryId, String name, String code, Integer status, String idsParam) {
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<>();
        // #11 fail-closed：强制租户过滤
        Long tenantId = BaseContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new CustomException("无导出权限，租户上下文缺失");
        }
        wrapper.eq(Dish::getTenantId, tenantId);
        List<Long> ids = parseIds(idsParam);
        if (!ids.isEmpty()) {
            wrapper.in(Dish::getId, ids);
        } else {
            wrapper.eq(Dish::getIsDeleted, 0);
            if (categoryId != null) {
                wrapper.eq(Dish::getCategoryId, categoryId);
            }
            if (hasText(name)) {
                wrapper.like(Dish::getName, name.trim());
            }
            if (hasText(code)) {
                wrapper.like(Dish::getCode, code.trim());
            }
            if (status != null) {
                wrapper.eq(Dish::getStatus, status);
            }
            // #12 限制最大导出行数，防止全量加载 OOM
            wrapper.last("LIMIT 100000");
        }
        wrapper.orderByDesc(Dish::getCreateTime);
        return dishService.list(wrapper);
    }

    /**
     * 查询员工列表
     * ids 非空时仅按勾选 ID 导出；否则按姓名/状态筛选
     */
    private List<Employee> queryEmployees(String name, Integer status, String idsParam) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        addTenantFilter(wrapper);
        List<Long> ids = parseIds(idsParam);
        if (!ids.isEmpty()) {
            wrapper.in(Employee::getId, ids);
        } else {
            if (hasText(name)) {
                wrapper.like(Employee::getName, name.trim());
            }
            if (status != null) {
                wrapper.eq(Employee::getStatus, status);
            }
            // #12 限制最大导出行数，防止全量加载 OOM
            wrapper.last("LIMIT 100000");
        }
        wrapper.orderByDesc(Employee::getCreateTime);
        return employeeService.list(wrapper);
    }

    /**
     * 查询C端用户列表
     * employee表在MybatisPlusConfig忽略列表中，必须手动隔离；用户表同样按租户隔离
     * #11 fail-closed：无租户上下文直接抛异常拒绝导出
     * ids 非空时仅按勾选 ID 导出；否则按姓名/手机号/状态筛选
     */
    private List<User> queryUsers(String name, String phone, Integer status, String idsParam) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = BaseContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new CustomException("无导出权限，租户上下文缺失");
        }
        wrapper.eq(User::getTenantId, tenantId);
        List<Long> ids = parseIds(idsParam);
        if (!ids.isEmpty()) {
            wrapper.in(User::getId, ids);
        } else {
            if (hasText(name)) {
                wrapper.like(User::getName, name.trim());
            }
            if (hasText(phone)) {
                wrapper.like(User::getPhone, phone.trim());
            }
            if (status != null) {
                wrapper.eq(User::getStatus, status);
            }
            // #12 限制最大行数，防止全量加载 OOM
            wrapper.last("LIMIT 100000");
        }
        wrapper.orderByDesc(User::getCreateTime);
        return userService.list(wrapper);
    }

    /**
     * 为employee查询手动添加tenant_id过滤
     * employee表在MybatisPlusConfig忽略列表中，必须手动隔离
     * #11 fail-closed：无租户上下文直接抛异常拒绝导出
     */
    private void addTenantFilter(LambdaQueryWrapper<Employee> wrapper) {
        Long tenantId = BaseContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new CustomException("无导出权限，租户上下文缺失");
        }
        wrapper.eq(Employee::getTenantId, tenantId);
    }

    // ==================== 数据构建方法（自 ExportController 迁移） ====================

    /**
     * 构建订单数据列表
     * Excel/PDF共用此方法，通过includeFull参数控制列数
     */
    private List<Map<String, Object>> buildOrderDataList(List<Orders> orders, boolean includeFull) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Orders order : orders) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("number", order.getNumber());
            row.put("userName", order.getUserName());
            row.put("phone", order.getPhone());
            if (includeFull) {
                row.put("address", order.getAddress());
            }
            row.put("amount", (order.getAmount() != null ? order.getAmount() : 0) + "元");
            row.put("status", getOrderStatusName(order.getStatus()));
            if (includeFull) {
                row.put("payMethod", getPayMethodName(order.getPayMethod()));
            }
            row.put("orderTime", order.getOrderTime());
            dataList.add(row);
        }
        return dataList;
    }

    /**
     * 支付方式文案（与 /api/meta/enums payMethod 字典一致）：
     * 1=现金，2=微信支付，3=支付宝，4=银行卡，5=会员储值，6=货到付款
     */
    private String getPayMethodName(Integer payMethod) {
        if (payMethod == null) return "";
        switch (payMethod) {
            case 1: return "现金";
            case 2: return "微信支付";
            case 3: return "支付宝";
            case 4: return "银行卡";
            case 5: return "会员储值";
            case 6: return "货到付款";
            default: return "";
        }
    }

    /**
     * 构建菜品数据列表
     * Excel/PDF共用，消除重复代码
     */
    private List<Map<String, Object>> buildDishDataList(Long categoryId, String name, String code,
                                                        Integer status, String idsParam) {
        List<Dish> dishes = queryDishes(categoryId, name, code, status, idsParam);

        Map<Long, String> categoryMap = new HashMap<>();
        LambdaQueryWrapper<Category> categoryWrapper = new LambdaQueryWrapper<>();
        // #11 fail-closed：强制租户过滤
        Long tenantId = BaseContext.getCurrentTenantId();
        if (tenantId == null) {
            throw new CustomException("无导出权限，租户上下文缺失");
        }
        categoryWrapper.eq(Category::getTenantId, tenantId);
        categoryWrapper.orderByAsc(Category::getSort);
        // #12 限制最大行数，防止全量加载
        categoryWrapper.last("LIMIT 100000");
        for (Category c : categoryService.list(categoryWrapper)) {
            categoryMap.put(c.getId(), c.getName());
        }

        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Dish dish : dishes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", dish.getName());
            row.put("categoryName", categoryMap.getOrDefault(dish.getCategoryId(), "未知"));
            row.put("price", (dish.getPrice() != null ? dish.getPrice() : 0) + "元");
            row.put("status", dish.getStatus() == 1 ? "在售" : "停售");
            row.put("description", dish.getDescription() != null ? dish.getDescription() : "");
            row.put("createTime", dish.getCreateTime());
            dataList.add(row);
        }
        return dataList;
    }

    /**
     * 构建员工数据列表
     */
    private List<Map<String, Object>> buildEmployeeDataList(List<Employee> employees) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Employee emp : employees) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", emp.getName());
            row.put("username", emp.getUsername());
            row.put("phone", emp.getPhone() != null ? emp.getPhone() : "");
            row.put("sex", "1".equals(emp.getSex()) ? "男" : ("2".equals(emp.getSex()) ? "女" : ""));
            row.put("status", emp.getStatus() == 1 ? "正常" : "禁用");
            row.put("createTime", emp.getCreateTime());
            dataList.add(row);
        }
        return dataList;
    }

    /**
     * 构建C端用户数据列表
     */
    private List<Map<String, Object>> buildUserDataList(List<User> users) {
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", u.getName());
            row.put("phone", u.getPhone() != null ? u.getPhone() : "");
            row.put("sex", "1".equals(u.getSex()) ? "男" : ("0".equals(u.getSex()) ? "女" : ""));
            row.put("idNumber", u.getIdNumber() != null ? u.getIdNumber() : "");
            row.put("status", u.getStatus() != null && u.getStatus() == 1 ? "正常" : "禁用");
            row.put("createTime", u.getCreateTime());
            dataList.add(row);
        }
        return dataList;
    }

    /**
     * 订单状态中文名（委托给 {@link OrderStatus} 枚举）
     */
    private String getOrderStatusName(Integer status) {
        if (status == null) return "未知";
        OrderStatus orderStatus = OrderStatus.fromCode(status);
        return orderStatus != null ? orderStatus.getDesc() : "其他";
    }

    // ==================== 参数解析工具（自 ExportController 迁移） ====================

    /**
     * 解析勾选导出的 ID 串（逗号分隔），空白忽略；非法值或超量抛业务异常
     */
    private List<Long> parseIds(String idsParam) {
        List<Long> ids = new ArrayList<>();
        if (!hasText(idsParam)) {
            return ids;
        }
        String[] parts = idsParam.trim().split(",");
        for (String part : parts) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(s));
            } catch (NumberFormatException e) {
                throw new CustomException("导出参数有误：非法的记录ID");
            }
        }
        if (ids.size() > MAX_EXPORT_IDS) {
            throw new CustomException("单次最多勾选导出" + MAX_EXPORT_IDS + "条，请缩小范围");
        }
        return ids;
    }

    /**
     * 宽松解析导出日期参数：兼容 yyyy-MM-dd 与前端误传的 yyyy-MM-dd HH:mm:ss（取前10位）
     */
    private LocalDate parseDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        String s = value.trim();
        if (s.length() > 10) {
            s = s.substring(0, 10);
        }
        try {
            return LocalDate.parse(s);
        } catch (java.time.format.DateTimeParseException e) {
            throw new CustomException("日期格式有误，请使用 yyyy-MM-dd");
        }
    }

    private Integer parseInt(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new CustomException("导出参数有误：状态需为数字");
        }
    }

    private Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new CustomException("导出参数有误：分类ID需为数字");
        }
    }

    /** 字符串非空白判断 */
    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
