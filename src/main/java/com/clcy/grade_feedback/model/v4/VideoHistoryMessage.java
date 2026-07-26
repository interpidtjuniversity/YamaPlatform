package com.clcy.grade_feedback.model.v4;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送给视频生成上游的历史对话消息.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoHistoryMessage {

    private String role;
    private String content;
}
