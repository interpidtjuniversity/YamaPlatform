package com.clcy.grade_feedback.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.clcy.grade_feedback.model.UserLoginModel;
import com.clcy.grade_feedback.service.GuavaCacheService;
import com.clcy.grade_feedback.utils.JwtUtil;
import com.clcy.grade_feedback.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private GuavaCacheService guavaCacheService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        if (requestURI.contains("/api/login") || requestURI.contains("/api/checkSession")) {
            return true;
        }
        String token = request.getHeader("Authorization");

        // 没有携带token(非法请求), token不存在 => 用户未登陆或者登陆失效
        if (null == token || null == guavaCacheService.getToken(token)) {
            response.getWriter().write(JSONObject.toJSONString(UserLoginModel.PLEASE_LOGIN()));
            return false;
        }
        // 只是刷新token过期时间, 不进行token生成
        UserLoginModel userLogin = JwtUtil.verify(token);
        UserHolder.setValue(userLogin);
        guavaCacheService.putToken(token, userLogin);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 无论本次请求是否抛异常, 都必须清理 ThreadLocal, 防止 Tomcat 线程复用时残留上一个用户的身份信息
        UserHolder.clear();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}