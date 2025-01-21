package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class GroupExamStudentAnswerRecord {

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
    private String studentId;

    @Getter
    @Setter
    private String studentName;

    @Getter
    @Setter
    private String answers;
}
