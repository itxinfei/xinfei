package com.xinfei.module.subsidy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinfei.common.BaseContext;
import com.xinfei.module.subsidy.dto.SubsidyGrantDTO;
import com.xinfei.module.subsidy.model.MealSubsidyAccount;
import com.xinfei.module.subsidy.model.MealSubsidyRecord;
import com.xinfei.module.subsidy.service.SubsidyService;
import com.xinfei.module.user.model.User;
import com.xinfei.module.user.mapper.UserMapper;
import com.xinfei.test.TestDatabaseCleaner;
import com.xinfei.test.TestIdGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 餐补资金操作事务原子性回归测试。
 * <p>
 * 发放/核销/回充都是「账户余额原子 UPDATE」+「流水 INSERT」两步。历史上二者不在同一事务，
 * 中途失败会留下「钱动了、流水没落」的账实不符。此测试用 {@code @MockBean} 让流水 INSERT
 * 在真实 UPDATE 之后抛异常，验证 {@code @Transactional} 使整体回滚——余额/账户回到操作前状态，
 * 绝不出现无流水的余额变动。
 * </p>
 */
@SpringBootTest(classes = com.xinfei.XinfeiApplication.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = {"classpath:schema.sql", "classpath:schema-subsidy.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class SubsidyTransactionAtomicityTest {

    private static final long USER_ID = 990311L;
    private static final long TENANT_ID = 999L;
    private static final long ORDER_ID = 990311L;

    @Autowired
    private SubsidyService subsidyService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TestDatabaseCleaner cleaner;

    @Autowired
    private TestIdGuard testIdGuard;

    /** 只 mock 流水 Mapper：让流水 INSERT 在真实余额 UPDATE 之后失败，观察余额是否随之回滚。 */
    @MockBean
    private com.xinfei.module.subsidy.mapper.MealSubsidyRecordMapper recordMapper;

    @BeforeEach
    void setUp() {
        cleaner.cleanTables("meal_subsidy_account", "meal_subsidy_record", "user");
        testIdGuard.assertAbsent("user", USER_ID);
        BaseContext.setCurrentId(USER_ID);
        BaseContext.setCurrentTenantId(TENANT_ID);
        insertUser();
    }

    @Test
    @DisplayName("发放 - 流水落库失败时账户创建与入账整体回滚")
    void grant_rollsBackWhenLedgerFails() {
        when(recordMapper.insert(any(MealSubsidyRecord.class)))
                .thenThrow(new RuntimeException("模拟流水落库失败"));

        assertThrows(RuntimeException.class,
                () -> subsidyService.grant(grantReq(new BigDecimal("100")), TENANT_ID, 1L));

        assertNull(accountOf(USER_ID),
                "流水写入失败应回滚账户创建与入账，不得出现有钱无流水的账户");
    }

    @Test
    @DisplayName("核销 - 流水落库失败时扣款整体回滚（余额不被扣走）")
    void consume_rollsBackWhenLedgerFails() {
        when(recordMapper.selectCount(any())).thenReturn(0L);
        // 真实发放 100（此处 insert 未打桩返回默认 0，不抛异常，随事务提交）
        subsidyService.grant(grantReq(new BigDecimal("100")), TENANT_ID, 1L);
        assertEquals(0, new BigDecimal("100").compareTo(accountOf(USER_ID).getBalance()));

        // 让流水 INSERT 失败，模拟「扣款成功、写流水时崩溃」
        when(recordMapper.insert(any(MealSubsidyRecord.class)))
                .thenThrow(new RuntimeException("模拟流水落库失败"));

        assertThrows(RuntimeException.class,
                () -> subsidyService.consumeForOrder(USER_ID, TENANT_ID, ORDER_ID,
                        new BigDecimal("30"), "T-ATOMIC-CONSUME"));

        assertEquals(0, new BigDecimal("100").compareTo(accountOf(USER_ID).getBalance()),
                "流水失败应回滚扣款，余额保持 100，绝不能变成 70（钱扣了流水没落）");
    }

    @Test
    @DisplayName("回充 - 流水落库失败时回充整体回滚")
    void refund_rollsBackWhenLedgerFails() {
        when(recordMapper.selectCount(any())).thenReturn(0L);
        subsidyService.grant(grantReq(new BigDecimal("100")), TENANT_ID, 1L);
        subsidyService.consumeForOrder(USER_ID, TENANT_ID, ORDER_ID, new BigDecimal("30"), "T-ATOMIC-REFUND");
        assertEquals(0, new BigDecimal("70").compareTo(accountOf(USER_ID).getBalance()));

        when(recordMapper.insert(any(MealSubsidyRecord.class)))
                .thenThrow(new RuntimeException("模拟流水落库失败"));

        assertThrows(RuntimeException.class,
                () -> subsidyService.refundForOrder(TENANT_ID, USER_ID, ORDER_ID,
                        new BigDecimal("30"), "R-ATOMIC-REFUND"));

        assertEquals(0, new BigDecimal("70").compareTo(accountOf(USER_ID).getBalance()),
                "回充流水失败应整体回滚，余额保持核销后的 70");
    }

    // ==================== 辅助 ====================

    private SubsidyGrantDTO grantReq(BigDecimal amount) {
        SubsidyGrantDTO dto = new SubsidyGrantDTO();
        dto.setUserId(USER_ID);
        dto.setAmount(amount);
        return dto;
    }

    private void insertUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setName("事务原子性测试用户");
        user.setPhone("13911" + String.format("%06d", USER_ID % 1000000));
        user.setStatus(1);
        user.setTenantId(TENANT_ID);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    private MealSubsidyAccount accountOf(Long userId) {
        return subsidyService.getOne(new LambdaQueryWrapper<MealSubsidyAccount>()
                .eq(MealSubsidyAccount::getTenantId, TENANT_ID)
                .eq(MealSubsidyAccount::getUserId, userId));
    }
}
