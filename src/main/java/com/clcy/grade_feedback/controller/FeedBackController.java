package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.annotation.Limit;
import com.clcy.grade_feedback.enumerate.LimitType;
import com.clcy.grade_feedback.model.FeedBackModel;
import com.clcy.grade_feedback.model.FeedBackPuzzleModel;
import com.clcy.grade_feedback.model.ResultModel;
import com.clcy.grade_feedback.service.ALiYunOssService;
import com.clcy.grade_feedback.service.FeedBackService;
import com.clcy.grade_feedback.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/grade_feedback/api")
public class FeedBackController {

    @Autowired
    private ALiYunOssService aLiYunOssService;

    @Autowired
    private FeedBackService feedBackService;

    @ResponseBody
    @RequestMapping("/feedBackList")
    @Limit(period = 10, count = 5, limitType = LimitType.IP_METHOD, prefix = "FeedBackController.feedBackList")
    public ResultModel<List<FeedBackModel>> feedBackList(HttpServletRequest request, HttpServletResponse response) {
        return ResultModel.CommonResult(
                feedBackService.queryFeedBackListByStudentId(UserHolder.getValue().getStudentId()));
    }

    @ResponseBody
    @RequestMapping("/feedback")
    @Limit(period = 10, count = 5, limitType = LimitType.IP_METHOD, prefix = "FeedBackController.feedback")
    public ResultModel<Boolean> feedback(HttpServletRequest request, HttpServletResponse response, @RequestBody FeedBackModel feedBackModel) {
        return ResultModel.CommonResult(feedBackService.feedBack(feedBackModel));
    }

    @ResponseBody
    @RequestMapping("/cancelFeedBack")
    @Limit(period = 10, count = 5, limitType = LimitType.IP_METHOD, prefix = "FeedBackController.cancelFeedBack")
    public ResultModel<Boolean> cancelFeedBack(HttpServletRequest request, HttpServletResponse response, @RequestBody FeedBackModel feedBackModel) {
        return ResultModel.CommonResult(feedBackService.cancelFeedBack(feedBackModel));
    }

    @ResponseBody
    @RequestMapping("/upload")
    @Limit(period = 10, count = 10, limitType = LimitType.IP_METHOD, prefix = "FeedBackController.upload")
    public ResultModel<Object> upload(HttpServletRequest request, HttpServletResponse response, @RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            String fileId = UserHolder.getValue().getStudentId() + System.currentTimeMillis() + file.getOriginalFilename();
            return ResultModel.CommonResult(aLiYunOssService.upload(fileId, new ByteArrayInputStream(data)));
        } catch (IOException e) {
            return ResultModel.FAILURE();
        }
    }

    @ResponseBody
    @RequestMapping("/uploadAudio")
    @Limit(period = 10, count = 10, limitType = LimitType.IP_METHOD, prefix = "FeedBackController.uploadAudio")
    public ResultModel<Object> uploadAudio(HttpServletRequest request, HttpServletResponse response, @RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            String fileId = UserHolder.getValue().getStudentId() + System.currentTimeMillis() + file.getOriginalFilename();
            return ResultModel.CommonResult(aLiYunOssService.uploadAudio(fileId, new ByteArrayInputStream(data)));
        } catch (IOException e) {
            return ResultModel.FAILURE();
        }
    }


    @ResponseBody
    @RequestMapping("/needFeedBackPuzzles")
    @Limit(period = 10, count = 5, limitType = LimitType.IP_METHOD, prefix = "FeedBackController.needFeedBackPuzzles")
    public ResultModel<Object> needFeedBackPuzzles(HttpServletRequest request, HttpServletResponse response, @RequestParam("examName") String examName) {
        String studentId = UserHolder.getValue().getStudentId();
        return ResultModel.CommonResult(feedBackService.queryExamNeedFeedBackPuzzles(studentId, examName));
    }

    @ResponseBody
    @RequestMapping("/feedbackPuzzle")
    @Limit(period = 10, count = 5, limitType = LimitType.IP_METHOD, prefix = "FeedBackController.feedbackPuzzle")
    public ResultModel<Boolean> feedbackPuzzle(HttpServletRequest request, HttpServletResponse response, @RequestBody FeedBackPuzzleModel feedBackPuzzleModel) {
        String studentId = UserHolder.getValue().getStudentId();
        feedBackPuzzleModel.setStudentId(studentId);
        return ResultModel.CommonResult(feedBackService.feedBackPuzzle(feedBackPuzzleModel));
    }

    @ResponseBody
    @RequestMapping("/cancelFeedBackPuzzle")
    @Limit(period = 10, count = 5, limitType = LimitType.IP_METHOD, prefix = "FeedBackController.cancelFeedBackPuzzle")
    public ResultModel<Boolean> cancelFeedBackPuzzle(HttpServletRequest request, HttpServletResponse response, @RequestBody FeedBackPuzzleModel feedBackPuzzleModel) {
        String studentId = UserHolder.getValue().getStudentId();
        feedBackPuzzleModel.setStudentId(studentId);
        return ResultModel.CommonResult(feedBackService.cancelFeedBackPuzzle(feedBackPuzzleModel));
    }
}
