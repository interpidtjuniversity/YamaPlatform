package com.clcy.grade_feedback.model.v4;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 学生视频查询业务结果.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentVideoQueryResult {

    private Boolean success;

    private String message;

    private List<StudentVideoModel> videos;
}
