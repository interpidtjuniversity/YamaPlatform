package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 音频转录任务状态(返回前端, 供展示进度与按钮).
 */
@Data
@Builder
public class AudioTranscriptTaskStatusModel {

    // NONE / RUNNING / DONE / FAILED
    private String status;

    private Integer totalCount;

    private Integer doneCount;

    private Timestamp startedAt;

    private Timestamp finishedAt;

    private String errorMessage;

    // 前端按钮提示: "开始识别" / "已完成, 重新识别" 等
    private String hint;
}
