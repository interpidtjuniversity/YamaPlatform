package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * audio_transcript_task 表实体(PostgreSQL, snnu_exam 库).
 * 记录某个班级某次考试的音频转录任务状态.
 */
@Data
@Builder
public class AudioTranscriptTask {

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
    // 失败文件列表(JSON 数组字符串), 重试时只跑这些文件; 全部成功后为 null
    private String failList;
}
