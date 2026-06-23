package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.ClassInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ClassInfoDao {

    int createClass(@Param("classInfo") ClassInfo classInfo);

    List<ClassInfo> queryClassForOwner(@Param("ownerNumber") String ownerNumber);

    /**
     * 校验某个班级是否属于某个拥有者, 用于接口权限校验. 返回非 null 即有权限.
     */
    ClassInfo queryClassByIdAndOwner(@Param("classId") int classId, @Param("ownerNumber") String ownerNumber);
}
