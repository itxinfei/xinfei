package com.xinfei.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xinfei.module.user.mapper.SmsSendLogMapper;
import com.xinfei.module.user.model.SmsSendLog;
import com.xinfei.module.user.service.SmsSendLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 短信发送台账服务实现。
 *
 * @author 心飞为你飞
 * @since 2026-10-19
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class SmsSendLogServiceImpl extends ServiceImpl<SmsSendLogMapper, SmsSendLog>
        implements SmsSendLogService {

    /**
     * 记录一次验证码发送。
     *
     * @param phone 手机号
     * @param scene 发送场景
     */
    @Override
    public void record(String phone, String scene) {
        SmsSendLog log = new SmsSendLog();
        log.setPhone(phone);
        log.setScene(scene);
        log.setSendDate(LocalDate.now());
        this.save(log);
    }

    /**
     * 统计该手机号今日已发送条数。
     *
     * @param phone 手机号
     * @return 今日已发送条数
     */
    @Override
    public long countTodayByPhone(String phone) {
        return this.count(new LambdaQueryWrapper<SmsSendLog>()
                .eq(SmsSendLog::getPhone, phone)
                .eq(SmsSendLog::getSendDate, LocalDate.now()));
    }

    /**
     * 统计全站今日总发送条数。
     *
     * @return 今日全站总条数
     */
    @Override
    public long countTodayTotal() {
        return this.count(new LambdaQueryWrapper<SmsSendLog>()
                .eq(SmsSendLog::getSendDate, LocalDate.now()));
    }
}
