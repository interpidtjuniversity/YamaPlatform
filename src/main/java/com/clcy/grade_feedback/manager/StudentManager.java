package com.clcy.grade_feedback.manager;

import com.clcy.grade_feedback.model.v2.*;

import java.util.List;

public interface StudentManager {

    List<StudentClassMetaModel> queryClassList(String studentId);

    // 查询某个学生的考试列表
    List<StudentExamMetaModel> queryExamList(int classId, String studentId);

    // 查询某次考试的考题
    List<GroupExamDetailModel> queryExamDetail(int groupId, String examName, boolean containsAnswer);

    // 查询学生的某次作答记录
    GroupExamStudentAnswerRecordModel queryAnswerRecord(int groupId, String examName, String studentId);

    boolean submitExam(GroupExamStudentAnswerRecordModel model);
}
