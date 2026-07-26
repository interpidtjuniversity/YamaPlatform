package com.clcy.grade_feedback.model.v4;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频生成请求的业务结果.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoConversationSubmitResult {

    private Boolean accepted;

    private String message;

    private VideoConversationModel record;
}
