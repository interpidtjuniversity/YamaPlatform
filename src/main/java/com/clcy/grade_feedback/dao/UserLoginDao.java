package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.UserLogin;
import org.apache.ibatis.annotations.Param;

public interface UserLoginDao {

    UserLogin queryUser(@Param("userLogin") UserLogin userLogin);

    boolean updatePassword(@Param("userLogin") UserLogin userLogin);
}
