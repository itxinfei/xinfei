package com.xinfei.module.inventory.controller;

import com.xinfei.common.R;
import com.xinfei.common.RateLimit;
import com.xinfei.common.annotation.RequireEmployee;
import com.xinfei.module.inventory.task.InventoryAlertTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 库存预警推送 Controller（O-26）。
 * <p>定时差分扫描（默认 5 分钟）为主，本控制器提供手动触发口：
 * 运维排查「为什么没推」与前端联调时立即执行一轮当前租户的差分扫描。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory/alert")
@Tag(name = "库存预警推送", description = "库存低于安全库存的实时推送（员工SSE通道）与手动扫描触发")
@RequireEmployee
public class InventoryAlertController {

    @Resource
    private InventoryAlertTask inventoryAlertTask;

    /**
     * 立即执行一轮当前租户的预警差分扫描（与定时任务同一套状态键与推送链路）。
     */
    @PostMapping("/sweep")
    @RateLimit(maxRequestsPerSecond = 2)
    @Operation(summary = "手动触发预警扫描", description = "立即执行当前租户的库存预警差分扫描，状态变迁时经员工SSE通道推送")
    public R<String> sweepNow() {
        inventoryAlertTask.sweepCurrentTenant();
        return R.success("库存预警扫描已完成，状态变迁将实时推送");
    }
}
