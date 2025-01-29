package com.clcy.grade_feedback.controller;

import com.alibaba.fastjson.JSONArray;
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
        // 在这里先查询这个考试是否已经提交过了
        GroupExamStudentAnswerRecordModel model = studentManager.queryAnswerRecord(submitModel.getGroupId(), submitModel.getExamName(), studentId);
        if (null != model) {
            return ResultModel.CommonResult(false).message("该考试已经提交过了, 请误重复提交");
        }
        return ResultModel.CommonResult(studentManager.submitExam(submitModel));
    }

    /**
     * 查询考试记录接口, 返回某个学生某次考试的作答记录
     * */
    @ResponseBody
    @RequestMapping("/examRecords")
    public ResultModel<JSONArray> examRecords(HttpServletRequest request, HttpServletResponse response, @RequestParam("examId") String examId) {
//        String studentId = UserHolder.getValue().getStudentId();
//        // 根据studentId, examId 去查询测试表
//        // studentId, examId, puzzles_id, answers_id
//        // List<JSONObject> puzzles_id = queryPuzzles(studentId, examId);
//        // 根据puzzles_id 去查询题库
//        // puzzle_id, content, images, options, choice, hard
//        return ResultModel.CommonResult(queryPuzzles(studentId, examId));
        return null;
    }

}
