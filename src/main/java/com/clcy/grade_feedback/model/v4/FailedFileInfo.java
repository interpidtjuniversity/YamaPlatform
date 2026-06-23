package com.clcy.grade_feedback.model.v4;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 失败的音频文件信息(存入 audio_transcript_task.fail_list 的 JSON 数组元素).
 * 重试时只需这些字段即可重新生成签名 URL 并提交识别任务.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedFileInfo {

    private Integer studentId;

    private Integer groupId;

    private Integer puzzleIdx;

    private String fileName;
}
