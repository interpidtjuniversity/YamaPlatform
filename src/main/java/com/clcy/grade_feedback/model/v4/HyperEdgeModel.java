package com.clcy.grade_feedback.model.v4;

import lombok.Data;

import java.util.List;

/**
 * LLM 返回的单条超边(推理结构).
 */
@Data
public class HyperEdgeModel {

    // 输入节点列表
    private List<String> inputs;

    // 输出节点列表
    private List<String> outputs;

    // 推理类型, 如 "invoke_rule"
    private String type;

    // 置信度
    private Double confidence;
}
