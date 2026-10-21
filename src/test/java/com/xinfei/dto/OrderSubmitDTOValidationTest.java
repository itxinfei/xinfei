package com.xinfei.dto;

import com.xinfei.module.order.dto.OrderSubmitDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>
 * 锁定外卖下单请求体的边界校验（§11-7 拍板①，2026-10-18）。
 * </p>
 *
 * <p>{@code orders.remark} 为 varchar(100)、{@code orders.phone} varchar(255)，
 * {@code OrderSubmitDTO} 此前只有 @Schema 无长度注解；C 端 add-order.html 备注框
 * 已夹 maxlength=50，正常 UI 路径不可能超长，风险面是绕过页面直连 API——
 * STRICT_TRANS_TABLES 下超长即 1406 → 下单事务整体回滚。
 * {@code OrderController.submit} 已是 @Valid @RequestBody，注解即生效。
 * 本测试直连 Validator，不起 Spring、不连库。</p>
 *
 * @author 心飞为你飞
 */
class OrderSubmitDTOValidationTest {

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

    private OrderSubmitDTO validDto() {
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setAddressBookId(1L);
        dto.setSource("TAKEOUT");
        dto.setRemark("不要辣");
        dto.setPhone("13800138000");
        dto.setPayMethod(2);
        return dto;
    }

    private Set<String> violations(OrderSubmitDTO dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("合法请求不产生校验违例")
    void validRequestHasNoViolation() {
        assertEquals(Collections.emptySet(), violations(validDto()));
    }

    @Test
    @DisplayName("备注 101 字被拦下（此前直连 API 可穿透到库层触发 1406）")
    void overLongRemarkIsRejected() {
        OrderSubmitDTO dto = validDto();
        dto.setRemark(repeat('原', 101));
        assertTrue(violations(dto).contains("订单备注不能超过100个字符"), violations(dto).toString());
    }

    @Test
    @DisplayName("备注恰好 100 字放行")
    void exactLimitRemarkIsAccepted() {
        OrderSubmitDTO dto = validDto();
        dto.setRemark(repeat('原', 100));
        assertEquals(Collections.emptySet(), violations(dto));
    }

    @Test
    @DisplayName("电话 256 字被拦下")
    void overLongPhoneIsRejected() {
        OrderSubmitDTO dto = validDto();
        dto.setPhone(repeat('1', 256));
        assertTrue(violations(dto).contains("联系电话不能超过255个字符"), violations(dto).toString());
    }
}
