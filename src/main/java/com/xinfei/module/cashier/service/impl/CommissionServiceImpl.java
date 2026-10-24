package com.xinfei.module.cashier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.common.utils.PageUtils;
import com.xinfei.module.auth.model.Employee;
import com.xinfei.module.auth.service.EmployeeService;
import com.xinfei.module.cashier.mapper.CommissionRecordMapper;
import com.xinfei.module.cashier.model.CashierRecord;
import com.xinfei.module.cashier.model.CommissionRecord;
import com.xinfei.module.cashier.service.CommissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工提成服务实现（收银提成口径，O-17）
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Service
public class CommissionServiceImpl implements CommissionService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CommissionRecordMapper commissionRecordMapper;
    private final EmployeeService employeeService;

    @Autowired
    public CommissionServiceImpl(CommissionRecordMapper commissionRecordMapper, EmployeeService employeeService) {
        this.commissionRecordMapper = commissionRecordMapper;
        this.employeeService = employeeService;
    }

    /**
     * 收银成功后按比例快照落提成记录。
     */
    @Override
    public void recordForCashier(CashierRecord record) {
        if (record == null || record.getCashierId() == null || record.getId() == null) {
            return;
        }
        Employee employee = employeeService.getById(record.getCashierId());
        if (employee == null || employee.getCommissionRate() == null
                || employee.getCommissionRate().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        // 基数 = 实收 - 找零（现金多收部分不计提）；缺字段按 0 兜底
        BigDecimal actual = record.getActualAmount() == null ? BigDecimal.ZERO : record.getActualAmount();
        BigDecimal change = record.getChangeAmount() == null ? BigDecimal.ZERO : record.getChangeAmount();
        BigDecimal base = actual.subtract(change).max(BigDecimal.ZERO);
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            // 0 元单（全额券抵扣等）无提成基数，不落记录
            return;
        }
        BigDecimal rate = employee.getCommissionRate();
        BigDecimal commission = base.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        CommissionRecord cr = new CommissionRecord();
        cr.setEmployeeId(employee.getId());
        cr.setEmployeeName(record.getCashierName() != null ? record.getCashierName() : employee.getName());
        cr.setCashierRecordId(record.getId());
        cr.setOrderId(record.getOrderId());
        cr.setBaseAmount(base);
        cr.setRate(rate);
        cr.setCommissionAmount(commission);
        cr.setPeriod(record.getCashierTime() != null ? record.getCashierTime().format(PERIOD_FMT)
                : java.time.LocalDate.now().format(PERIOD_FMT));
        cr.setStatus(CommissionRecord.STATUS_PENDING);
        try {
            commissionRecordMapper.insert(cr);
            log.info("[提成] 收银提成已生成: employeeId={}, cashierRecordId={}, base={}, rate={}%, commission={}",
                    employee.getId(), record.getId(), base, rate, commission);
        } catch (DuplicateKeyException e) {
            // 幂等：同一收银记录重复触发（重试/并发）静默跳过
            log.info("[提成] 重复提成被唯一键拦截: cashierRecordId={}", record.getId());
        }
    }

    /**
     * 按员工聚合提成。
     */
    @Override
    public List<Map<String, Object>> summaryByEmployee(String beginTime, String endTime) {
        List<CommissionRecord> records = commissionRecordMapper.selectList(buildRangeQuery(beginTime, endTime));
        // LinkedHashMap 保持「提成额降序」的稳定聚合顺序
        Map<Long, Map<String, Object>> byEmployee = new LinkedHashMap<>();
        for (CommissionRecord cr : records) {
            Map<String, Object> row = byEmployee.computeIfAbsent(cr.getEmployeeId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("employeeId", cr.getEmployeeId());
                m.put("employeeName", cr.getEmployeeName());
                m.put("orderCount", 0);
                m.put("baseAmount", BigDecimal.ZERO);
                m.put("commissionAmount", BigDecimal.ZERO);
                return m;
            });
            row.put("orderCount", ((Integer) row.get("orderCount")) + 1);
            row.put("baseAmount", ((BigDecimal) row.get("baseAmount")).add(
                    cr.getBaseAmount() == null ? BigDecimal.ZERO : cr.getBaseAmount()));
            row.put("commissionAmount", ((BigDecimal) row.get("commissionAmount")).add(
                    cr.getCommissionAmount() == null ? BigDecimal.ZERO : cr.getCommissionAmount()));
        }
        List<Map<String, Object>> result = new ArrayList<>(byEmployee.values());
        result.sort((a, b) -> ((BigDecimal) b.get("commissionAmount")).compareTo(
                (BigDecimal) a.get("commissionAmount")));
        return result;
    }

    /**
     * 提成明细分页。
     */
    @Override
    public Page<CommissionRecord> page(int page, int pageSize, Long employeeId, String period) {
        Page<CommissionRecord> pageInfo = PageUtils.of(page, pageSize);
        LambdaQueryWrapper<CommissionRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(employeeId != null, CommissionRecord::getEmployeeId, employeeId);
        qw.eq(period != null && !period.isEmpty(), CommissionRecord::getPeriod, period);
        qw.orderByDesc(CommissionRecord::getCreateTime);
        return commissionRecordMapper.selectPage(pageInfo, qw);
    }

    private LambdaQueryWrapper<CommissionRecord> buildRangeQuery(String beginTime, String endTime) {
        LambdaQueryWrapper<CommissionRecord> qw = new LambdaQueryWrapper<>();
        qw.ge(beginTime != null && !beginTime.isEmpty(), CommissionRecord::getCreateTime, beginTime);
        qw.le(endTime != null && !endTime.isEmpty(), CommissionRecord::getCreateTime, endTime);
        return qw;
    }
}
