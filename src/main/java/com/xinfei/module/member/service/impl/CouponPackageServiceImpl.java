package com.xinfei.module.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinfei.common.BaseContext;
import com.xinfei.common.CustomException;
import com.xinfei.common.utils.PageUtils;
import com.xinfei.module.member.mapper.CouponPackageItemMapper;
import com.xinfei.module.member.mapper.CouponPackageMapper;
import com.xinfei.module.member.mapper.CouponPackageOrderMapper;
import com.xinfei.module.member.model.CouponPackage;
import com.xinfei.module.member.model.CouponPackageItem;
import com.xinfei.module.member.model.CouponPackageOrder;
import com.xinfei.module.member.model.CouponTemplate;
import com.xinfei.module.member.model.Member;
import com.xinfei.module.member.service.CouponPackageService;
import com.xinfei.module.member.service.CouponTemplateService;
import com.xinfei.module.member.service.MemberService;
import com.xinfei.module.member.service.CouponUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 付费券包服务实现（O-18）
 *
 * @author xinfei
 * @since 2026-10-24
 */
@Slf4j
@Service
public class CouponPackageServiceImpl implements CouponPackageService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CouponPackageMapper packageMapper;
    private final CouponPackageItemMapper itemMapper;
    private final CouponPackageOrderMapper orderMapper;
    private final CouponTemplateService templateService;
    private final MemberService memberService;

    @Autowired
    private CouponUserService couponUserService;

    @Autowired
    public CouponPackageServiceImpl(CouponPackageMapper packageMapper, CouponPackageItemMapper itemMapper,
                                    CouponPackageOrderMapper orderMapper, CouponTemplateService templateService,
                                    MemberService memberService) {
        this.packageMapper = packageMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.templateService = templateService;
        this.memberService = memberService;
    }

    /**
     * 管理端分页。
     */
    @Override
    public Page<CouponPackage> adminPage(int page, int pageSize, String name, Integer status) {
        Page<CouponPackage> pageInfo = PageUtils.of(page, pageSize);
        LambdaQueryWrapper<CouponPackage> qw = new LambdaQueryWrapper<>();
        qw.like(name != null && !name.isEmpty(), CouponPackage::getName, name);
        qw.eq(status != null, CouponPackage::getStatus, status);
        qw.orderByAsc(CouponPackage::getSort).orderByDesc(CouponPackage::getId);
        packageMapper.selectPage(pageInfo, qw);
        fillItems(pageInfo.getRecords());
        return pageInfo;
    }

    /**
     * 新增/更新券包。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponPackage saveWithItems(CouponPackage pkg) {
        if (pkg.getPrice() == null || pkg.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException("券包售价必须大于 0");
        }
        List<CouponPackageItem> items = pkg.getItems();
        if (items == null || items.isEmpty()) {
            throw new CustomException("券包至少包含一张优惠券");
        }
        // 一期口径：包内每模板限一张（coupon_user.uk_member_template 约束，重复无法发券）
        Set<Long> templateIds = new HashSet<>();
        for (CouponPackageItem item : items) {
            if (item.getTemplateId() == null || !templateIds.add(item.getTemplateId())) {
                throw new CustomException("包内券模板不能重复（一期每模板限一张）");
            }
            CouponTemplate tpl = templateService.getById(item.getTemplateId());
            if (tpl == null) {
                throw new CustomException("券模板不存在：ID " + item.getTemplateId());
            }
        }

        if (pkg.getId() == null) {
            if (pkg.getStatus() == null) {
                pkg.setStatus(CouponPackage.STATUS_OFF);
            }
            packageMapper.insert(pkg);
        } else {
            CouponPackage existing = packageMapper.selectById(pkg.getId());
            if (existing == null) {
                throw new CustomException("券包不存在");
            }
            pkg.setStatus(existing.getStatus());
            packageMapper.updateById(pkg);
            // 明细全量替换
            itemMapper.delete(new LambdaQueryWrapper<CouponPackageItem>()
                    .eq(CouponPackageItem::getPackageId, pkg.getId()));
        }
        for (CouponPackageItem item : items) {
            item.setId(null);
            item.setPackageId(pkg.getId());
            itemMapper.insert(item);
        }
        return packageMapper.selectById(pkg.getId());
    }

    /**
     * 上架/下架。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, int status) {
        if (status != CouponPackage.STATUS_ON && status != CouponPackage.STATUS_OFF) {
            throw new CustomException("非法状态");
        }
        CouponPackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new CustomException("券包不存在");
        }
        pkg.setStatus(status);
        packageMapper.updateById(pkg);
    }

    /**
     * 删除（逻辑删）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (packageMapper.selectById(id) == null) {
            throw new CustomException("券包不存在");
        }
        packageMapper.deleteById(id);
    }

    /**
     * 顾客端上架券包列表。
     */
    @Override
    public List<CouponPackage> listPublished() {
        List<CouponPackage> list = packageMapper.selectList(new LambdaQueryWrapper<CouponPackage>()
                .eq(CouponPackage::getStatus, CouponPackage.STATUS_ON)
                .orderByAsc(CouponPackage::getSort).orderByDesc(CouponPackage::getId));
        fillItems(list);
        return list;
    }

    /**
     * 顾客创建购买单（幂等复用待支付单）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponPackageOrder createOrder(Long userId, Long packageId) {
        CouponPackage pkg = packageMapper.selectById(packageId);
        if (pkg == null || pkg.getStatus() == null || pkg.getStatus() != CouponPackage.STATUS_ON) {
            throw new CustomException("券包不存在或已下架");
        }
        // 幂等：同用户同包的待支付单直接复用（防连点多单）
        CouponPackageOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<CouponPackageOrder>()
                .eq(CouponPackageOrder::getUserId, userId)
                .eq(CouponPackageOrder::getPackageId, packageId)
                .eq(CouponPackageOrder::getStatus, CouponPackageOrder.STATUS_PENDING)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        CouponPackageOrder order = new CouponPackageOrder();
        order.setOrderNo("CP" + LocalDateTime.now().format(NO_FMT)
                + ThreadLocalRandom.current().nextInt(1000, 9999));
        order.setUserId(userId);
        order.setPackageId(packageId);
        order.setPackageName(pkg.getName());
        order.setPrice(pkg.getPrice());
        order.setStatus(CouponPackageOrder.STATUS_PENDING);
        orderMapper.insert(order);
        return order;
    }

    /**
     * 支付到账拆包发券（支付回调联动）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deliverPaid(Long purchaseOrderId) {
        CouponPackageOrder order = orderMapper.selectById(purchaseOrderId);
        if (order == null) {
            log.warn("[券包] 购买单不存在，跳过发券: purchaseOrderId={}", purchaseOrderId);
            return false;
        }
        if (order.getStatus() != null && order.getStatus() != CouponPackageOrder.STATUS_PENDING) {
            // 已支付（重复回调）幂等跳过；已关闭由调用方走退款兜底
            return order.getStatus() == CouponPackageOrder.STATUS_PAID;
        }
        // CAS 0→1：并发回调只有一方成功；发券失败抛异常整体回滚（支付单 CAS 由外层事务一并回滚后可重试）
        int updated = orderMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CouponPackageOrder>()
                        .eq(CouponPackageOrder::getId, purchaseOrderId)
                        .eq(CouponPackageOrder::getStatus, CouponPackageOrder.STATUS_PENDING)
                        .set(CouponPackageOrder::getStatus, CouponPackageOrder.STATUS_PAID)
                        .set(CouponPackageOrder::getPaidTime, LocalDateTime.now()));
        if (updated == 0) {
            return false;
        }

        // 购买者须有会员档案（未开通自动开通，与会员中心免费开通同语义）
        Long userId = order.getUserId();
        Member member = memberService.getByUserId(userId);
        if (member == null) {
            com.xinfei.module.user.model.User user = userService().getById(userId);
            member = memberService.registerForUser(userId,
                    user != null ? user.getPhone() : null,
                    user != null ? user.getName() : "券包顾客");
        }

        List<CouponPackageItem> items = itemMapper.selectList(new LambdaQueryWrapper<CouponPackageItem>()
                .eq(CouponPackageItem::getPackageId, order.getPackageId()));
        for (CouponPackageItem item : items) {
            boolean granted = templateService.claimCoupon(member.getId(), item.getTemplateId());
            if (!granted) {
                // 同模板已领过（uk 限制）或库存不足：整体回滚，避免"付了钱只发一部分"
                throw new CustomException("券发放失败（已领过同模板券或已领完），购买已回滚，请更换券包或联系商家");
            }
        }
        log.info("[券包] 购买单已发券: purchaseOrderId={}, packageId={}, userId={}, 券数={}",
                purchaseOrderId, order.getPackageId(), userId, items.size());
        return true;
    }

    /**
     * 我的购买记录。
     */
    @Override
    public List<CouponPackageOrder> myOrders(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<CouponPackageOrder>()
                .eq(CouponPackageOrder::getUserId, userId)
                .orderByDesc(CouponPackageOrder::getId));
    }

    // ==================== helpers ====================

    /** 批量回填明细 + 模板摘要 */
    private void fillItems(List<CouponPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        List<Long> ids = packages.stream().map(CouponPackage::getId).collect(Collectors.toList());
        List<CouponPackageItem> all = itemMapper.selectList(new LambdaQueryWrapper<CouponPackageItem>()
                .in(CouponPackageItem::getPackageId, ids));
        if (all.isEmpty()) {
            return;
        }
        Map<Long, CouponTemplate> tplMap = templateService.listByIds(
                all.stream().map(CouponPackageItem::getTemplateId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CouponTemplate::getId, t -> t));
        for (CouponPackageItem item : all) {
            CouponTemplate tpl = tplMap.get(item.getTemplateId());
            if (tpl != null) {
                item.setTemplateName(tpl.getName());
                item.setTemplateSummary(summarize(tpl));
            }
        }
        Map<Long, List<CouponPackageItem>> byPkg = all.stream()
                .collect(Collectors.groupingBy(CouponPackageItem::getPackageId));
        for (CouponPackage pkg : packages) {
            pkg.setItems(byPkg.get(pkg.getId()));
        }
    }

    /** 券摘要：满减=满X减Y；折扣=X折 */
    private String summarize(CouponTemplate tpl) {
        if ("DISCOUNT".equals(tpl.getType()) && tpl.getDiscountRate() != null) {
            return tpl.getDiscountRate() + "折";
        }
        String cond = tpl.getConditionAmount() != null ? "满" + tpl.getConditionAmount().stripTrailingZeros().toPlainString() : "无门槛";
        String off = tpl.getDiscountAmount() != null ? "减" + tpl.getDiscountAmount().stripTrailingZeros().toPlainString() : "";
        return cond + off;
    }

    private com.xinfei.module.user.service.UserService userService() {
        return userServiceBean;
    }

    @Autowired(required = false)
    private com.xinfei.module.user.service.UserService userServiceBean;
}
