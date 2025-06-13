package com.clcy.grade_feedback.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class FeedBackPuzzleModel {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private int classId;

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
}
