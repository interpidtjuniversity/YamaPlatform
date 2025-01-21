package com.clcy.grade_feedback.controller;

import com.alibaba.fastjson.JSONArray;
import com.clcy.grade_feedback.model.ResultModel;
import com.clcy.grade_feedback.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/exam/api")
public class PublishExamController {

    private static final JSONArray puzzles = JSONArray.parseArray(
            "[{\"puzzle_id\":\"1\",\"content\":\"1.麦克斯韦速率分布函数\\\\(f(\\\\nu)\\\\)的物理意义是（A）\",\"images\":[],\"choices\":[\"它是气体分子处于\\\\(\\\\nu\\\\)附近单位速率区间的概率\",\"它是气体分子处于\\\\(\\\\nu\\\\)附近的频率\",\"它是气体分子处于\\\\(\\\\nu \\\\sim \\\\nu + d\\\\nu\\\\)速率区间的分子数\",\"它是气体分子处于\\\\(\\\\nu \\\\sim \\\\nu + d\\\\nu\\\\)速率区间的相对分子数\"],\"answer\":\"A\",\"hard\":\"1\"},{\"puzzle_id\":\"2\",\"content\":\"2.麦克斯韦速率分布函数\\\\(f(\\\\nu)\\\\)的物理意义是（A）\",\"images\":[],\"choices\":[\"它是气体分子处于\\\\(\\\\nu\\\\)附近单位速率区间的概率\",\"它是气体分子处于\\\\(\\\\nu\\\\)附近的频率\",\"它是气体分子处于\\\\(\\\\nu \\\\sim \\\\nu + d\\\\nu\\\\)速率区间的分子数\",\"它是气体分子处于\\\\(\\\\nu \\\\sim \\\\nu + d\\\\nu\\\\)速率区间的相对分子数\"],\"answer\":\"A\",\"hard\":\"1\"},{\"puzzle_id\":\"3\",\"content\":\"3.麦克斯韦速率分布函数\\\\(f(\\\\nu)\\\\)的物理意义是（A）\",\"images\":[],\"choices\":[\"它是气体分子处于\\\\(\\\\nu\\\\)附近单位速率区间的概率\",\"它是气体分子处于\\\\(\\\\nu\\\\)附近的频率\",\"它是气体分子处于\\\\(\\\\nu \\\\sim \\\\nu + d\\\\nu\\\\)速率区间的分子数\",\"它是气体分子处于\\\\(\\\\nu \\\\sim \\\\nu + d\\\\nu\\\\)速率区间的相对分子数\"],\"answer\":\"A\",\"hard\":\"1\"}]");


    /**
    * 查询某个学生的考试列表
    * */
    @ResponseBody
    @RequestMapping("/examList")
    public ResultModel<JSONArray> examList(HttpServletRequest request, HttpServletResponse response, @RequestParam("examId") String examId) {
        String studentId = UserHolder.getValue().getStudentId();
        // 根据studentId, examId 去查询测试表
        // studentId, examId, puzzles_id, answers_id
        // List<JSONObject> puzzles_id = queryPuzzles(studentId, examId);
        // 根据puzzles_id 去查询题库
        // puzzle_id, content, images, options, choice, hard
        return ResultModel.CommonResult(queryPuzzles(studentId, examId));
    }

    /**
     * 发题目接口, 返回某个学生某次考试的所有题目
     * */
    @ResponseBody
    @RequestMapping("/examPuzzles")
    public ResultModel<JSONArray> examPuzzles(HttpServletRequest request, HttpServletResponse response, @RequestParam("examId") String examId) {
        String studentId = UserHolder.getValue().getStudentId();
        // 根据studentId, examId 去查询测试表
        // studentId, examId, puzzles_id, answers_id
        // List<JSONObject> puzzles_id = queryPuzzles(studentId, examId);
        // 根据puzzles_id 去查询题库
        // puzzle_id, content, images, options, choice, hard
        return ResultModel.CommonResult(queryPuzzles(studentId, examId));
    }

    /**
     * 查询考试记录接口, 返回某个学生某次考试的作答记录
     * */
    @ResponseBody
    @RequestMapping("/examRecords")
    public ResultModel<JSONArray> examRecords(HttpServletRequest request, HttpServletResponse response, @RequestParam("examId") String examId) {
        String studentId = UserHolder.getValue().getStudentId();
        // 根据studentId, examId 去查询测试表
        // studentId, examId, puzzles_id, answers_id
        // List<JSONObject> puzzles_id = queryPuzzles(studentId, examId);
        // 根据puzzles_id 去查询题库
        // puzzle_id, content, images, options, choice, hard
        return ResultModel.CommonResult(queryPuzzles(studentId, examId));
    }

    private JSONArray queryPuzzles(String studentId, String examId) {
        // List<String>puzzles_id = Lists.newArrayList("1","2","3","4","5","6");
        return puzzles;
    }
}
