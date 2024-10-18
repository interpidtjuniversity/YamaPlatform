package com.clcy.grade_feedback.service;

import com.clcy.grade_feedback.dao.UserLoginDao;
import com.clcy.grade_feedback.model.UserLoginModel;
import com.clcy.grade_feedback.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class LoginServiceImpl implements LoginService{

    @Autowired
    private GuavaCacheService guavaCacheService;

    @Resource
    private UserLoginDao userLoginDao;

    @Override
    public Object login(UserLoginModel userLogin) {
        if (null != userLogin && null != userLoginDao.queryUser(userLogin)) {
            // 生成token
            String token = JwtUtil.getToken(userLogin);
            guavaCacheService.putToken(token, userLogin);
            return UserLoginModel.LOGIN_SUCCESS().setToken(token);
        }
        return UserLoginModel.PLEASE_CHECK();
    }
}
