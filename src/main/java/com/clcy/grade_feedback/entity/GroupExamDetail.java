package com.clcy.grade_feedback.entity;

//import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
//@Builder
public class GroupExamDetail {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private int classId;

    @Getter
    @Setter
    private String className;

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
    private String choices;

    @Getter
    @Setter
    private String images;

    @Getter
    @Setter
    private String answer;

    @Getter
    @Setter
    private String analysis;

    @Getter
    @Setter
    private String knowledgePoints;
}
