package com.xinfei.module.export.service;

import java.util.Map;

/**
 * 导出数据构建服务（O-25 抽取）
 * <p>原 ExportController 内联的「查询 + 组列 + 生成字节」逻辑统一抽取到本服务，
 * 同步导出端点（ExportController）与异步导出任务（ExportTaskService）共用同一实现，
 * 消除两套口径漂移的风险。</p>
 * <p>参数统一走原始字符串 Map（与 @RequestParam / params_json 同构），由实现负责解析与校验。</p>
 *
 * @author xinfei
 * @since 2026-10-24
 */
public interface ExportDataService {

    /**
     * 订单数据导出字节。
     * 参数：startDate / endDate / status / number / ids（均可选）
     */
    byte[] orders(String format, Map<String, String> params);

    /**
     * 菜品数据导出字节。
     * 参数：categoryId / name / code / status / ids（均可选）
     */
    byte[] dishes(String format, Map<String, String> params);

    /**
     * 员工数据导出字节。
     * 参数：name / status / ids（均可选）
     */
    byte[] employees(String format, Map<String, String> params);

    /**
     * C端用户数据导出字节。
     * 参数：name / phone / status / ids（均可选）
     */
    byte[] users(String format, Map<String, String> params);

    /**
     * 经营报表导出字节（委托 reportService.exportDailyReport）。
     * 参数：startDate / endDate（必填）
     */
    byte[] report(String format, Map<String, String> params);
}
