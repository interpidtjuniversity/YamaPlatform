package com.clcy.grade_feedback.service;

import com.clcy.grade_feedback.model.UserLoginModel;

public interface LoginService {

    Object login(UserLoginModel login);

    boolean updatePassword(UserLoginModel login);

    boolean isTeacher(String token);

    String studentName(String studentId);
}
