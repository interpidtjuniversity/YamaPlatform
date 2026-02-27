package com.clcy.grade_feedback.service.v2;

import com.clcy.grade_feedback.model.v2.GroupClassInfoModel;
import com.clcy.grade_feedback.model.v2.GroupInstanceModel;

import java.util.List;

public interface GroupService {

    int createGroupsForClass(List<GroupClassInfoModel> infoModels);

    List<GroupClassInfoModel> queryClassGroups(int classId);

    GroupClassInfoModel queryGroupClass(int groupId);

    int createGroupInstance(GroupInstanceModel model);

    List<GroupInstanceModel> queryGroupInstanceByGroupId(int groupId);

    List<GroupInstanceModel> queryGroupInstanceByClassIdAndExamName(int classId, String examName);

    int updateGroupInstanceExam(int groupId, String oldExamName, String newExamName);
}
