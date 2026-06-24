package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 梯径节点提取任务状态(返回前端, 供展示进度与按钮).
 */
@Data
@Builder
public class LadderonNodesTaskStatusModel {

    // NONE / RUNNING / DONE / FAILED
    private String status;

    private Integer totalCount;

    private Integer doneCount;

    private Timestamp startedAt;

    private Timestamp finishedAt;

    private String errorMessage;

    // 前端按钮提示
    private String hint;
}
