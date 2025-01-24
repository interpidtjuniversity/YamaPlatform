package com.clcy.grade_feedback.manager;

import com.clcy.grade_feedback.model.v2.ClassInfoModel;
import com.clcy.grade_feedback.model.v2.GroupClassInfoModel;
import com.clcy.grade_feedback.model.v2.OwnerClassModel;
import com.clcy.grade_feedback.model.v2.StudentClassInfoModel;
import com.clcy.grade_feedback.service.v2.ClassService;
import com.clcy.grade_feedback.service.v2.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
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
        transactionTemplate.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
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
                    // 4.回调分组策略
                    // TODO
                    return true;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    return false;
                }
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
}
