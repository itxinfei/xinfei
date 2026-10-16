package com.xinfei.common;

/**
 * 自定义业务异常类
 *
 * @author xinfei
 * @since 2026-07-09
 */
public class CustomException extends RuntimeException {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 构造方法
     *
     * @param message 异常信息
     */
    public CustomException(String message){
        super(message);
    }
}
