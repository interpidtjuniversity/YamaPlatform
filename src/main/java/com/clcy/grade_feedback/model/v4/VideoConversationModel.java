package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 视频生成对话记录的前端返回模型.
 */
@Data
@Builder
public class VideoConversationModel {

    private Long id;
    private String userPrompt;
    private String scriptName;
    private String code;
    private String videoUrl;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp finishedAt;
}
