package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.StudentClassInfo;
import com.clcy.grade_feedback.entity.UserLogin;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StudentClassInfoDao {

    int batchInsertClassStudents(@Param("infos") List<StudentClassInfo> infos);

    List<StudentClassInfo> queryClassStudents(@Param("classId") int classId);

    List<StudentClassInfo> queryStudentClasses(@Param("studentId") String studentId);
}
