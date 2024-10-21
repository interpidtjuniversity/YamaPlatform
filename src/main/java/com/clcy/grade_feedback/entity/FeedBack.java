package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Data
@Builder
public class FeedBack {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private String studentId;

    @Getter
    @Setter
    private String examName;

    @Getter
    @Setter
    private String studentName;

    @Getter
    @Setter
    private String feedbackStatus;

    @Getter
    @Setter
    private String feedbackContent;

    @Getter
    @Setter
    private String feedbackImages;

    @Getter
    @Setter
    private Timestamp deadline;

    @Getter
    @Setter
    private String tag;
}
