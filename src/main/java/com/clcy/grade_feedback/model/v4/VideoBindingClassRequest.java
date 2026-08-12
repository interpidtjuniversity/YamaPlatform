package com.clcy.grade_feedback.model.v4;

import lombok.Data;

import java.util.List;

/**
 * Class-level generated video binding request.
 */
@Data
public class VideoBindingClassRequest {

    private Integer classId;
    private List<VideoBindingGroupRequest> groupInfo;
}
