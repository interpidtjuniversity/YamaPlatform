package com.clcy.grade_feedback.utils;

import com.clcy.grade_feedback.model.UserLoginModel;

public class UserHolder {
    public static final ThreadLocal<UserLoginModel> userThreadLocal = new ThreadLocal<>();

    public static void setValue(UserLoginModel userLogin) {
        userThreadLocal.set(userLogin);
    }

    public static UserLoginModel getValue() {
        return userThreadLocal.get();
    }

    public static void clear() {
        userThreadLocal.remove();
    }
}
