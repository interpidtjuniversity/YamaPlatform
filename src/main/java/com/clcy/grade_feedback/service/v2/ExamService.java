package com.clcy.grade_feedback.service.v2;

import com.clcy.grade_feedback.model.v2.GroupExamDetailModel;
import com.clcy.grade_feedback.model.v2.GroupExamMetaModel;
import com.clcy.grade_feedback.model.v2.GroupExamStudentAnswerRecordModel;

import java.util.List;

public interface ExamService {

    List<GroupExamMetaModel> queryGroupExamsMeta(int groupId);

    GroupExamMetaModel queryGroupExamMeta(int groupId, String examName);

    List<GroupExamDetailModel> queryGroupExamDetail(int groupId, String examName, boolean containsAnswer, boolean containsAnalysis);

    List<GroupExamStudentAnswerRecordModel> queryStudentAnswerRecords(int classId, String studentId);

    GroupExamStudentAnswerRecordModel queryStudentAnswerRecord(int groupId, String examName, String studentId);

    int addStudentAnswerRecord(GroupExamStudentAnswerRecordModel record);

}
