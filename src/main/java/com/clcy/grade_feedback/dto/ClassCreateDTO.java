package com.clcy.grade_feedback.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ClassCreateDTO {

    @Getter
    @Setter
    private String className;

    @Getter
    @Setter
    private String ownerNumber;

    @Getter
    @Setter
    private Map<String, String> students;

    @Getter
    @Setter
    private List<Map<String, String>> groups;
}
