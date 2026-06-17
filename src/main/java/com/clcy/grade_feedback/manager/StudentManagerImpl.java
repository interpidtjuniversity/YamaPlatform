package com.clcy.grade_feedback.manager;

import com.clcy.grade_feedback.model.v2.*;
import com.clcy.grade_feedback.model.v3.GroupExamModel;
import com.clcy.grade_feedback.model.v3.SyncExamModel;
import com.clcy.grade_feedback.model.v3.SyncPuzzleModel;
import com.clcy.grade_feedback.service.GuavaCacheService;
import com.clcy.grade_feedback.service.FeedBackService;
import com.clcy.grade_feedback.service.v2.ClassService;
import com.clcy.grade_feedback.service.v2.ExamService;
import com.clcy.grade_feedback.service.v2.GroupService;
import com.clcy.grade_feedback.service.v3.ExamStateService;
import com.clcy.grade_feedback.service.v3.RedisLockService;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    @Autowired
    private ExamStateService examStateService;

    @Autowired
    private RedisLockService redisLockService;

    @Autowired
    private FeedBackService feedBackService;

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
    public void generateFeedBackPuzzle(int classId, String studentId, String studentName, String examName, int groupId) {
        GroupExamMetaModel examMetaModel = examService.queryGroupExamMeta(groupId, examName);
        // 1.查询考试的题目
        List<GroupExamDetailModel>puzzles = examService.queryGroupExamDetail(groupId, examName, false, false);
        // 2.抽取最后5道题目
        List<Integer> puzzlesId = IntStream.rangeClosed(puzzles.size() - 4, puzzles.size()).boxed().collect(Collectors.toList());
        // 3.生成反馈题目
        feedBackService.generateFeedBackPuzzle(classId, studentId, studentName, examName, groupId, puzzlesId, examMetaModel);
    }

    @Override
    public List<GroupExamDetailModel> queryExamDetail(int groupId, String examName, boolean containsAnswer, boolean containsAnalysis) {
        return examService.queryGroupExamDetail(groupId, examName, containsAnswer, containsAnalysis);
    }

    /**
     * 开始考试, 创建考试状态
     *
     * */
    @Override
    public SyncExamModel startExam(String studentId, GroupExamMetaModel metaModel) {
        // 获取锁, 获取锁成功后再进行下一步
        RLock lock = redisLockService.acquireLock(studentId, metaModel.getGroupId(), metaModel.getExamName());
        try {
            //0. 查询是否有已经提交的考试记录
            GroupExamStudentAnswerRecordModel record = examService.queryStudentAnswerRecord(metaModel.getGroupId(), metaModel.getExamName(), studentId);
            if (null != record) {
                // 考试已经提交, 无正在进行的考试状态
                return null;
            }
            //1. 查询是否有已经开始但是还未结束的考试状态记录
            SyncExamModel examState = examStateService.queryExamState(studentId, metaModel.getGroupId(), metaModel.getExamName());
            if (null == examState) {
                examState = examStateService.storeExamState(metaModel,
                        SyncExamModel.builder()
                                .studentId(studentId)
                                .groupId(metaModel.getGroupId())
                                .examName(metaModel.getExamName())
                                .build()
                );
            }

            //2. 计算剩余时间
            long now = new Date().getTime();
            examState.setRemainMillSeconds(examState.getEndTime().getTime() - now);

            return examState;
        } finally {
            // 释放锁
            redisLockService.releaseLock(lock);
        }
    }

    /**
     * 恢复考试记录
     * */
    @Override
    public GroupExamModel fetchExam(String studentId, GroupExamMetaModel metaModel, boolean queryDetails) {
        // 如果返回为null, 则提前停止(没有正在进行中的考试状态)
        SyncExamModel examState = startExam(studentId, metaModel);
        if (null == examState) {
            return GroupExamModel.builder()
                    .examState(null)
                    .build();
        }
        // 倒计时已经结束, 这时不返回考试
        if (examState.getRemainMillSeconds() <= 0) {
            return GroupExamModel.builder()
                    .examState(null)
                    .build();
        }

        List<GroupExamDetailModel> details = null;
        if (queryDetails) {
            details = queryExamDetail(metaModel.getGroupId(), metaModel.getExamName(), false, false);
        }
        List<SyncPuzzleModel> puzzleStates = examStateService.queryPuzzleRecords(studentId, metaModel.getGroupId(), metaModel.getExamName());
        Map<String, String> answers = Maps.newHashMap();
        List<Long> clickNextTimeList = Lists.newArrayList();

        // 默认停留在第-1题, 也就是考试注意事项的页面
        final int[] currentPuzzleIdx = {-1};
        puzzleStates.forEach(ps -> {
            answers.put(ps.getPuzzleIdx(), ps.getAnswer());
            clickNextTimeList.add(ps.getClickNextTime());
            int idx = Integer.parseInt(ps.getPuzzleIdx());
            if (idx > currentPuzzleIdx[0]) {
                currentPuzzleIdx[0] = idx;
            }
        });

        return GroupExamModel.builder()
                .details(details)
                .examState(examState)
                .answers(answers)
                .clickNextTimeList(clickNextTimeList)
                .currentPuzzleIdx(currentPuzzleIdx[0])
                .build();
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

        // 上锁
        RLock lock = redisLockService.acquireLock(model.getStudentId(), model.getGroupId(), model.getExamName());
        try {
            // 如果已经提交过
            GroupExamStudentAnswerRecordModel record = queryAnswerRecord(model.getGroupId(), model.getExamName(), model.getStudentId());
            if (null != record) {
                return false;
            }
            Boolean success = examService.addStudentAnswerRecord(
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
            if (success) {
                // 删除考试状态
                examStateService.deleteExamState(model.getStudentId(), model.getGroupId(), model.getExamName());
                // 删除题目状态
                examStateService.deletePuzzleState(model.getStudentId(), model.getGroupId(), model.getExamName());
                // 这里提交后立即产生几道反馈题目
                generateFeedBackPuzzle(model.getClassId(), model.getStudentId(), model.getStudentName(), model.getExamName(), model.getGroupId());

                return true;
            } else {
                return false;
            }
        } finally {
            // 无论走哪个 return 分支(包括"已提交"提前返回), 锁都必须在这里释放
            redisLockService.releaseLock(lock);
        }
    }

    @Override
    public List<StudentExamRecordModel> examRecords(int groupId, String examName, String studentId) {
        GroupExamMetaModel metaModel = examService.queryGroupExamMeta(groupId, examName);
        if (null == metaModel) {
            return new ArrayList<>();
        }

        // 作答记录
        GroupExamStudentAnswerRecordModel answerModel = queryAnswerRecord(groupId, examName, studentId);

        List<GroupExamDetailModel> detailModels;
        // 考试还没有结束, 不公布答案
        if (new Date().before(metaModel.getEndTime())) {
            // 考试未结束并且还未作答, 直接返回空
            if (null == answerModel) {
                return new ArrayList<>();
            }
            detailModels = queryExamDetail(groupId, examName, false, false);
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
                        .build();
                if (answerModel.getAnswers().containsKey(String.valueOf(detail.getPuzzleIdx()))) {
                    recordModel.setYourChoice(
                            answerModel.getAnswers().get(String.valueOf(detail.getPuzzleIdx()))
                    );
                    recordModel.setStatus("已作答");
                } else {
                    recordModel.setStatus("未作答");
                }
                return recordModel;
            }).collect(Collectors.toList());
        } else {
            // 考试已结束, 不管是否作答都返回答案
            detailModels = queryExamDetail(groupId, examName, true, true);
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

    @Override
    public Boolean syncPuzzleRecord(String studentId, GroupExamMetaModel metaModel, SyncPuzzleModel puzzleModel) {
        return examStateService.syncPuzzleRecord(studentId, metaModel, puzzleModel);
    }

        // 查询某个学生某次考试的正确答案的题目索引
    public List<Integer> queryExamCorrectAnswerPuzzle(int classId, String studentId, String examName, Integer groupId) {
        List<Integer> correctPuzzles = Lists.newArrayList();
        // 再查询该学生的答题记录
        GroupExamStudentAnswerRecordModel record = examService.queryStudentAnswerRecord(groupId, examName, studentId);
        // 未作答
        if (null == record) {
            return correctPuzzles;
        }

        // 已作答
        List<GroupExamDetailModel> detailModels = guavaCacheService.getGroupExamDetails(groupId, examName);
        Map<Integer, String> ansMap = detailModels.stream().collect(Collectors.toMap(GroupExamDetailModel::getPuzzleIdx, GroupExamDetailModel::getAnswer));
        // 作答正确的题目索引
        record.getAnswers().forEach((key, value) -> {
            if (value.equals(ansMap.get(Integer.valueOf(key)))) {
                correctPuzzles.add(Integer.valueOf(key));
            }   
        });

        return correctPuzzles;
    }
}
