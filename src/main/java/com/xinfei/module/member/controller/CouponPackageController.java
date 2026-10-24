package com.xinfei.module.member.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.common.R;
import com.xinfei.common.RateLimit;
import com.xinfei.common.annotation.RequireEmployee;
import com.xinfei.module.member.model.CouponPackage;
import com.xinfei.module.member.service.CouponPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 付费券包管理端接口（O-18）：券包维护（一期口径：包内每模板限一张）。
 *
 * @author xinfei
 * @since 2026-10-24
 */
@RestController
@RequestMapping("/coupon-package")
@Validated
@Tag(name = "付费券包管理", description = "券包 CRUD 与上下架")
public class CouponPackageController {

    @Autowired
    private CouponPackageService couponPackageService;

    /**
     * 分页查询（含包内明细与模板摘要）。
     */
    @GetMapping("/page")
    @RequireEmployee
    @RateLimit(maxRequestsPerSecond = 10)
    @Operation(summary = "券包分页", description = "管理端分页查询券包，含包内券模板明细")
    public R<Page<CouponPackage>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "名称（可选）") @RequestParam(required = false) String name,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) Integer status) {
        return R.success(couponPackageService.adminPage(page, pageSize, name, status));
    }

    /**
     * 新增/更新券包（items 全量替换）。
     */
    @PostMapping
    @RequireEmployee
    @RateLimit(maxRequestsPerSecond = 10)
    @Operation(summary = "保存券包", description = "新增或更新券包（id 为空=新增）；包内每模板限一张")
    public R<CouponPackage> save(@Valid @RequestBody CouponPackage pkg) {
        return R.success(couponPackageService.saveWithItems(pkg));
    }

    /**
     * 上架/下架。
     */
    @PutMapping("/{id}/status/{status}")
    @RequireEmployee
    @RateLimit(maxRequestsPerSecond = 10)
    @Operation(summary = "上下架", description = "1=上架 0=下架；下架后顾客端不可再购买（存量待支付单仍可支付）")
    public R<String> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        couponPackageService.updateStatus(id, status);
        return R.success(status == 1 ? "已上架" : "已下架");
    }

    /**
     * 删除（逻辑删）。
     */
    @DeleteMapping("/{id}")
    @RequireEmployee
    @RateLimit(maxRequestsPerSecond = 10)
    @Operation(summary = "删除券包", description = "逻辑删除；有购买记录的包建议下架而非删除")
    public R<String> delete(@PathVariable Long id) {
        couponPackageService.delete(id);
        return R.success("已删除");
    }
}
