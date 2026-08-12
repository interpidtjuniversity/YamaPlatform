package com.clcy.grade_feedback.model.v4;

import lombok.Data;

import java.util.List;

/**
 * Request to bind a generated video to classes, groups, and exams.
 */
@Data
public class GeneratedVideoBindingRequest {

    private String scriptName;
    private String videoName;
    private String videoUrl;
    private List<VideoBindingClassRequest> classInfo;
}
