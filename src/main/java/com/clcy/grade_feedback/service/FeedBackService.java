package com.clcy.grade_feedback.service;

import com.clcy.grade_feedback.model.FeedBackModel;
import com.clcy.grade_feedback.model.FeedBackPuzzleModel;
import com.clcy.grade_feedback.model.v2.GroupExamDetailModel;
import com.clcy.grade_feedback.model.v2.GroupExamMetaModel;

import java.util.List;

public interface FeedBackService {

    List<FeedBackModel> queryFeedBackListByStudentId(String studentId);

    boolean feedBack(FeedBackModel model);

    boolean cancelFeedBack(FeedBackModel model);

    List<GroupExamDetailModel> queryExamNeedFeedBackPuzzles(String studentId, String examName);

    boolean feedBackPuzzle(FeedBackPuzzleModel feedBackPuzzleModel);

    boolean cancelFeedBackPuzzle(FeedBackPuzzleModel feedBackPuzzleModel);

    Integer getStudentExamGroup(int classId, String studentId, String examName);

    // 由每次考试完成后自动生成
    boolean generateFeedBackPuzzle(int classId, String studentId, String studentName, String examName, Integer groupId, List<Integer> puzzlesIdx, GroupExamMetaModel metaModel);
}
