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

    /**
     * 该条转录对应音频的 OSS key(完整 key, 如 feed_back/{studentId}_{examName}_{puzzleIdx}_{ts}_{originName}).
     * 历史数据在修复程序(Fix)中通过重新转录回填; 稳定不过期, 需要可播放 URL 时用 ALiYunOssService.gerAudioUrl 现取.
     */
    @Getter
    @Setter
    private String audioUrl;

    /**
     * 音频来源标记:
     *   examName  -- 测试后立刻提交的音频(feedback_puzzle_table 中 tag=examName 的记录所引用)
     *   yyMMdd    -- 某日期集体补充提交的音频(feedback_puzzle_table 中 tag=yyMMdd 的记录所引用)
     *   null      -- 幽灵音频(学生上传但未最终提交, 不在 feedback_puzzle_table 中)
     */
    @Getter
    @Setter
    private String tag;

    @Getter
    @Setter
    private Timestamp timestamp;
}
