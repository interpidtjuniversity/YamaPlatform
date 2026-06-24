package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * student_exam_hyper_edges_task 表实体.
 * 某个学生某次考试的超边提取任务状态(per student+exam).
 */
@Data
@Builder
public class StudentExamHyperEdgesTask {

    private Integer id;
    private Integer classId;
    private Integer studentId;
    private String examName;
    // RUNNING / DONE / FAILED
    private String status;
    private Integer totalCount;
    private Integer doneCount;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private String errorMessage;
}
