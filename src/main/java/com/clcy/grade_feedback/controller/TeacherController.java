package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.manager.TeacherManager;
import com.clcy.grade_feedback.model.ResultModel;
import com.clcy.grade_feedback.model.v2.OwnerClassModel;
import com.clcy.grade_feedback.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/teacher/api")
public class TeacherController {

    @Autowired
    private TeacherManager teacherManager;

    /**
     * 创建班级
     * */
    @ResponseBody
    @RequestMapping("/createClass")
    public ResultModel<Boolean> createClass(HttpServletRequest request, HttpServletResponse response, @RequestBody OwnerClassModel model) {
        model.setOwnerNumber(UserHolder.getValue().getStudentId());
        return ResultModel.CommonResult(teacherManager.createClass(model));
    }

    /**
     * 查询班级
     * */
    @ResponseBody
    @RequestMapping("/queryClasses")
    public ResultModel<List<OwnerClassModel>> queryClasses(HttpServletRequest request, HttpServletResponse response) {
        return ResultModel.CommonResult(teacherManager.queryClasses(UserHolder.getValue().getStudentId()));
    }
}
