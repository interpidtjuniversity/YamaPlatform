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
        UserLogin ul;
        if (null != (ul = userLoginDao.queryUser(userLogin))) {
            // 生成token
            String token = JwtUtil.getToken(userLoginModel);
            guavaCacheService.putToken(token, userLoginModel);
            UserLoginModel ulm = UserLoginModel.LOGIN_SUCCESS().setToken(token);
            if ("TEACHER".equals(ul.getRole())) {
                ulm.setUrl("/teacherhome");
            } else if ("STUDENT".equals(ul.getRole())) {
                ulm.setUrl("/studenthome");
            }
            return ulm;
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

    @Override
    public boolean isTeacher(String token) {
        UserLoginModel ulm = JwtUtil.getTokenInfo(token);
        if (null == ulm) {
            return false;
        }
        UserLogin userLogin = UserLogin.builder()
                .studentId(ulm.getStudentId())
                .password(ulm.getPassword())
                .build();

        UserLogin ul;
        if (null != (ul =userLoginDao.queryUser(userLogin))) {
            return "TEACHER".equals(ul.getRole());
        }
        return false;
    }

    @Override
    public String studentName(String studentId) {
        UserLogin userLogin = userLoginDao.queryStudentName(studentId);
        return null == userLogin ? null : userLogin.getStudentName();
    }
}
