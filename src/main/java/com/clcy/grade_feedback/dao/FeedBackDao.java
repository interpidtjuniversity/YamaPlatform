package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.FeedBack;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FeedBackDao {

    List<FeedBack> queryByStudentId(@Param("studentId") String studentId);

    boolean updateFeedBack(@Param("feedBack") FeedBack feedBack);

    // 插入一条记录
    int insertOne(@Param("feedBack") FeedBack feedBack);

    /**
     * 删除某个学生某次考试的总反馈记录, 用于生成反馈前保证幂等.
     */
    int deleteByStudentIdAndExam(@Param("feedBack") FeedBack feedBack);
}
