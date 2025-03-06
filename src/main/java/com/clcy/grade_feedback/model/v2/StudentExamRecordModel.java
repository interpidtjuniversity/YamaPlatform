package com.clcy.grade_feedback.model.v2;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Builder
public class StudentExamRecordModel {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private int groupId;

    @Getter
    @Setter
    private String groupName;

    @Getter
    @Setter
    private String examName;

    @Getter
    @Setter
    private int puzzleIdx;

    @Getter
    @Setter
    private String content;

    @Getter
    @Setter
    private List<String> images;

    @Getter
    @Setter
    private List<String> choices;

    @Getter
    @Setter
    private String answer;

    @Getter
    @Setter
    private String yourChoice;

    // 该道题是否作答
    @Getter
    @Setter
    private String status;

    @Getter
    @Setter
    private String analysis;

    @Getter
    @Setter
    private String knowledgePoints;
}
