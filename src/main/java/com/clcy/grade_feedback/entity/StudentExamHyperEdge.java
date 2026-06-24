package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * student_exam_hyper_edges 表实体(PostgreSQL, snnu_exam 库).
 * 存储从学生音频中提取的推理结构(超边).
 */
@Data
@Builder
public class StudentExamHyperEdge {

    private Integer id;
    private Integer classId;
    private Integer studentId;
    private Integer groupId;
    private String examName;
    // JSON 数组字符串, 如 ["合外力","质量"]
    private String inputs;
    // JSON 数组字符串, 如 ["加速度"]
    private String outputs;
    private String type;
    private Double confidence;
    // 第几个音频识别出的(0-based)
    private Integer puzzleIndex;
    private Timestamp timestamp;
}
