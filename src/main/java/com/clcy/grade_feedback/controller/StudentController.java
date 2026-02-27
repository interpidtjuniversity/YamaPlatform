package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.manager.StudentManager;
import com.clcy.grade_feedback.model.ResultModel;
import com.clcy.grade_feedback.model.v2.*;
import com.clcy.grade_feedback.model.v3.*;
import com.clcy.grade_feedback.utils.UserHolder;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/student/api")
public class StudentController {

    @Autowired
    private StudentManager studentManager;

    /**
     * 查询某个学生的班级列表
     * */
    @ResponseBody
    @RequestMapping("/classList")
    public ResultModel<List<StudentClassMetaModel>> classList(HttpServletRequest request, HttpServletResponse response) {
        String studentId = UserHolder.getValue().getStudentId();
        // TODO check classId is valid
        return ResultModel.CommonResult(studentManager.queryClassList(studentId));
    }

    /**
    * 查询某个学生的考试列表
    * */
    @ResponseBody
    @RequestMapping("/examList")
    public ResultModel<List<StudentExamMetaModel>> examList(HttpServletRequest request, HttpServletResponse response, @RequestParam("classId") int classId) {
        String studentId = UserHolder.getValue().getStudentId();
        // TODO check classId is valid
        return ResultModel.CommonResult(studentManager.queryExamList(classId, studentId));
    }

    /**
     * 同步考试状态
     * */
    @ResponseBody
    @RequestMapping("/syncExamState")
    public ResultModel<Boolean> syncExamPuzzles(HttpServletRequest request, HttpServletResponse response, @RequestBody SyncExamModel syncModel) {
        return null;
    }

    /**
     * 发题目接口, 返回某个学生某次考试的所有题目
     * @invalided 此接口废弃
     * */
    @ResponseBody
    @RequestMapping("/examPuzzles")
    public ResultModel<List<GroupExamDetailModel>> examPuzzles(HttpServletRequest request, HttpServletResponse response, @RequestParam("examName") String examName, @RequestParam("groupId") int groupId) {
        String studentId = UserHolder.getValue().getStudentId();
        GroupExamMetaModel examMeta = studentManager.queryExamMeta(groupId, examName);
        List<GroupExamDetailModel> details = Lists.newArrayList();

        if (null == examMeta) {
            return ResultModel.CommonResult(details).success(Boolean.FALSE).message("考试不存在");
        } else {
            if (examMeta.getStartTime().after(new Date())) {
                return ResultModel.CommonResult(details).success(Boolean.FALSE).message("考试未开始");
            }
            if (examMeta.getEndTime().before(new Date())) {
                return ResultModel.CommonResult(details).success(Boolean.FALSE).message("考试已结束");
            }
        }
        // 查询是否有已经开始的考试SyncExamModel
        // 没有的话返回全新的考试
        return ResultModel.CommonResult(studentManager.queryExamDetail(groupId, examName, false, false));
    }

    @ResponseBody
    @RequestMapping("/startExam")
    public ResultModel<GroupExamModel> startExam(HttpServletRequest request, HttpServletResponse response, @RequestParam("examName") String examName, @RequestParam("groupId") int groupId) {
        String studentId = UserHolder.getValue().getStudentId();
        GroupExamMetaModel examMeta = studentManager.queryExamMeta(groupId, examName);

        ResultModel<GroupExamModel> ans = ResultModel.CommonResult(null);
        if (null == examMeta) {
            return ans.success(Boolean.FALSE).message("考试不存在");
        } else {
            if (examMeta.getStartTime().after(new Date())) {
                return ans.success(Boolean.FALSE).message("考试未开始");
            }
            if (examMeta.getEndTime().before(new Date())) {
                return ans.success(Boolean.FALSE).message("考试已结束");
            }
        }
        // 查询是否有已经开始的考试SyncExamModel
        // 没有的话返回全新的考试
        return ResultModel.CommonResult(studentManager.fetchExam(studentId, examMeta, true));
    }

    @ResponseBody
    @RequestMapping("/syncPuzzle")
    public ResultModel<GroupExamModel> syncPuzzle(HttpServletRequest request, HttpServletResponse response,
                                                  @RequestParam("groupId") int groupId,
                                                  @RequestParam("examName") String examName,
                                                  @RequestParam("puzzleIdx") String puzzleIdx,
                                                  @RequestParam("answer") String answer) {
        String studentId = UserHolder.getValue().getStudentId();
        // 这里加缓存, 实现快速失败
        GroupExamMetaModel examMeta = studentManager.queryExamMeta(groupId, examName);

        ResultModel<GroupExamModel> ans = ResultModel.CommonResult(null);
        if (null == examMeta) {
            return ans.success(Boolean.FALSE).message("考试不存在");
        } else {
            if (examMeta.getStartTime().after(new Date())) {
                return ans.success(Boolean.FALSE).message("考试未开始");
            }
            if (examMeta.getEndTime().before(new Date())) {
                return ans.success(Boolean.FALSE).message("考试已结束");
            }
        }
        // 同步题目记录
        studentManager.syncPuzzleRecord(studentId, examMeta,
                SyncPuzzleModel.builder()
                        .puzzleIdx(puzzleIdx)
                        .answer(answer)
                        .clickNextTime(new Date().getTime())
                        .build());
        return ResultModel.CommonResult(studentManager.fetchExam(studentId, examMeta, false));
    }

    /**
     * 提交考试接口
     * */
    @ResponseBody
    @RequestMapping("/submitExam")
    public ResultModel<Boolean> submitExam(HttpServletRequest request, HttpServletResponse response, @RequestBody GroupExamStudentAnswerRecordModel submitModel) {
        String studentId = UserHolder.getValue().getStudentId();
        submitModel.setStudentId(studentId);

        studentManager.submitExam(submitModel);
        // 这里提交后立即产生几道反馈题目
        studentManager.generateFeedBackPuzzle(submitModel.getClassId(), studentId, submitModel.getStudentName(), submitModel.getExamName(), submitModel.getGroupId());

        return ResultModel.CommonResult(true);
    }

    /**
     * 查询考试记录接口, 返回某个学生某次考试的作答记录或者是未作答
     * */
    @ResponseBody
    @RequestMapping("/examRecords")
    public ResultModel<List<StudentExamRecordModel>> examRecords(HttpServletRequest request, HttpServletResponse response, @RequestParam("examName") String examName, @RequestParam("groupId") int groupId) {
        String studentId = UserHolder.getValue().getStudentId();
        return ResultModel.CommonResult(studentManager.examRecords(groupId, examName, studentId));
    }

    /**
     * 考试心跳检测
     * */
    @ResponseBody
    @RequestMapping("/keepalive")
    public ResultModel<Boolean> keepalive(HttpServletRequest request, HttpServletResponse response) {
        return ResultModel.CommonResult(Boolean.TRUE);
    }

}
