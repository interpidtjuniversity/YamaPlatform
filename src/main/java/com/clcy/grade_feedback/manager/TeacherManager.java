package com.clcy.grade_feedback.manager;

import com.clcy.grade_feedback.model.v2.ClassExamStatModel;
import com.clcy.grade_feedback.model.v2.OwnerClassModel;
import com.clcy.grade_feedback.model.v4.StudentAudioDetailModel;

import java.util.List;

public interface TeacherManager {

    boolean createClass(OwnerClassModel createModel);

    List<OwnerClassModel> queryClasses(String ownerNumber);

    /**
     * 查询某个班级下所有考试的音频提交统计.
     * @param classId 班级 id
     * @param ownerNumber 当前登录用户(用于权限校验, 必须是该班级拥有者)
     * @return 按考试开始时间倒序排列的统计列表; 无权限返回 null
     */
    List<ClassExamStatModel> queryClassExams(int classId, String ownerNumber);

    /**
     * 查询某班级某次考试每个学生的音频提交详情.
     * @param classId 班级 id
     * @param examName 考试名
     * @param ownerNumber 当前登录用户(用于权限校验)
     * @return 学生音频详情列表; 无权限返回 null
     */
    List<StudentAudioDetailModel> queryExamAudioDetail(int classId, String examName, String ownerNumber);
}
