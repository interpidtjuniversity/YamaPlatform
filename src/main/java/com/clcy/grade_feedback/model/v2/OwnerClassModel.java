package com.clcy.grade_feedback.model.v2;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Builder
public class OwnerClassModel {

    @Getter
    @Setter
    private int classId;

    @Getter
    @Setter
    private String className;

    @Getter
    @Setter
    private String ownerNumber;

    @Getter
    @Setter
    private List<GroupClassInfoModel> groupClassInfoModel;

    @Getter
    @Setter
    private StudentClassInfoModel studentClassInfoModel;
}
