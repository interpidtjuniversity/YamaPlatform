package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * student_ladderon_nodes 表实体(PostgreSQL, snnu_exam 库).
 * 存储从学生音频转录文本中提取并经 LLM 过滤后的物理知识节点.
 */
@Data
@Builder
public class StudentLadderonNode {

    private Integer id;
    private Integer classId;
    private Integer studentId;
    private Integer groupId;
    private String examName;
    private String nodeText;
    // exam_name 的 start_time(来自 group_exam_meta)
    private Timestamp timestamp;
}
