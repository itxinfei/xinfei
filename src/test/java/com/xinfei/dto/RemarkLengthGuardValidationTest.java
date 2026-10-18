package com.xinfei.dto;

import com.xinfei.module.ai.model.AiProviderConfig;
import com.xinfei.module.cashier.model.CashierRecord;
import com.xinfei.module.inventory.model.PurchaseOrder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>
 * 锁定 §10.4 续扫收口的备注长度守卫（2026-10-18，用户批准同 §4.1 族补齐）。
 * </p>
 *
 * <p>覆盖 DTO/实体侧的 {@code @Size} 注解：采购单创建（CreatePurchaseOrderDTO 同包）与更新
 * （PurchaseOrder 实体，PUT 直收实体 + @Validated）、盘点单创建/明细（CreateStockCheckDTO /
 * StockCheckItemDTO 同包）、收银记录（CashierRecord，POST /record 走 @Valid；两个
 * @RequestParam 端点由 CashierServiceImpl 统一构建口守卫）、AI 供应商配置（AiProviderConfig，
 * /add /update 直收实体 + @Validated）。本测试直连 Validator，不起 Spring、不连库。</p>
 *
 * @author 心飞为你飞
 */
class RemarkLengthGuardValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) {
            factory.close();
        }
    }

    private static String repeat(char ch, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    private Set<String> violations(Object target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private void assertRejects(Object target, String message) {
        assertTrue(violations(target).contains(message), violations(target).toString());
    }

    private void assertAccepts(Object target) {
        assertTrue(violations(target).isEmpty(), violations(target).toString());
    }

    @Test
    @DisplayName("采购单创建 DTO：备注 201 字拦下、200 字放行")
    void createPurchaseOrderRemarkGuard() {
        CreatePurchaseOrderDTO dto = new CreatePurchaseOrderDTO();
        dto.setSupplierId(1L);
        dto.setOperator("张三");
        dto.setRemark(repeat('备', 201));
        assertRejects(dto, "备注不能超过200个字符");
        dto.setRemark(repeat('备', 200));
        assertAccepts(dto);
    }

    @Test
    @DisplayName("采购单实体（PUT 更新链）：备注 201 字拦下")
    void purchaseOrderEntityRemarkGuard() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(1L);
        order.setRemark(repeat('备', 201));
        assertRejects(order, "备注不能超过200个字符");
    }

    @Test
    @DisplayName("盘点单创建 DTO：备注 201 字拦下")
    void createStockCheckRemarkGuard() {
        CreateStockCheckDTO dto = new CreateStockCheckDTO();
        dto.setOperator("张三");
        dto.setRemark(repeat('盘', 201));
        assertRejects(dto, "备注不能超过200个字符");
    }

    @Test
    @DisplayName("盘点明细项 DTO：备注 201 字拦下（潜伏面锁死）")
    void stockCheckItemRemarkGuard() {
        StockCheckItemDTO item = new StockCheckItemDTO();
        item.setMaterialId(1L);
        item.setRemark(repeat('盘', 201));
        assertRejects(item, "备注不能超过200个字符");
    }

    @Test
    @DisplayName("收银记录实体（POST /record 链）：备注 201 字拦下")
    void cashierRecordRemarkGuard() {
        CashierRecord record = new CashierRecord();
        record.setRemark(repeat('收', 201));
        assertRejects(record, "备注不能超过200个字符");
    }

    @Test
    @DisplayName("AI 供应商配置实体：备注 501 字拦下、500 字放行")
    void aiProviderConfigRemarkGuard() {
        AiProviderConfig config = new AiProviderConfig();
        config.setRemark(repeat('模', 501));
        assertRejects(config, "备注不能超过500个字符");
        config.setRemark(repeat('模', 500));
        assertTrue(violations(config).isEmpty(), violations(config).toString());
    }
}
