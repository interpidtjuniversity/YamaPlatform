package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Class node in a generated video binding tree.
 */
@Data
@Builder
public class VideoBindingClassNode {

    private Integer classId;
    private String className;
    private Boolean selected;
    private Boolean directlyBound;
    private List<VideoBindingGroupNode> groupInfo;
}
