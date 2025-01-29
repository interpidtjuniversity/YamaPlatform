package com.clcy.grade_feedback.service.v2;

import com.clcy.grade_feedback.model.v2.ClassInfoModel;
import com.clcy.grade_feedback.model.v2.StudentClassInfoModel;
import com.clcy.grade_feedback.model.v2.StudentClassMetaModel;

import java.util.List;

public interface ClassService {

    int createClass(ClassInfoModel classModel);

    int addStudentsToClass(StudentClassInfoModel infoModel);

    List<ClassInfoModel> queryClassForOwner(String ownerNumber);

    List<StudentClassMetaModel> queryStudentClasses(String studentId);

    StudentClassInfoModel queryClassStudents(int classId);
}
