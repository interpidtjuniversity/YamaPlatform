package com.clcy.grade_feedback.service.v4;

import com.clcy.grade_feedback.model.v4.VideoConversationModel;
import com.clcy.grade_feedback.model.v4.VideoConversationSubmitResult;
import com.clcy.grade_feedback.model.v4.VideoGenerateStatusModel;

import java.util.List;

/**
 * 登录用户的视频生成对话服务.
 */
public interface VideoConversationService {

    List<VideoConversationModel> queryConversations(String userId);

    VideoConversationSubmitResult generate(String userId, String prompt, Integer historyLimit);

    VideoGenerateStatusModel queryGenerateStatus(String userId, String scriptName);
}
