package com.xinfei.module.payment.channel;

import com.xinfei.module.payment.channel.notify.RefundNotifyResult;

/**
 * 退款查单能力（可选实现）：PROCESSING 退款的对账兜底主动查渠道终态。
 * <p>
 * 渠道退款受理可能同步返回 PROCESSING（钱在路上），终态本由退款异步通知确定；
 * 通知丢失时由 {@link com.xinfei.module.payment.task.RefundReconcileTask} 调本接口
 * 找回终态，结果直接复用退款回调链路（含 CAS 幂等与全额联动）。
 * </p>
 * <p>仅真实渠道实现；mock 渠道无真实资金，对账任务在 mock-mode 下整体跳过。</p>
 *
 * @author xinfei
 * @since 2026-10-16
 */
public interface RefundQueryCapable {

    /**
     * 按商户退款单号查询渠道退款终态。
     *
     * @param outRefundNo 商户退款单号（RefundRecord.refundNo）
     * @return 查询结果：success=true 时 status 为渠道终态
     *         （SUCCESS/CLOSED/ABNORMAL/PROCESSING）；查询失败 success=false
     */
    RefundNotifyResult queryRefundStatus(String outRefundNo);
}
