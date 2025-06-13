package com.clcy.grade_feedback.model.v2;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GroupExamDetailModel {

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
    private List<String> choices;

    @Getter
    @Setter
    private List<String> images;

    @Getter
    @Setter
    private String answer;

    @Getter
    @Setter
    private String analysis;

    @Getter
    @Setter
    private String knowledgePoints;

    @Getter
    @Setter
    private Map<String, Object> extInfo;
}
