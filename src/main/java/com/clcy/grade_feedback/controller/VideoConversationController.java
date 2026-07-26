package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.annotation.Limit;
import com.clcy.grade_feedback.enumerate.LimitType;
import com.clcy.grade_feedback.model.ResultModel;
import com.clcy.grade_feedback.model.v4.VideoConversationModel;
import com.clcy.grade_feedback.model.v4.VideoConversationRequest;
import com.clcy.grade_feedback.model.v4.VideoConversationSubmitResult;
import com.clcy.grade_feedback.model.v4.VideoGenerateStatusModel;
import com.clcy.grade_feedback.service.v4.VideoConversationService;
import com.clcy.grade_feedback.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录用户的视频生成对话接口.
 */
@RestController
@RequestMapping("/video/api")
public class VideoConversationController {

    @Autowired
    private VideoConversationService videoConversationService;

    @GetMapping("/conversations")
    public ResultModel<List<VideoConversationModel>> conversations() {
        String userId = UserHolder.getValue().getStudentId();
        return ResultModel.CommonResult(videoConversationService.queryConversations(userId));
    }

    @GetMapping("/get_generate_status")
    public ResultModel<VideoGenerateStatusModel> getGenerateStatus(
            @RequestParam("script_name") String scriptName) {
        String userId = UserHolder.getValue().getStudentId();
        VideoGenerateStatusModel status = videoConversationService.queryGenerateStatus(userId, scriptName);
        if (null == status) {
            return ResultModel.CommonResult((VideoGenerateStatusModel) null)
                    .success(Boolean.FALSE)
                    .message("视频生成任务不存在");
        }
        return ResultModel.CommonResult(status);
    }

    @PostMapping("/conversations/generate")
    @Limit(period = 10, count = 3, limitType = LimitType.IP_METHOD,
            prefix = "VideoConversationController.generate")
    public ResultModel<VideoConversationModel> generate(@RequestBody VideoConversationRequest request) {
        String userId = UserHolder.getValue().getStudentId();
        String prompt = null == request ? null : request.getPrompt();
        Integer historyLimit = null == request ? null : request.getHistoryLimit();
        VideoConversationSubmitResult result = videoConversationService.generate(
                userId, prompt, historyLimit);
        return ResultModel.CommonResult(result.getRecord())
                .success(Boolean.TRUE.equals(result.getAccepted()))
                .message(result.getMessage());
    }
}
