package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Data
@Builder
public class FeedBackPuzzle {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private String studentId;

    @Getter
    @Setter
    private int groupId;

    @Getter
    @Setter
    private String examName;

    @Getter
    @Setter
    private int puzzleIdx;

    @Getter
    @Setter
    private String feedbackStatus;

    @Getter
    @Setter
    private String feedBackAudio;

    @Getter
    @Setter
    private Timestamp deadline;

    @Getter
    @Setter
    private String tag;
}
