package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Generated video bindings represented as a class/group/exam tree.
 */
@Data
@Builder
public class GeneratedVideoBindingTreeModel {

    private String scriptName;
    private String videoName;
    private String videoUrl;
    private List<VideoBindingClassNode> classInfo;
}
