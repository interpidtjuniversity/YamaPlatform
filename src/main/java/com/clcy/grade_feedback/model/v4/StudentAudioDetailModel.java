package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

/**
 * 班级某次考试的学生音频提交详情(返回前端).
 */
@Data
@Builder
public class StudentAudioDetailModel {

    // 学生姓名
    private String studentName;

    // 学号
    private String studentId;

    // 音频提交数量(OSS 中 studentId_examName_ 前缀的文件条数)
    private Integer audioCount;
}
