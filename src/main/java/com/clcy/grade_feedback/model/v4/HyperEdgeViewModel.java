package com.clcy.grade_feedback.model.v4;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

/**
 * 超边(推理结构)返回前端模型.
 */
@Data
@Builder
public class HyperEdgeViewModel {

    private Integer id;

    // 输入节点列表
    private List<String> inputs;

    // 输出节点列表
    private List<String> outputs;

    // 推理类型, 如 "invoke_rule"
    private String type;

    // 置信度
    private Double confidence;

    // 来源测试
    private String examName;

    // 题目序号
    private Integer puzzleIndex;

    // 考试开始时间
    private Timestamp timestamp;
}
