package com.xinfei.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.xinfei.module.user.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户 Mapper 接口
 * </p>
 *
 * @author 心飞为你飞
 * @since 2024-01-01
 */
@Mapper
public interface UserMapper extends BaseMapper<User>{

    /**
     * 按手机号查询用户（跨租户，供匿名登录使用）
     * <p>
     * 用户登录（/user/login）是公开端点，LoginCheckFilter 放行时不会设置租户上下文，
     * 若走租户插件会注入 {@code tenant_id = -1} 导致永远查不到用户、登录失败。
     * 登录前尚不知用户归属哪个租户，必须跳过租户过滤、按手机号全局匹配，
     * 登录成功后租户上下文由用户自身 tenantId 恢复。
     * </p>
     *
     * @param phone 手机号
     * @return 用户信息，不存在则返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM `user` WHERE phone = #{phone} ORDER BY id ASC LIMIT 1")
    User selectByPhoneIgnoreTenant(@Param("phone") String phone);

    /**
     * 按微信小程序 openid 查用户（wxLogin 登录前无租户上下文，忽略租户插件）。
     * <p>openid 全局唯一（uk_user_openid），登录成功后租户上下文由用户自身 tenantId 恢复，
     * 口径同 selectByPhoneIgnoreTenant。</p>
     *
     * @param openid 微信小程序 openid
     * @return 用户信息，不存在则返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM `user` WHERE openid = #{openid} LIMIT 1")
    User selectByOpenidIgnoreTenant(@Param("openid") String openid);

    /**
     * 按门店(tenant_id)聚合指定时间区间内的新增用户数（总部控制台用）
     * <p>仅在超管视图（tenantId 上下文为空）下跨门店返回全部分组；用于替代逐店 N+1 查询</p>
     * <p>必须 @InterceptorIgnore 绕开租户拦截器：否则 MyBatis-Plus 会注入
     * {@code WHERE tenant_id = -1}，导致跨门店聚合完全失效。</p>
     *
     * @param start 起始时间（含）
     * @param end   结束时间（不含）
     * @return 每个 tenantId 的 newUsers
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tenant_id AS tenantId, COUNT(*) AS newUsers "
            + "FROM `user` WHERE create_time >= #{start} AND create_time < #{end} "
            + "GROUP BY tenant_id")
    List<Map<String, Object>> statNewUsersByTenant(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * 将微信 openid 绑定到指定用户（wxLogin 绑定链路，miniapp 前置改造 5.3）。
     * <p>绑定动作发生在匿名登录请求内（无租户上下文），忽略租户插件避免 fail-closed
     * 追加 tenant_id=-1 导致静默更新 0 行；{@code openid IS NULL} 条件防并发重复绑定，
     * 与唯一键 uk_user_openid 双保险。</p>
     *
     * @param userId 用户 ID
     * @param openid 微信小程序 openid
     * @return 绑定成功返回 1；该用户已有 openid 返回 0
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE `user` SET openid = #{openid}, update_time = NOW() WHERE id = #{userId} AND openid IS NULL")
    int bindOpenid(@Param("userId") Long userId, @Param("openid") String openid);
}