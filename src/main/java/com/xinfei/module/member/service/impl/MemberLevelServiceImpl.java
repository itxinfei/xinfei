package com.xinfei.module.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinfei.module.member.mapper.MemberLevelMapper;
import com.xinfei.module.member.model.MemberLevel;
import com.xinfei.module.member.service.MemberLevelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员等级服务实现
 *
 * @author xinfei
 * @since 2026-07-09
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class MemberLevelServiceImpl extends ServiceImpl<MemberLevelMapper, MemberLevel> implements MemberLevelService {

    /**
     * 获取 default level。
     * @return 返回结果
     */
    @Override
    public MemberLevel getDefaultLevel() {
        LambdaQueryWrapper<MemberLevel> qw = new LambdaQueryWrapper<>();
        qw.orderByAsc(MemberLevel::getMinPoints);
        qw.last("LIMIT 1");
        return getOne(qw);
    }

    /**
     * 查找 level by points。
     * @param points 参数 points
     * @return 返回结果
     */
    @Override
    public MemberLevel findLevelByPoints(Long points) {
        LambdaQueryWrapper<MemberLevel> qw = new LambdaQueryWrapper<>();
        qw.le(MemberLevel::getMinPoints, points);
        qw.orderByDesc(MemberLevel::getMinPoints);
        qw.last("LIMIT 1");
        return getOne(qw);
    }
}

