package com.clcy.grade_feedback.manager;

import com.clcy.grade_feedback.model.v2.*;
import com.clcy.grade_feedback.service.GuavaCacheService;
import com.clcy.grade_feedback.service.v2.ClassService;
import com.clcy.grade_feedback.service.v2.ExamService;
import com.clcy.grade_feedback.service.v2.GroupService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StudentManagerImpl implements StudentManager{

    @Autowired
    private GroupService groupService;

    @Autowired
    private ExamService examService;

    @Autowired
    private ClassService classService;

    @Autowired
    private GuavaCacheService guavaCacheService;

    @Override
    public List<StudentClassMetaModel> queryClassList(String studentId) {
        List<StudentClassMetaModel> classes =  classService.queryStudentClasses(studentId);
        classes.forEach(cls -> {
            cls.setStudents(classService.queryClassStudents(cls.getClassId()).getStudents());
        });

        return classes;
    }

    @Override
    public List<StudentExamMetaModel> queryExamList(int classId, String studentId) {
        // 1.查询该班级下的分组信息
        List<GroupClassInfoModel> classGroups = groupService.queryClassGroups(classId);
        // 2.获取所有分组的所有分组实例, 判断该学生每次都在哪个分组里
        Map<String, StudentExamMetaModel> hitMap = Maps.newHashMap();
        classGroups.forEach(group -> {
            // 组内考试元信息
            List<GroupExamMetaModel> exams = guavaCacheService.getGroupExams(group.getGroupId());
            // 分组实例
            List<GroupInstanceModel> instances = guavaCacheService.getGroupInstances(group.getGroupId());
            instances.forEach(instance -> {
                if (instance.getStudentsId().contains(studentId)) {
                    exams
                            .stream()
                            .filter(exam -> Objects.equals(exam.getExamName(), instance.getExamName()))
                            .findAny()
                            .ifPresent(examMetaModel -> hitMap.put(
                                    instance.getExamName(),
                                    StudentExamMetaModel.builder()
                                            .id(examMetaModel.getId())
                                            .groupId(group.getGroupId())
                                            .groupName(group.getGroupName())
                                            .examName(examMetaModel.getExamName())
                                            .startTime(examMetaModel.getStartTime())
                                            .endTime(examMetaModel.getEndTime())
                                            // 初始考卷都默认未作答
                                            .status("未作答")
                                            .build()
                            ));
                }
            });
        });
        // 3.查询该学生的答题记录表, 主动点击交卷的或者在考试页面倒计时结束等待交卷的
        List<GroupExamStudentAnswerRecordModel> records = examService.queryStudentAnswerRecords(classId, studentId);
        // 4.执行过滤并返回
        records.forEach(record -> {
            StudentExamMetaModel model;
            if (null != (model = hitMap.get(record.getExamName()))) {
                model.setStatus("已作答");
            }
        });
        List<StudentExamMetaModel> result = Lists.newArrayList();
        hitMap.forEach((examName, metaModel) -> result.add(metaModel));
        return result.stream().sorted(Comparator.comparingInt(StudentExamMetaModel::getId)).collect(Collectors.toList());
    }



    @Override
    public List<GroupExamDetailModel> queryExamDetail(int groupId, String examName, boolean containsAnswer) {
        List<GroupExamDetailModel> examDetail = examService.queryGroupExamDetail(groupId, examName);
        if (!containsAnswer) {
            examDetail.forEach(puzzle -> {
                puzzle.setAnswer(null);
            });
        }
        return examDetail;
    }

    @Override
    public GroupExamStudentAnswerRecordModel queryAnswerRecord(int groupId, String examName, String studentId) {
        return examService.queryStudentAnswerRecord(groupId, examName, studentId);
    }

    @Override
    public boolean submitExam(GroupExamStudentAnswerRecordModel model) {
        return examService.addStudentAnswerRecord(
                GroupExamStudentAnswerRecordModel.builder()
                        .classId(model.getClassId())
                        .className(model.getClassName())
                        .groupId(model.getGroupId())
                        .groupName(model.getGroupName())
                        .studentId(model.getStudentId())
                        .studentName(model.getStudentName())
                        .examName(model.getExamName())
                        .answers(model.getAnswers())
                        .build()
        ) == 1;

    }
}
