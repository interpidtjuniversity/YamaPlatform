package com.clcy.grade_feedback.service;

import java.io.InputStream;

public interface ALiYunOssService {

    String upload(String id, InputStream data);

    String gerUrl(String id);
}
