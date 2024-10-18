package com.clcy.grade_feedback.service;

import com.clcy.grade_feedback.model.FeedBackModel;

import java.util.List;

public interface FeedBackService {

    List<FeedBackModel> queryFeedBackListByStudentId(String studentId);

    boolean feedBack(FeedBackModel model);

    boolean cancelFeedBack(FeedBackModel model);
}
