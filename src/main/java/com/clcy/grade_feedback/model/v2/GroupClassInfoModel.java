package com.clcy.grade_feedback.model.v2;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class GroupClassInfoModel {

    @Getter
    @Setter
    private int groupId;

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
