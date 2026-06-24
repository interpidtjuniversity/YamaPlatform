package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * student_ladderon_nodes_task 表实体(PostgreSQL, snnu_exam 库).
 * 记录某个班级某次考试的梯径节点提取任务状态.
 */
@Data
@Builder
public class StudentLadderonNodesTask {

    private Integer id;
    private Integer classId;
    private String examName;
    // RUNNING / DONE / FAILED
    private String status;
    private Integer totalCount;
    private Integer doneCount;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private String errorMessage;
}
