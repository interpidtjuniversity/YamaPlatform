package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

/**
 * Exam node in a generated video binding tree.
 */
@Data
@Builder
public class VideoBindingExamNode {

    private String examName;
    private Boolean selected;
}
