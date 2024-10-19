package com.clcy.grade_feedback.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Builder
public class FeedBackModel {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private String studentId;

    @Getter
    @Setter
    private String studentName;

    @Getter
    @Setter
    private String examName;

    @Getter
    @Setter
    private String feedbackText;

    @Getter
    @Setter
    private List<String> images;

    @Getter
    @Setter
    private String feedbackStatus;
}
