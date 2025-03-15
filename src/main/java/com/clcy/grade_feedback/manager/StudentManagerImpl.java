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

import java.util.*;
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
                                            .duration(examMetaModel.getDuration())
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

                // 5.计算得分
                List<GroupExamDetailModel> detailModels = guavaCacheService.getGroupExamDetails(model.getGroupId(), model.getExamName());
                Map<Integer, String> ansMap = detailModels.stream().collect(Collectors.toMap(GroupExamDetailModel::getPuzzleIdx, GroupExamDetailModel::getAnswer));
                int[] score = {0};
                record.getAnswers().forEach((key, value) -> {
                    if (value.equals(ansMap.get(Integer.valueOf(key)))) {
                        score[0]++;
                    }
                });
                model.setScore(String.valueOf(score[0]));
            }
        });
        List<StudentExamMetaModel> result = Lists.newArrayList();
        hitMap.forEach((examName, metaModel) -> result.add(metaModel));
        return result.stream().sorted(Comparator.comparingInt(StudentExamMetaModel::getId)).collect(Collectors.toList());
    }



    @Override
    public List<GroupExamDetailModel> queryExamDetail(int groupId, String examName, boolean containsAnswer, boolean containsAnalysis) {
        return examService.queryGroupExamDetail(groupId, examName, containsAnswer, containsAnalysis);
    }

    @Override
    public GroupExamMetaModel queryExamMeta(int groupId, String examName) {
        return examService.queryGroupExamMeta(groupId, examName);
    }

    @Override
    public GroupExamStudentAnswerRecordModel queryAnswerRecord(int groupId, String examName, String studentId) {
        return examService.queryStudentAnswerRecord(groupId, examName, studentId);
    }

    @Override
    public boolean submitExam(GroupExamStudentAnswerRecordModel model) {
        GroupExamMetaModel exam = examService.queryGroupExamMeta(model.getGroupId(), model.getExamName());
        // 如果考试已经结束
        if (null == exam || exam.getEndTime().before(new Date())) {
            return false;
        }
        // 如果已经提交过
        GroupExamStudentAnswerRecordModel record = queryAnswerRecord(model.getGroupId(), model.getExamName(), model.getStudentId());
        if (null != record) {
            return false;
        }

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
                        .clickNextTimeList(model.getClickNextTimeList())
                        .build()
        ) == 1;

    }

    @Override
    public List<StudentExamRecordModel> examRecords(int groupId, String examName, String studentId) {
        // 题目
        List<GroupExamDetailModel> detailModels = queryExamDetail(groupId, examName, true, true);
        // 作答
        GroupExamStudentAnswerRecordModel answerModel = queryAnswerRecord(groupId, examName, studentId);

        return detailModels.stream().map(detail -> {
            StudentExamRecordModel recordModel = StudentExamRecordModel
                    .builder()
                    .groupId(detail.getGroupId())
                    .groupName(detail.getGroupName())
                    .examName(detail.getExamName())
                    .content(detail.getContent())
                    .choices(detail.getChoices())
                    .images(detail.getImages())
                    .puzzleIdx(detail.getPuzzleIdx())
                    .answer(detail.getAnswer())
                    .analysis(detail.getAnalysis())
                    .knowledgePoints(detail.getKnowledgePoints())
                    .build();
            if (null != answerModel && answerModel.getAnswers().containsKey(String.valueOf(detail.getPuzzleIdx()))) {
                recordModel.setYourChoice(
                        answerModel.getAnswers().get(String.valueOf(detail.getPuzzleIdx()))
                );
                recordModel.setStatus("已作答");
            } else {
                recordModel.setStatus("未作答");
            }
            return recordModel;
        }).collect(Collectors.toList());
    }
}
