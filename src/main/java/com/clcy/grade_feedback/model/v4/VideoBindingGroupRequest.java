package com.clcy.grade_feedback.model.v4;

import lombok.Data;

import java.util.List;

/**
 * Group-level generated video binding request.
 */
@Data
public class VideoBindingGroupRequest {

    private Integer groupId;
    private List<String> examNameInfo;
}
