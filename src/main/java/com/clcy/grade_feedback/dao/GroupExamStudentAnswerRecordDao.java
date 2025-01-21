package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.GroupExamStudentAnswerRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GroupExamStudentAnswerRecordDao {

    /**
     * 查询某个学生在某个班的全部作答记录
     * */
    List<GroupExamStudentAnswerRecord> queryStudentAnswerRecords(@Param("classId") int classId, @Param("studentId") String studentId);

    /**
     * 查询某个学生某次考试的作答记录
     * */
    GroupExamStudentAnswerRecord queryStudentAnswerRecord(@Param("groupId") int groupId, @Param("examName") String examName, @Param("studentId") String studentId);

    /**
     * 学生提交一条作答记录
     * 只要学生主动点击了提交, 或者在答题页面等待答题倒计时结束, 都会插入这条作答记录
     * */
    int addStudentAnswerRecord(@Param("record") GroupExamStudentAnswerRecord record);

}
