package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 全局登录拦截器
 */
public class LoginINterceptor implements HandlerInterceptor {
    /**
     * http拦截器
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //    判断threadLocal是否有用户id
        if (UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }
        //有就放行
        return true;
    }
}
