package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.manager.StudentManager;
import com.clcy.grade_feedback.model.ResultModel;
import com.clcy.grade_feedback.model.v2.*;
import com.clcy.grade_feedback.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/student/api")
public class StudentController {

    @Autowired
    private StudentManager studentManager;

    /**
     * 查询某个学生的班级列表
     * */
    @ResponseBody
    @RequestMapping("/classList")
    public ResultModel<List<StudentClassMetaModel>> classList(HttpServletRequest request, HttpServletResponse response) {
        String studentId = UserHolder.getValue().getStudentId();
        // TODO check classId is valid
        return ResultModel.CommonResult(studentManager.queryClassList(studentId));
    }

    /**
    * 查询某个学生的考试列表
    * */
    @ResponseBody
    @RequestMapping("/examList")
    public ResultModel<List<StudentExamMetaModel>> examList(HttpServletRequest request, HttpServletResponse response, @RequestParam("classId") int classId) {
        String studentId = UserHolder.getValue().getStudentId();
        // TODO check classId is valid
        return ResultModel.CommonResult(studentManager.queryExamList(classId, studentId));
    }

    /**
     * 发题目接口, 返回某个学生某次考试的所有题目
     * */
    @ResponseBody
    @RequestMapping("/examPuzzles")
    public ResultModel<List<GroupExamDetailModel>> examPuzzles(HttpServletRequest request, HttpServletResponse response, @RequestParam("examName") String examName, @RequestParam("groupId") int groupId) {
        String studentId = UserHolder.getValue().getStudentId();
        return ResultModel.CommonResult(studentManager.queryExamDetail(groupId, examName, false));
    }

    /**
     * 提交考试接口
     * */
    @ResponseBody
    @RequestMapping("/submitExam")
    public ResultModel<Boolean> submitExam(HttpServletRequest request, HttpServletResponse response, @RequestBody GroupExamStudentAnswerRecordModel submitModel) {
        String studentId = UserHolder.getValue().getStudentId();
        submitModel.setStudentId(studentId);
        return ResultModel.CommonResult(studentManager.submitExam(submitModel));
    }

    /**
     * 查询考试记录接口, 返回某个学生某次考试的作答记录或者是未作答
     * */
    @ResponseBody
    @RequestMapping("/examRecords")
    public ResultModel<List<StudentExamRecordModel>> examRecords(HttpServletRequest request, HttpServletResponse response, @RequestParam("examName") String examName, @RequestParam("groupId") int groupId) {
        String studentId = UserHolder.getValue().getStudentId();
        return ResultModel.CommonResult(studentManager.examRecords(groupId, examName, studentId));
    }

}
