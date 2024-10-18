package com.clcy.grade_feedback.controller;

import com.clcy.grade_feedback.model.FeedBackModel;
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
    @RequestMapping("/needFeedBackList")
    public List<FeedBackModel> needFeedBackList(HttpServletRequest request, HttpServletResponse response) {
        return feedBackService.queryFeedBackListByStudentId(UserHolder.getValue().getStudentId());
    }

    @ResponseBody
    @RequestMapping("/feedback")
    public boolean feedback(HttpServletRequest request, HttpServletResponse response, @RequestBody FeedBackModel feedBackModel) {
        return feedBackService.feedBack(feedBackModel);
    }

    @ResponseBody
    @RequestMapping("/cancelFeedBack")
    public boolean cancelFeedBack(HttpServletRequest request, HttpServletResponse response, @RequestBody FeedBackModel feedBackModel) {
        return feedBackService.cancelFeedBack(feedBackModel);
    }

    @ResponseBody
    @RequestMapping("/upload")
    public Object upload(HttpServletRequest request, HttpServletResponse response, @RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            String fileId = UserHolder.getValue().getStudentId() + System.currentTimeMillis() + file.getOriginalFilename();
            return aLiYunOssService.upload(fileId, new ByteArrayInputStream(data));
        } catch (IOException e) {
            return null;
        }
    }
}
