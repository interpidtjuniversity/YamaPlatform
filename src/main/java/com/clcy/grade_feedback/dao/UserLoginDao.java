package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.UserLogin;
import com.clcy.grade_feedback.model.UserLoginModel;
import org.apache.ibatis.annotations.Param;

public interface UserLoginDao {

    UserLogin queryUser(@Param("userLogin") UserLoginModel userLogin);
}
