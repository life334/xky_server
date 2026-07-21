package com.xakcch.common.exception.user;

import com.xakcch.common.exception.base.BaseException;

/**
 * 用户信息异常类
 * 
 * @author liuyonghui
 */
public class UserException extends BaseException
{
    private static final long serialVersionUID = 1L;

    public UserException(String code, Object[] args)
    {
        super("user", code, args, null);
    }
}
