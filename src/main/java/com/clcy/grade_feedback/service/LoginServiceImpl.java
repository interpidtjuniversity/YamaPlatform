package com.clcy.grade_feedback.service;

import com.clcy.grade_feedback.dao.UserLoginDao;
import com.clcy.grade_feedback.entity.UserLogin;
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
    public Object login(UserLoginModel userLoginModel) {
        UserLogin userLogin = UserLogin.builder()
                .studentId(userLoginModel.getStudentId())
                .password(userLoginModel.getPassword())
                .build();
        if (null != userLoginDao.queryUser(userLogin)) {
            // 生成token
            String token = JwtUtil.getToken(userLoginModel);
            guavaCacheService.putToken(token, userLoginModel);
            return UserLoginModel.LOGIN_SUCCESS().setToken(token);
        }
        return UserLoginModel.PLEASE_CHECK();
    }

    @Override
    public boolean updatePassword(UserLoginModel loginModel) {
        UserLogin userLogin = UserLogin.builder()
                .studentId(loginModel.getStudentId())
                .password(loginModel.getPassword())
                .build();
        userLoginDao.updatePassword(userLogin);
        guavaCacheService.deleteToken(JwtUtil.getToken(loginModel));

        return true;
    }
}
