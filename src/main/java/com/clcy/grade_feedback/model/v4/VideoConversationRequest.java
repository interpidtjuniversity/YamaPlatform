package com.clcy.grade_feedback.model.v4;

import lombok.Data;

/**
 * 发起一轮视频生成对话的请求.
 */
@Data
public class VideoConversationRequest {

    private String prompt;
    private Integer historyLimit;
}
