package com.xinfei.dto;

import com.xinfei.module.order.model.OrderDetail;
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
 * 锁定堂食扫码下单请求体的边界校验。
 * </p>
 *
 * <p>{@code orders.remark} 为 varchar(100)、{@code user_name} varchar(50)、{@code table_name}
 * varchar(32)，而 {@code EatInOrderRequest.OrderInfo} 此前只有 {@code @NotNull(tableId)} 且外层
 * {@code order} 字段缺 {@code @Valid}——嵌套约束不级联，超长备注一路穿透到 MyBatis，在 UPDATE/INSERT
 * 时抛 MySQL 1406 Data too long，整个 {@code @Transactional} 下单回滚，顾客端只看到"下单失败"。
 * 本测试直连 Validator，不起 Spring、不连库。</p>
 *
 * @author 心飞为你飞
 */
class EatInOrderRequestValidationTest {

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

    private EatInOrderRequest validRequest() {
        EatInOrderRequest request = new EatInOrderRequest();
        EatInOrderRequest.OrderInfo info = new EatInOrderRequest.OrderInfo();
        info.setTableId(1L);
        info.setTableName("A01");
        info.setUserName("张三");
        info.setPhone("13800138000");
        info.setRemark("少放辣");
        info.setPayMethod(2);
        info.setCustomerCount(4);
        request.setOrder(info);
        OrderDetail detail = new OrderDetail();
        detail.setDishId(1L);
        detail.setName("宫保鸡丁");
        detail.setNumber(1);
        request.setOrderDetails(Collections.singletonList(detail));
        return request;
    }

    private Set<String> violations(EatInOrderRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("合法请求不产生校验违例")
    void validRequestHasNoViolation() {
        assertEquals(Collections.emptySet(), violations(validRequest()));
    }

    @Test
    @DisplayName("备注 101 字被拦下（此前穿透到库层触发 1406）")
    void overLongRemarkIsRejected() {
        EatInOrderRequest request = validRequest();
        request.getOrder().setRemark(repeat('原', 101));
        assertTrue(violations(request).contains("备注不能超过100个字符"), violations(request).toString());
    }

    @Test
    @DisplayName("联系人姓名 51 字被拦下")
    void overLongUserNameIsRejected() {
        EatInOrderRequest request = validRequest();
        request.getOrder().setUserName(repeat('张', 51));
        assertTrue(violations(request).contains("联系人姓名不能超过50个字符"), violations(request).toString());
    }

    @Test
    @DisplayName("嵌套 order 已级联：tableId 为空即报错")
    void nestedOrderIsCascaded() {
        EatInOrderRequest request = validRequest();
        request.getOrder().setTableId(null);
        assertTrue(violations(request).contains("桌台ID不能为空"), violations(request).toString());
    }
}
