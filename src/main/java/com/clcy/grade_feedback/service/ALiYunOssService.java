package com.clcy.grade_feedback.service;

import java.io.InputStream;
import java.util.List;

public interface ALiYunOssService {

    String upload(String id, InputStream data);

    String gerUrl(String id);

    String uploadAudio(String id, InputStream data);

    String gerAudioUrl(String id);

    /**
     * 列举音频 bucket 下指定前缀的所有对象 key(自动分页).
     * 用于按 studentId 前缀拉取该学生的全部音频文件.
     */
    List<String> listAudioKeysByPrefix(String prefix);
}
