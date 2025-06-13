package com.clcy.grade_feedback.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.clcy.grade_feedback.dao.*;
import com.clcy.grade_feedback.entity.*;
import com.clcy.grade_feedback.model.*;
import com.clcy.grade_feedback.model.v2.GroupExamDetailModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FeedBackServiceImpl implements FeedBackService{

    @Resource
    private FeedBackDao feedBackDao;

    @Resource
    private StudentClassInfoDao studentClassInfoDao;

    @Resource
    private GroupInstanceDao groupInstanceDao;

    @Resource
    private GroupClassInfoDao groupClassInfoDao;

    @Resource
    private GroupExamDetailDao groupExamDetailDao;

    @Resource
    private FeedBackPuzzleDao feedBackPuzzleDao;

    @Override
    public List<FeedBackModel> queryFeedBackListByStudentId(String studentId) {
        List<FeedBack> feedBacks = feedBackDao.queryByStudentId(studentId);

        return feedBacks.stream().map(feedBack -> FeedBackModel.builder()
                .studentId(feedBack.getStudentId())
                .studentName(feedBack.getStudentName())
                .examName(feedBack.getExamName())
                .id(feedBack.getId())
                .feedbackStatus(feedBack.getFeedbackStatus())
                .deadline(feedBack.getDeadline().toString())
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

    @Override
    public List<GroupExamDetailModel> queryExamNeedFeedBackPuzzles(String studentId, String examName) {
        // 先默认一个学生只能加入一个班级(反馈页面可能需要也加一个班级列表)
        int classId = studentClassInfoDao.queryStudentClasses(studentId).get(0).getClassId();
        Integer groupId = getStudentExamGroup(classId, studentId, examName);
        List<GroupExamDetailModel> ans = new ArrayList<>();
        if (null != groupId) {
            // 查询到这次到考试试题
            List<GroupExamDetail> examDetails = groupExamDetailDao.queryGroupExamDetailByIdAndName(groupId, examName, false, false);
            // 查询需要反馈到题目列表
            List<FeedBackPuzzle> needFeedBackPuzzles = feedBackPuzzleDao.queryByStudentId(studentId, groupId, examName);
            List<Integer> ids = needFeedBackPuzzles.stream().map(FeedBackPuzzle::getPuzzleIdx).collect(Collectors.toList());
            Map<Integer, String> audioUrls = needFeedBackPuzzles.stream().collect(Collectors.toMap(FeedBackPuzzle::getPuzzleIdx, FeedBackPuzzle::getFeedBackAudio));
            // 本来可以直接排序后直接get的
            examDetails.forEach(detail -> {
                if (ids.contains(detail.getPuzzleIdx())) {
                    Map<String, Object> extInfo = new HashMap<>(1, 1.f);
                    extInfo.put("audioUrl", audioUrls.get(detail.getPuzzleIdx()));
                    ans.add(GroupExamDetailModel.builder()
                                    .content(detail.getContent())
                                    .images(JSON.parseArray(detail.getImages(), String.class))
                                    .puzzleIdx(detail.getPuzzleIdx())
                                    .choices(JSON.parseArray(detail.getChoices(), String.class))
                                    .extInfo(extInfo)
                            .build());
                }
            });
        }
        return ans;
    }

    @Override
    public boolean feedBackPuzzle(FeedBackPuzzleModel feedBackPuzzleModel) {
        int classId = studentClassInfoDao.queryStudentClasses(feedBackPuzzleModel.getStudentId()).get(0).getClassId();
        Integer groupId = getStudentExamGroup(classId, feedBackPuzzleModel.getStudentId(), feedBackPuzzleModel.getExamName());
        if (null == groupId) {
            return false;
        }
        // 1.判断是否需要反馈
        FeedBackPuzzle feedBackPuzzle = feedBackPuzzleDao.queryOne(feedBackPuzzleModel.getStudentId(), groupId, feedBackPuzzleModel.getExamName(), feedBackPuzzleModel.getPuzzleIdx());
        if (null == feedBackPuzzle || "已反馈".equals(feedBackPuzzle.getFeedbackStatus())) {
            return false;
        }
        // 2.可反馈状态才能反馈
        return feedBackPuzzleDao.feedBackPuzzle(
                FeedBackPuzzle.builder()
                        .feedBackAudio(feedBackPuzzleModel.getFeedBackAudio())
                        .feedbackStatus("已反馈")
                        .id(feedBackPuzzle.getId())
                        .studentId(feedBackPuzzle.getStudentId())
                        .groupId(groupId)
                        .examName(feedBackPuzzle.getExamName())
                        .puzzleIdx(feedBackPuzzle.getPuzzleIdx())
                        .build()
        );
    }

    private Integer getStudentExamGroup(int classId, String studentId, String examName) {
        List<GroupClassInfo> groups = groupClassInfoDao.queryGroupsInClass(classId);
        if (groups.size() == 0) {
            return null;
        }
        for (GroupClassInfo group : groups) {
            GroupInstance instance = groupInstanceDao.queryInstanceByGroupIdAndExam(group.getId(), examName);
            if (null != instance) {
                List<String> studentIds = JSON.parseArray(instance.getStudentsId(), String.class);
                if (studentIds.contains(studentId)) {
                    return group.getId();
                }
            }
        }
        return null;
    }

    @Override
    public boolean cancelFeedBackPuzzle(FeedBackPuzzleModel feedBackPuzzleModel) {
        int classId = studentClassInfoDao.queryStudentClasses(feedBackPuzzleModel.getStudentId()).get(0).getClassId();
        Integer groupId = getStudentExamGroup(classId, feedBackPuzzleModel.getStudentId(), feedBackPuzzleModel.getExamName());
        if (null == groupId) {
            return false;
        }
        // 1.判断是否需要反馈
        FeedBackPuzzle feedBackPuzzle = feedBackPuzzleDao.queryOne(feedBackPuzzleModel.getStudentId(), groupId, feedBackPuzzleModel.getExamName(), feedBackPuzzleModel.getPuzzleIdx());
        if (null == feedBackPuzzle || "未反馈".equals(feedBackPuzzle.getFeedbackStatus())) {
            return false;
        }
        // 2.可反馈状态才能反馈
        return feedBackPuzzleDao.feedBackPuzzle(
                FeedBackPuzzle.builder()
                        .feedBackAudio("")
                        .feedbackStatus("未反馈")
                        .id(feedBackPuzzle.getId())
                        .studentId(feedBackPuzzle.getStudentId())
                        .groupId(groupId)
                        .examName(feedBackPuzzle.getExamName())
                        .puzzleIdx(feedBackPuzzle.getPuzzleIdx())
                        .build()
        );    }

}
