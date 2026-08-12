package com.clcy.grade_feedback.service.v4;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.clcy.grade_feedback.dao.GroupInstanceDao;
import com.clcy.grade_feedback.dao.StudentClassInfoDao;
import com.clcy.grade_feedback.dao.pg.GeneratedVideoBindingDao;
import com.clcy.grade_feedback.entity.GeneratedVideoBinding;
import com.clcy.grade_feedback.entity.GroupInstance;
import com.clcy.grade_feedback.model.v4.StudentVideoModel;
import com.clcy.grade_feedback.model.v4.StudentVideoQueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学生可见视频查询实现.
 */
@Service
public class StudentVideoServiceImpl implements StudentVideoService {

    @Autowired
    private GeneratedVideoBindingDao generatedVideoBindingDao;

    @Autowired
    private StudentClassInfoDao studentClassInfoDao;

    @Autowired
    private GroupInstanceDao groupInstanceDao;

    @Override
    public StudentVideoQueryResult queryExamVideos(String studentId, Integer groupId, String examName) {
        if (!StringUtils.hasText(studentId) || null == groupId || !StringUtils.hasText(examName)) {
            return failure("groupId和examName不能为空");
        }
        String normalizedExamName = examName.trim();
        GroupInstance instance = groupInstanceDao.queryInstanceByGroupIdAndExam(groupId, normalizedExamName);
        if (null == instance || !containsStudent(instance.getStudentsId(), studentId)) {
            return failure("无权访问该分组测试或测试不存在");
        }
        List<GeneratedVideoBinding> bindings = generatedVideoBindingDao.queryExamVisibleVideos(
                instance.getClassId(), groupId, normalizedExamName);
        return success(bindings);
    }

    @Override
    public StudentVideoQueryResult queryClassVideos(String studentId, Integer classId) {
        if (!StringUtils.hasText(studentId) || null == classId) {
            return failure("classId不能为空");
        }
        boolean belongsToClass = studentClassInfoDao.queryStudentClasses(studentId).stream()
                .anyMatch(studentClass -> classId.equals(studentClass.getClassId()));
        if (!belongsToClass) {
            return failure("无权访问该班级或班级不存在");
        }
        return success(generatedVideoBindingDao.queryClassPublicVideos(classId));
    }

    private boolean containsStudent(String studentsJson, String studentId) {
        if (!StringUtils.hasText(studentsJson)) {
            return false;
        }
        try {
            JSONArray students = JSON.parseArray(studentsJson);
            if (null == students) {
                return false;
            }
            for (Object student : students) {
                if (studentId.equals(String.valueOf(student))) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private StudentVideoQueryResult success(List<GeneratedVideoBinding> bindings) {
        List<StudentVideoModel> videos = null == bindings ? Collections.emptyList() : bindings.stream()
                .filter(binding -> StringUtils.hasText(binding.getVideoName())
                        && StringUtils.hasText(binding.getVideoUrl()))
                .map(binding -> StudentVideoModel.builder()
                        .videoName(binding.getVideoName())
                        .videoUrl(binding.getVideoUrl())
                        .build())
                .collect(Collectors.toList());
        return StudentVideoQueryResult.builder()
                .success(Boolean.TRUE)
                .message("查询成功")
                .videos(videos)
                .build();
    }

    private StudentVideoQueryResult failure(String message) {
        return StudentVideoQueryResult.builder()
                .success(Boolean.FALSE)
                .message(message)
                .videos(Collections.emptyList())
                .build();
    }
}
