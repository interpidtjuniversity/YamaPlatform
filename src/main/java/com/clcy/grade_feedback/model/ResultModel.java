package com.clcy.grade_feedback.model;

import lombok.*;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultModel<T> {

    // 业务主数据
    @Getter
    @Setter
    private T data;

    // 业务扩展数据
    @Getter
    @Setter
    private Map<String, Object> extInfo;

    @Getter
    @Setter
    private String message;

    @Getter
    @Setter
    private boolean success = Boolean.FALSE;

    public static ResultModel<Object> LIMITED() {
        return ResultModel.builder().message("限流: 系统繁忙,请稍后再试").build();
    }

    public static ResultModel<Object> FAILURE() {
        return ResultModel.builder().message("系统错误,请稍后再试").build();
    }

    public static <T> ResultModel<T> CommonResult(T result) {
        ResultModel<T> resultModel = new ResultModel<>();
        resultModel.setData(result);
        resultModel.setSuccess(Boolean.TRUE);
        return resultModel;
    }

    public ResultModel<T> message(String message) {
        this.setMessage(message);
        return this;
    }

    public ResultModel<T> success(Boolean success) {
        this.setSuccess(success);
        return this;
    }

    public ResultModel<T> extInfo(Map<String, Object> extInfo) {
        this.setExtInfo(extInfo);
        return this;
    }
}
