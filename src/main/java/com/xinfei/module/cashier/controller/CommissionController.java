package com.xinfei.module.cashier.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.common.R;
import com.xinfei.common.RateLimit;
import com.xinfei.common.annotation.RequireEmployee;
import com.xinfei.module.cashier.model.CommissionRecord;
import com.xinfei.module.cashier.service.CommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 员工提成接口（收银提成口径，O-17）
 *
 * @author xinfei
 * @since 2026-10-24
 */
@RestController
@RequestMapping("/commission")
@Validated
@Tag(name = "员工提成", description = "收银提成的员工汇总与明细查询")
public class CommissionController {

    @Autowired
    private CommissionService commissionService;

    /**
     * 按员工聚合提成（时间范围筛选）
     *
     * @param beginTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 员工提成汇总（提成额降序）
     */
    @GetMapping("/summary")
    @RequireEmployee
    @RateLimit(maxRequestsPerSecond = 10)
    @Operation(summary = "员工提成汇总", description = "按收银员工聚合提成额/基数/单数，支持时间范围筛选")
    public R<List<Map<String, Object>>> summary(
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss（可选）") @RequestParam(required = false) String beginTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss（可选）") @RequestParam(required = false) String endTime) {
        return R.success(commissionService.summaryByEmployee(beginTime, endTime));
    }

    /**
     * 提成明细分页
     *
     * @return 分页明细
     */
    @GetMapping("/page")
    @RequireEmployee
    @RateLimit(maxRequestsPerSecond = 10)
    @Operation(summary = "提成明细分页", description = "按员工与期间筛选提成明细")
    public R<Page<CommissionRecord>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "员工ID（可选）") @RequestParam(required = false) Long employeeId,
            @Parameter(description = "期间 yyyy-MM（可选）") @RequestParam(required = false) String period) {
        return R.success(commissionService.page(page, pageSize, employeeId, period));
    }
}
