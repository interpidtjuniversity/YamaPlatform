package com.clcy.grade_feedback.entity;

import lombok.Data;

/**
 * Read-only flattened row for an owner's class, group, and exam hierarchy.
 */
@Data
public class OwnerVideoHierarchyRow {

    private Integer classId;

    private String className;

    private Integer groupId;

    private String groupName;

    private String examName;
}
