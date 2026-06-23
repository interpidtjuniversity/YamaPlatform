package com.clcy.grade_feedback.model.v2;

import com.clcy.grade_feedback.model.v4.AudioTranscriptTaskStatusModel;
import lombok.*;

import java.sql.Timestamp;

/**
 * 班级某次考试的音频提交统计
 */
@Data
@Builder
public class ClassExamStatModel {

    // 测试名称
    @Getter
    @Setter
    private String examName;

    // 音频提交人数(去重后的 studentId 数量)
    @Getter
    @Setter
    private Integer submitStudentCount;

    // 音频提交数量(文件条数)
    @Getter
    @Setter
    private Integer submitCount;

    // 测试开始时间
    @Getter
    @Setter
    private Timestamp startTime;

    // 测试结束时间
    @Getter
    @Setter
    private Timestamp endTime;

    // 该考试对应的音频识别任务状态(与 transcript_status 接口返回一致)
    @Getter
    @Setter
    private AudioTranscriptTaskStatusModel transcriptStatus;
}
