package com.clcy.grade_feedback.service.v4;

import com.clcy.grade_feedback.model.v4.GeneratedVideoBindingQueryResult;
import com.clcy.grade_feedback.model.v4.GeneratedVideoBindingRequest;
import com.clcy.grade_feedback.model.v4.GeneratedVideoBindingSaveResult;

/**
 * 生成视频的班级、分组和测试绑定服务.
 */
public interface GeneratedVideoBindingService {

    GeneratedVideoBindingSaveResult replaceBindings(String ownerNumber, GeneratedVideoBindingRequest request);

    GeneratedVideoBindingQueryResult queryBindingTree(String ownerNumber, String scriptName);
}
