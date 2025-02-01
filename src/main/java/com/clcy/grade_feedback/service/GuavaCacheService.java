package com.clcy.grade_feedback.service;

import com.clcy.grade_feedback.model.v2.GroupExamDetailModel;
import com.clcy.grade_feedback.model.v2.GroupExamMetaModel;
import com.clcy.grade_feedback.model.v2.GroupInstanceModel;

import java.util.List;

public interface GuavaCacheService {

    Object getToken(String key);

    Object putToken(String key, Object Value);

    void deleteToken(String key);

    List<GroupInstanceModel> getGroupInstances(int groupId);

    List<GroupExamMetaModel> getGroupExams(int groupId);

    List<GroupExamDetailModel> getGroupExamDetails(int groupId, String examName);
}
