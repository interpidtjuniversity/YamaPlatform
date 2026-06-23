package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.manager.TeacherManager;
import com.clcy.grade_feedback.model.ResultModel;
import com.clcy.grade_feedback.model.v2.ClassExamStatModel;
import com.clcy.grade_feedback.model.v2.OwnerClassModel;
import com.clcy.grade_feedback.model.v4.AudioTranscriptTaskStatusModel;
import com.clcy.grade_feedback.service.v4.AudioTranscriptTaskService;
import com.clcy.grade_feedback.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/teacher/api")
public class TeacherController {

    @Autowired
    private TeacherManager teacherManager;

    @Autowired
    private AudioTranscriptTaskService audioTranscriptTaskService;

    /**
     * 创建班级
     * */
    @ResponseBody
    @RequestMapping("/createClass")
    public ResultModel<Boolean> createClass(HttpServletRequest request, HttpServletResponse response, @RequestBody OwnerClassModel model) {
        model.setOwnerNumber(UserHolder.getValue().getStudentId());
        return ResultModel.CommonResult(teacherManager.createClass(model));
    }

    /**
     * 查询班级
     * */
    @ResponseBody
    @RequestMapping("/queryClasses")
    public ResultModel<List<OwnerClassModel>> queryClasses(HttpServletRequest request, HttpServletResponse response) {
        return ResultModel.CommonResult(teacherManager.queryClasses(UserHolder.getValue().getStudentId()));
    }

    /**
     * 查询某个班级下所有考试的音频提交统计.
     * 返回(测试名称, 音频提交人数, 音频提交数量, 测试开始时间, 测试结束时间), 按开始时间倒序.
     */
    @ResponseBody
    @RequestMapping("/query_class_exams")
    public ResultModel<List<ClassExamStatModel>> queryClassExams(HttpServletRequest request, HttpServletResponse response,
                                                                  @RequestParam("classId") int classId) {
        // 权限校验在 manager 内完成: 当前登录用户必须是该班级的 owner
        List<ClassExamStatModel> result = teacherManager.queryClassExams(classId, UserHolder.getValue().getStudentId());
        if (null == result) {
            return ResultModel.CommonResult(result).success(Boolean.FALSE).message("无权访问该班级或班级不存在");
        }
        return ResultModel.CommonResult(result);
    }

    /**
     * 触发某个班级某次考试的全员音频转录任务.
     * 若该任务已在运行则拒绝重复提交(返回当前 RUNNING 状态), 否则后台异步启动并立即返回 RUNNING.
     * 前端拿到 RUNNING 后轮询 /transcript_status 展示进度.
     */
    @ResponseBody
    @RequestMapping("/recognize_audio")
    public ResultModel<AudioTranscriptTaskStatusModel> recognizeAudio(HttpServletRequest request, HttpServletResponse response,
                                                                      @RequestParam("classId") int classId,
                                                                      @RequestParam("examName") String examName) {
        AudioTranscriptTaskStatusModel status = audioTranscriptTaskService.triggerTranscript(
                classId, examName, UserHolder.getValue().getStudentId());
        if (null == status) {
            return ResultModel.CommonResult(status).success(Boolean.FALSE).message("无权访问该班级或班级不存在");
        }
        return ResultModel.CommonResult(status);
    }

    /**
     * 查询音频转录任务状态(供前端轮询, 展示进度与按钮).
     */
    @ResponseBody
    @RequestMapping("/transcript_status")
    public ResultModel<AudioTranscriptTaskStatusModel> transcriptStatus(HttpServletRequest request, HttpServletResponse response,
                                                                         @RequestParam("classId") int classId,
                                                                         @RequestParam("examName") String examName) {
        AudioTranscriptTaskStatusModel status = audioTranscriptTaskService.queryStatus(
                classId, examName, UserHolder.getValue().getStudentId());
        if (null == status) {
            return ResultModel.CommonResult(status).success(Boolean.FALSE).message("无权访问该班级或班级不存在");
        }
        return ResultModel.CommonResult(status);
    }
}
