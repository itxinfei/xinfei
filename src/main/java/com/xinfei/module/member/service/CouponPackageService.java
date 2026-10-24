package com.xinfei.module.member.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.module.member.model.CouponPackage;
import com.xinfei.module.member.model.CouponPackageOrder;

import java.util.List;

/**
 * 付费券包服务（O-18）：券包维护、顾客购买单、支付到账拆包发券。
 *
 * @author xinfei
 * @since 2026-10-24
 */
public interface CouponPackageService {

    /**
     * 管理端分页（含包内明细与模板摘要回填）
     *
     * @return 分页券包
     */
    Page<CouponPackage> adminPage(int page, int pageSize, String name, Integer status);

    /**
     * 新增/更新券包（items 全量替换；一期口径：包内每模板限一张，重复模板拒绝）
     *
     * @param pkg 券包（id 为空=新增）
     * @return 保存后的券包
     */
    CouponPackage saveWithItems(CouponPackage pkg);

    /**
     * 上架/下架
     */
    void updateStatus(Long id, int status);

    /**
     * 删除（逻辑删）
     */
    void delete(Long id);

    /**
     * 顾客端上架券包列表（含明细摘要）
     */
    List<CouponPackage> listPublished();

    /**
     * 顾客创建购买单（幂等：同用户同包存在待支付单则复用返回）
     *
     * @return 待支付购买单
     */
    CouponPackageOrder createOrder(Long userId, Long packageId);

    /**
     * 支付到账拆包发券（支付回调联动，事务内）：
     * 购买单 CAS 0→1 后逐模板发券（claimCoupon 内核）；任一张失败整体回滚（支付单联动由调用方兜底）。
     *
     * @param purchaseOrderId 购买单ID（支付单 orderId）
     * @return true=发券成功；false=购买单已支付/不存在（幂等跳过）
     */
    boolean deliverPaid(Long purchaseOrderId);

    /**
     * 我的购买记录（倒序）
     */
    List<CouponPackageOrder> myOrders(Long userId);
}
