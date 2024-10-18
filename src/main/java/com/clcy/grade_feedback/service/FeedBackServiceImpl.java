package com.clcy.grade_feedback.service;

import com.alibaba.fastjson.JSONObject;
import com.clcy.grade_feedback.dao.FeedBackDao;
import com.clcy.grade_feedback.entity.FeedBack;
import com.clcy.grade_feedback.model.FeedBackModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeedBackServiceImpl implements FeedBackService{

    @Resource
    private FeedBackDao feedBackDao;

    @Override
    public List<FeedBackModel> queryFeedBackListByStudentId(String studentId) {
        List<FeedBack> feedBacks = feedBackDao.queryByStudentId(studentId);

        return feedBacks.stream().map(feedBack -> FeedBackModel.builder()
                .studentId(feedBack.getStudentId())
                .studentName(feedBack.getStudentName())
                .examName(feedBack.getExamName())
                .id(feedBack.getId())
                .status(feedBack.getFeedbackStatus())
                .feedbackText(feedBack.getFeedbackContent())
                .images(JSONObject.parseArray(feedBack.getFeedbackImages(), String.class))
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    public boolean feedBack(FeedBackModel model) {
        return feedBackDao.updateFeedBack(FeedBack.builder()
                .id(model.getId())
                .studentId(model.getStudentId())
                .studentName(model.getStudentName())
                .examName(model.getExamName())
                .feedbackContent(model.getFeedbackText())
                .feedbackImages(JSONObject.toJSONString(model.getImages()))
                .feedbackStatus("已反馈")
                .build()
        );
    }

    @Override
    public boolean cancelFeedBack(FeedBackModel model) {
        return feedBackDao.updateFeedBack(FeedBack.builder()
                .id(model.getId())
                .studentId(model.getStudentId())
                .studentName(model.getStudentName())
                .examName(model.getExamName())
                .feedbackContent("")
                .feedbackImages(JSONObject.toJSONString(new ArrayList<>()))
                .feedbackStatus("未反馈")
                .build()
        );
    }
}
