package com.clcy.grade_feedback.service.v4;

import com.clcy.grade_feedback.model.v4.StudentVideoQueryResult;

/**
 * 学生可见视频查询服务.
 */
public interface StudentVideoService {

    StudentVideoQueryResult queryExamVideos(String studentId, Integer groupId, String examName);

    StudentVideoQueryResult queryClassVideos(String studentId, Integer classId);
}
