package com.clcy.grade_feedback.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class UserLoginModel {

    public static UserLoginModel PLEASE_LOGIN() {
        return UserLoginModel.builder().message("请重新登陆").code("0").token(null).build();
    }

    public static UserLoginModel PLEASE_CHECK() {
        return UserLoginModel.builder().message("用户名或密码错误").code("1").build();
    }

    public static UserLoginModel LOGIN_SUCCESS() {
        return UserLoginModel.builder().message("登陆成功").code("100").build();
    }


    @Getter
    @Setter
    private String studentId;

    @Getter
    @Setter
    private String password;

    @Getter
    @Setter
    private String studentName;

    @Getter
    @Setter
    private String message;

    @Getter
    @Setter
    private String code;

    @Getter
    private String token;

    public UserLoginModel setToken(String token) {
        this.token = token;
        return this;
    }

    @Getter
    private String url;

    public UserLoginModel setUrl(String url) {
        this.url = url;
        return this;
    }
}
