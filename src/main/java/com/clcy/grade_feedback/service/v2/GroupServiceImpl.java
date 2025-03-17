package com.clcy.grade_feedback.service.v2;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.clcy.grade_feedback.dao.GroupClassInfoDao;
import com.clcy.grade_feedback.dao.GroupInstanceDao;
import com.clcy.grade_feedback.entity.GroupClassInfo;
import com.clcy.grade_feedback.entity.GroupInstance;
import com.clcy.grade_feedback.model.v2.GroupClassInfoModel;
import com.clcy.grade_feedback.model.v2.GroupInstanceModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupServiceImpl implements GroupService{

    @Resource
    private GroupClassInfoDao groupClassInfoDao;

    @Resource
    private GroupInstanceDao groupInstanceDao;

    @Override
    public int createGroupsForClass(List<GroupClassInfoModel> infoModel) {
        List<GroupClassInfo> infos = infoModel.stream().map(model ->
                GroupClassInfo.builder()
                    .classId(model.getClassId())
                    .className(model.getClassName())
                    .groupName(model.getGroupName())
                    .groupingStrategy(model.getGroupingStrategy())
                    .groupingInfo(model.getGroupingInfo())
                    .type(model.getType())
                    .build()
        ).collect(Collectors.toList());
        return groupClassInfoDao.createGroupsForClass(infos);
    }

    @Override
    public List<GroupClassInfoModel> queryClassGroups(int classId) {
        List<GroupClassInfo> infos = groupClassInfoDao.queryGroupsInClass(classId);
        return infos.stream().map(info -> GroupClassInfoModel
                .builder().groupId(info.getId())
                .classId(info.getClassId())
                .className(info.getClassName())
                .groupName(info.getGroupName())
                .groupingStrategy(info.getGroupingStrategy())
                .groupingInfo(info.getGroupingInfo())
                .type(info.getType())
                .build()
        ).sorted(Comparator.comparingInt(GroupClassInfoModel::getGroupId)).collect(Collectors.toList());
    }

    @Override
    public int createGroupInstance(GroupInstanceModel model) {
        return groupInstanceDao.createGroupInstance(GroupInstance
                .builder()
                        .classId(model.getClassId())
                        .className(model.getClassName())
                        .groupId(model.getGroupId())
                        .groupName(model.getGroupName())
                        .examName(model.getExamName())
                        .studentsId(JSONObject.toJSONString(model.getStudentsId()))
                .build()
        );
    }

    @Override
    public List<GroupInstanceModel> queryGroupInstanceByGroupId(int groupId) {
        List<GroupInstance> instances = groupInstanceDao.queryInstanceByGroupId(groupId);
        return instances.stream().map(instance ->
                GroupInstanceModel.builder()
                        .id(instance.getId())
                        .classId(instance.getClassId())
                        .className(instance.getClassName())
                        .groupId(instance.getGroupId())
                        .groupName(instance.getGroupName())
                        .examName(instance.getExamName())
                        .studentsId(JSONArray.parseArray(instance.getStudentsId(), String.class))
                        .build()
        ).sorted(Comparator.comparingInt(GroupInstanceModel::getId)).collect(Collectors.toList());
    }

    @Override
    public int updateGroupInstanceExam(int groupId, String oldExamName, String newExamName) {
        return groupInstanceDao.updateGroupInstanceExam(groupId, oldExamName, newExamName);
    }

    @Override
    public GroupClassInfoModel queryGroupClass(int groupId) {
        GroupClassInfo info = groupClassInfoDao.queryGroupClass(groupId);
        if (null != info) {
            return GroupClassInfoModel
                    .builder()
                    .classId(info.getClassId())
                    .className(info.getClassName())
                    .groupId(groupId)
                    .groupName(info.getGroupName())
                    .build();
        }
        return null;
    }
}
