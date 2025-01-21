package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class GroupClassInfo {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private String groupName;

    @Getter
    @Setter
    private int classId;

    @Getter
    @Setter
    private String className;

    @Getter
    @Setter
    private String groupingStrategy;

    @Getter
    @Setter
    private String groupingInfo;
}
