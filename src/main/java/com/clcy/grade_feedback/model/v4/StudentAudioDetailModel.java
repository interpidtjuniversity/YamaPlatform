package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

/**
 * 班级某次考试的学生音频提交详情(返回前端).
 */
@Data
@Builder
public class StudentAudioDetailModel {

    // 班级 id
    private Integer classId;

    // 学生姓名
    private String studentName;

    // 学号
    private String studentId;

    // 音频提交数量(OSS 中 studentId_examName_ 前缀的文件条数)
    private Integer audioCount;

    // 该学生该考试的超边提取任务状态(NONE/RUNNING/DONE/FAILED)
    private String status;
}
