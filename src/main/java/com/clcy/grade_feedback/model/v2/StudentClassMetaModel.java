package com.clcy.grade_feedback.model.v2;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Data
@Builder
public class StudentClassMetaModel {

    @Getter
    @Setter
    private String studentId;

    @Getter
    @Setter
    private String studentName;

    @Getter
    @Setter
    private int classId;

    @Getter
    @Setter
    private String className;

    @Getter
    @Setter
    private Map<String, String> students;
}
