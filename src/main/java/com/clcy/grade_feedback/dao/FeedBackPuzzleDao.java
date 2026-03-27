package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.FeedBackPuzzle;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FeedBackPuzzleDao {

    // 插入一条记录
    int insertOne(@Param("feedBackPuzzle") FeedBackPuzzle feedBackPuzzle);

    // 批量插入记录，
    int batchInsert(@Param("feedBackPuzzles") List<FeedBackPuzzle> feedBackPuzzles);

    List<FeedBackPuzzle> queryByStudentId(@Param("studentId") String studentId, @Param("groupId") int groupId, @Param("examName") String examName);

    FeedBackPuzzle queryOne(@Param("studentId") String studentId, @Param("groupId") int groupId, @Param("examName") String examName, @Param("puzzleIdx") int puzzleIdx);

    // 直接将未反馈改未已反馈
    boolean feedBackPuzzle(@Param("feedBackPuzzle") FeedBackPuzzle feedBackPuzzle);
}
