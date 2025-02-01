package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.annotation.Limit;
import com.clcy.grade_feedback.enumerate.LimitType;
import com.clcy.grade_feedback.model.UserLoginModel;
import com.clcy.grade_feedback.service.GuavaCacheService;
import com.clcy.grade_feedback.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/grade_feedback/api")
public class LoginController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private GuavaCacheService guavaCacheService;

    @ResponseBody
    @RequestMapping("/login")
    public Object login(HttpServletRequest request, HttpServletResponse response, @RequestBody UserLoginModel userLogin) {
        if (null == userLogin || null == userLogin.getStudentId() || null == userLogin.getPassword()) {
            return UserLoginModel.PLEASE_CHECK();
        }
        return loginService.login(userLogin);
    }

    @ResponseBody
    @RequestMapping("/updatePassword")
    @Limit(period = 10, count = 5, limitType = LimitType.IP_METHOD, prefix = "LoginController.updatePassword")
    public Object updatePassword(HttpServletRequest request, HttpServletResponse response, @RequestBody UserLoginModel userLogin) {
        if (null == userLogin || null == userLogin.getStudentId() || null == userLogin.getPassword()) {
            return UserLoginModel.PLEASE_CHECK();
        }
        return loginService.updatePassword(userLogin);
    }

    @ResponseBody
    @RequestMapping("/checkSession")
    public boolean checkSession(HttpServletRequest request, HttpServletResponse response) {
        String token = request.getHeader("Authorization");
        return null != guavaCacheService.getToken(token);
    }

    @ResponseBody
    @RequestMapping("/isTeacher")
    public boolean isTeacher(HttpServletRequest request, HttpServletResponse response) {
        String token = request.getHeader("Authorization");
        return loginService.isTeacher(token);
    }

    @ResponseBody
    @RequestMapping("/test")
    public String test(HttpServletRequest request, HttpServletResponse response) {
        return "test";
    }
}
