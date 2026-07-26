package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

/**
 * 视频生成任务状态返回模型.
 */
@Data
@Builder
public class VideoGenerateStatusModel {

    private String scriptName;

    private String status;

    private String errorCode;

    private String errorMessage;
}
