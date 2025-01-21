package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class ClassInfo {

    @Getter
    @Setter
    private int id;

    @Getter
    @Setter
    private String className;

    @Getter
    @Setter
    private String ownerNumber;
}
