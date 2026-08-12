package com.clcy.grade_feedback.entity;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/**
 * A generated video's binding to a class, group, or exam.
 */
@Data
@Builder
public class GeneratedVideoBinding {

    private Long id;
    private Long videoConversationId;
    private String ownerNumber;
    private Integer classId;
    private Integer groupId;
    private String examName;
    private String scriptName;
    private String videoName;
    private String videoUrl;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
