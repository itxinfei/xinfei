package com.xinfei.module.cashier.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.module.cashier.model.CashierRecord;
import com.xinfei.module.cashier.model.CommissionRecord;

import java.util.List;
import java.util.Map;

/**
 * 员工提成服务（收银提成口径，O-17）
 *
 * @author xinfei
 * @since 2026-10-24
 */
public interface CommissionService {

    /**
     * 收银成功后按收银员比例落提成记录（幂等：uk_commission_cashier，重复调用静默跳过）
     * <p>基数 = 实收 - 找零；比例取生成时刻 employee.commission_rate 快照；
     * 比例为 NULL/0 的员工不落记录。失败不阻断收银主流程（调用方宽异常兜底）。</p>
     *
     * @param record 收银记录（已落库，含 id/cashierId/actualAmount/changeAmount/orderId）
     */
    void recordForCashier(CashierRecord record);

    /**
     * 按员工聚合提成（时间范围筛选）
     *
     * @param beginTime 开始时间（yyyy-MM-dd HH:mm:ss，可选）
     * @param endTime   结束时间（可选）
     * @return 每员工 {employeeId, employeeName, orderCount, baseAmount, commissionAmount}
     */
    List<Map<String, Object>> summaryByEmployee(String beginTime, String endTime);

    /**
     * 提成明细分页
     *
     * @param page       页码
     * @param pageSize   每页（封顶 100）
     * @param employeeId 员工ID（可选）
     * @param period     期间 yyyy-MM（可选）
     * @return 分页明细
     */
    Page<CommissionRecord> page(int page, int pageSize, Long employeeId, String period);
}
