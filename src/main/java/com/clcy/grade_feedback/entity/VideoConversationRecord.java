package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * video_conversation_records 表实体(PostgreSQL, snnu_exam 库).
 * 一条记录表示一轮视频生成对话及其异步任务状态.
 */
@Data
@Builder
public class VideoConversationRecord {

    private Long id;
    private String userId;
    private String userPrompt;
    private String scriptName;
    private String status;
    private String code;
    private String videoUrl;
    private String errorCode;
    private String errorMessage;
    private Integer pollCount;
    private Timestamp nextPollAt;
    private String leaseToken;
    private Timestamp leaseUntil;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp finishedAt;
    private String analysisInfo;
}
