package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

/**
 * 学生可查看的视频信息.
 */
@Data
@Builder
public class StudentVideoModel {

    private String videoName;

    private String videoUrl;
}
