package com.clcy.grade_feedback.manager;

import com.clcy.grade_feedback.model.v2.*;
import com.clcy.grade_feedback.service.v2.ClassService;
import com.clcy.grade_feedback.service.v2.GroupService;
import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeacherManagerImpl implements TeacherManager{

    @Autowired
    private ClassService classService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private TransactionTemplate transactionTemplate;


    @Override
    public boolean createClass(OwnerClassModel createModel) {
        transactionTemplate.execute(status -> {
            try {
                // 1.创建班级
                int classId = classService.createClass(ClassInfoModel.builder().className(createModel.getClassName()).ownerNumber(createModel.getOwnerNumber()).build());
                // 2.创建班级内的学生
                StudentClassInfoModel studentClassInfoModel =
                        StudentClassInfoModel.builder()
                                .classId(classId)
                                .className(createModel.getClassName())
                                .students(createModel.getStudentClassInfoModel().getStudents())
                                .build();
                classService.addStudentsToClass(studentClassInfoModel);
                // 3.创建班级的分组
                createModel.getGroupClassInfoModel().forEach(groupClassInfoModel -> groupClassInfoModel.setClassId(classId));
                groupService.createGroupsForClass(createModel.getGroupClassInfoModel());
                // 4.回调分组策略 默认default的话创建两个初始分组

                // TODO 策略模式 reconstruct
                List<GroupClassInfoModel> groups = groupService.queryClassGroups(classId);
                Map<Integer, List<String>> partitionRatioMap = partitionListByRatio(new ArrayList<>(createModel.getStudentClassInfoModel().getStudents().keySet()), groups);
                groups.forEach(group -> {
                    if ("default".equalsIgnoreCase(group.getGroupingStrategy())) {
                        // 初始随机分组
                        groupService.createGroupInstance(GroupInstanceModel
                                .builder()
                                        .groupId(group.getGroupId())
                                        .groupName(group.getGroupName())
                                        .classId(group.getClassId())
                                        .className(group.getClassName())
                                        .examName("default")
                                        .studentsId(partitionRatioMap.get(group.getGroupId()))
                                .build());
                    }
                });

                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                return false;
            }
        });

        return true;
    }

    @Override
    public List<OwnerClassModel> queryClasses(String ownerNumber) {
        // 查询班级列表
        List<ClassInfoModel> ownerClasses = classService.queryClassForOwner(ownerNumber);
        return ownerClasses.stream().map(model -> {
            // 查询该班级下的学生列表
            StudentClassInfoModel studentClassInfoModel = classService.queryClassStudents(model.getClassId());
            // 查询该班级下的学生分组信息
            List<GroupClassInfoModel> groupClassInfoModels = groupService.queryClassGroups(model.getClassId());

            return OwnerClassModel.builder()
                    .ownerNumber(ownerNumber)
                    .classId(model.getClassId())
                    .className(model.getClassName())
                    .studentClassInfoModel(studentClassInfoModel)
                    .groupClassInfoModel(groupClassInfoModels)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * @param list 是学生Map的keys
     * @param groups 是按照创建顺序排列好的班级分组
     * */
    public static <T> Map<Integer, List<T>> partitionListByRatio(List<T> list, List<GroupClassInfoModel> groups) {
        // TODO 检查比例总和是否为1.0
        int totalSize = list.size();
        // 随机打乱
        Collections.shuffle(list);

        Map<Integer, List<T>> ans = Maps.newHashMap();
        int startIndex = 0;

        // 按照比例计算每部分的起始和结束索引
        for (int i = 0; i < groups.size(); i++) {
            GroupClassInfoModel group = groups.get(i);
            double ratio = Integer.parseInt(group.getGroupingInfo().replace("%", "")) / 100.0;
            int partitionSize = (int) Math.round(totalSize * ratio); // 使用四舍五入
            if (i == groups.size() - 1) {
                partitionSize = totalSize - startIndex;
            }
            int endIndex = startIndex + partitionSize;
            ans.put(group.getGroupId(), list.subList(startIndex, endIndex));
            startIndex = endIndex;
        }
        return ans;
    }
}
