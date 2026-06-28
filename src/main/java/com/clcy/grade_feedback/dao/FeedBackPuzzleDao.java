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

    /**
     * 查询某学生某次考试的"全部"反馈题目记录(不限 deadline, 不 limit).
     * 供数据修复使用: 历史考试的 deadline 多已过期, queryByStudentId/queryOne 的 deadline > NOW() 过滤会漏掉.
     * 同一 (studentId, examName, puzzleIdx) 可能存在两条记录(tag 分别为 examName 与 yyMMdd), 都会返回.
     */
    List<FeedBackPuzzle> queryAllByStudentAndExam(@Param("studentId") String studentId, @Param("groupId") int groupId, @Param("examName") String examName);

    // 直接将未反馈改未已反馈
    boolean feedBackPuzzle(@Param("feedBackPuzzle") FeedBackPuzzle feedBackPuzzle);

    /**
     * 删除某个学生某次考试的全部反馈题目记录, 用于生成反馈前保证幂等,
     * 避免重复交卷/重试时产生重复记录.
     */
    int deleteByStudentIdAndExam(@Param("studentId") String studentId, @Param("groupId") int groupId, @Param("examName") String examName);
}
