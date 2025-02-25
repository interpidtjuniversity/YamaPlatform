package com.clcy.grade_feedback.model.v2;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GroupExamStudentAnswerRecordModel {

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
    private Map<String, String> answers;

    @Getter
    @Setter
    private List<Long> clickNextTimeList;
}
