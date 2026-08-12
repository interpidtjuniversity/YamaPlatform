package com.clcy.grade_feedback.model.v4;

import lombok.Data;

/**
 * 视频生成上游调用结果. HTTP 错误和传输异常均转换为该模型,
 * 由业务层根据 httpStatus、status 和 retryable 决定后续状态流转.
 */
@Data
public class VideoUpstreamResult {

    /** 实际 HTTP 状态码; 传输异常尚未收到响应时为 null. */
    private Integer httpStatus;

    private String scriptName;
    private String status;
    private String code;
    private String analysisInfo;
    private String errorCode;
    private String error;
    private boolean retryable;

    public boolean isSuccessful() {
        return null != httpStatus && httpStatus >= 200 && httpStatus < 300 && null == errorCode;
    }
}
