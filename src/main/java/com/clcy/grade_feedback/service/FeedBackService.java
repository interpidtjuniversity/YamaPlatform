package com.clcy.grade_feedback.service;

import com.clcy.grade_feedback.model.FeedBackModel;
import com.clcy.grade_feedback.model.FeedBackPuzzleModel;
import com.clcy.grade_feedback.model.v2.GroupExamDetailModel;

import java.util.List;

public interface FeedBackService {

    List<FeedBackModel> queryFeedBackListByStudentId(String studentId);

    boolean feedBack(FeedBackModel model);

    boolean cancelFeedBack(FeedBackModel model);

    List<GroupExamDetailModel> queryExamNeedFeedBackPuzzles(String studentId, String examName);

    boolean feedBackPuzzle(FeedBackPuzzleModel feedBackPuzzleModel);

    boolean cancelFeedBackPuzzle(FeedBackPuzzleModel feedBackPuzzleModel);
}
