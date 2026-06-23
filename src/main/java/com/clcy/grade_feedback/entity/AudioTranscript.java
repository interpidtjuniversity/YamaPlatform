package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * audio_transcripts 表实体(存储于 PostgreSQL 的 snnu_exam 库)
 */
@Data
@Builder
public class AudioTranscript {

    @Getter
    @Setter
    private Integer id;

    @Getter
    @Setter
    private Integer classId;

    @Getter
    @Setter
    private Integer studentId;

    @Getter
    @Setter
    private Integer groupId;

    @Getter
    @Setter
    private String examName;

    @Getter
    @Setter
    private Integer puzzleIdx;

    @Getter
    @Setter
    private String transcriptText;

    @Getter
    @Setter
    private Timestamp timestamp;
}
