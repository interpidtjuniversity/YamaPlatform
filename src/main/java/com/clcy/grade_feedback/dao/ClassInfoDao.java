package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.ClassInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ClassInfoDao {

    int createClass(@Param("classInfo") ClassInfo classInfo);

    List<ClassInfo> queryClassForOwner(@Param("ownerNumber") String ownerNumber);
}
