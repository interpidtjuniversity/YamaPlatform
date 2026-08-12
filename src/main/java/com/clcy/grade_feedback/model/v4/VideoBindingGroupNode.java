package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Group node in a generated video binding tree.
 */
@Data
@Builder
public class VideoBindingGroupNode {

    private Integer groupId;
    private String groupName;
    private Boolean selected;
    private Boolean directlyBound;
    private List<VideoBindingExamNode> examNameInfo;
}
