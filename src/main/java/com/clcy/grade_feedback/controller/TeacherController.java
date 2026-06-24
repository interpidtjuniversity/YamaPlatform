package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.manager.TeacherManager;
import com.clcy.grade_feedback.model.ResultModel;
import com.clcy.grade_feedback.model.v2.ClassExamStatModel;
import com.clcy.grade_feedback.model.v2.OwnerClassModel;
import com.clcy.grade_feedback.model.v4.AudioTranscriptTaskStatusModel;
import com.clcy.grade_feedback.model.v4.HyperEdgesTaskStatusModel;
import com.clcy.grade_feedback.model.v4.LadderonNodesTaskStatusModel;
import com.clcy.grade_feedback.model.v4.StudentAudioDetailModel;
import com.clcy.grade_feedback.service.v4.AudioTranscriptTaskService;
import com.clcy.grade_feedback.service.v4.HyperEdgesTaskService;
import com.clcy.grade_feedback.service.v4.LadderonNodesTaskService;
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

    @Autowired
    private LadderonNodesTaskService ladderonNodesTaskService;

    @Autowired
    private HyperEdgesTaskService hyperEdgesTaskService;

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

    /**
     * 触发梯径节点提取任务.
     * 前置条件: examName 之前(含)的所有考试的音频转录任务必须已完成, 否则拒绝执行.
     * 前端拿到 RUNNING 后轮询 /ladderon_nodes_status 展示进度.
     */
    @ResponseBody
    @RequestMapping("/extract_ladderon_nodes")
    public ResultModel<LadderonNodesTaskStatusModel> extractLadderonNodes(HttpServletRequest request, HttpServletResponse response,
                                                                            @RequestParam("classId") int classId,
                                                                            @RequestParam("examName") String examName) {
        LadderonNodesTaskStatusModel status = ladderonNodesTaskService.triggerExtract(
                classId, examName, UserHolder.getValue().getStudentId());
        if (null == status) {
            return ResultModel.CommonResult(status).success(Boolean.FALSE).message("无权访问该班级或班级不存在");
        }
        return ResultModel.CommonResult(status);
    }

    /**
     * 查询梯径节点提取任务状态(供前端轮询).
     */
    @ResponseBody
    @RequestMapping("/ladderon_nodes_status")
    public ResultModel<LadderonNodesTaskStatusModel> ladderonNodesStatus(HttpServletRequest request, HttpServletResponse response,
                                                                          @RequestParam("classId") int classId,
                                                                          @RequestParam("examName") String examName) {
        LadderonNodesTaskStatusModel status = ladderonNodesTaskService.queryStatus(
                classId, examName, UserHolder.getValue().getStudentId());
        if (null == status) {
            return ResultModel.CommonResult(status).success(Boolean.FALSE).message("无权访问该班级或班级不存在");
        }
        return ResultModel.CommonResult(status);
    }

    /**
     * 查询某班级某次考试每个学生的音频提交详情(姓名/学号/音频数量).
     */
    @ResponseBody
    @RequestMapping("/exam_audio_detail")
    public ResultModel<List<StudentAudioDetailModel>> examAudioDetail(HttpServletRequest request, HttpServletResponse response,
                                                                       @RequestParam("classId") int classId,
                                                                       @RequestParam("examName") String examName) {
        List<StudentAudioDetailModel> result = teacherManager.queryExamAudioDetail(classId, examName, UserHolder.getValue().getStudentId());
        if (null == result) {
            return ResultModel.CommonResult(result).success(Boolean.FALSE).message("无权访问该班级或班级不存在");
        }
        return ResultModel.CommonResult(result);
    }

    /**
     * 触发某个学生某次考试的超边(推理结构)提取任务.
     */
    @ResponseBody
    @RequestMapping("/extract_student_exam_hyper_edges")
    public ResultModel<HyperEdgesTaskStatusModel> extractStudentExamHyperEdges(HttpServletRequest request, HttpServletResponse response,
                                                                                 @RequestParam("classId") int classId,
                                                                                 @RequestParam("examName") String examName,
                                                                                 @RequestParam("studentId") String studentId) {
        HyperEdgesTaskStatusModel status = hyperEdgesTaskService.triggerExtract(
                classId, examName, studentId, UserHolder.getValue().getStudentId());
        if (null == status) {
            return ResultModel.CommonResult(status).success(Boolean.FALSE).message("无权访问该班级或班级不存在");
        }
        return ResultModel.CommonResult(status);
    }

    /**
     * 查询超边提取任务状态(供前端轮询).
     */
    @ResponseBody
    @RequestMapping("/hyper_edges_status")
    public ResultModel<HyperEdgesTaskStatusModel> hyperEdgesStatus(HttpServletRequest request, HttpServletResponse response,
                                                                     @RequestParam("classId") int classId,
                                                                     @RequestParam("examName") String examName,
                                                                     @RequestParam("studentId") String studentId) {
        HyperEdgesTaskStatusModel status = hyperEdgesTaskService.queryStatus(
                classId, examName, studentId, UserHolder.getValue().getStudentId());
        if (null == status) {
            return ResultModel.CommonResult(status).success(Boolean.FALSE).message("无权访问该班级或班级不存在");
        }
        return ResultModel.CommonResult(status);
    }
}
