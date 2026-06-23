package com.clcy.grade_feedback.service.v4;

import com.clcy.grade_feedback.model.v4.TranscriptPollResult;

/**
 * 阿里云 NLS 录音文件识别服务.
 * 通过阿里云 filetrans API 对 OSS 上的音频文件进行语音转文字.
 */
public interface AliyunTranscriptsService {

    /**
     * 根据语音文件全路径 URL 进行语音识别, 返回识别结果文本.
     * 内部完成"提交任务 + 轮询结果"全流程, 设置合理超时(最长等待 5 分钟).
     * 识别结果为空或识别失败时返回空字符串.
     *
     * @param fileLink 音频文件的完整可访问 URL
     * @return 识别结果文本; 为空或失败时返回 ""
     */
    String recognizeAudio(String fileLink);

    /**
     * 提交录音文件识别任务, 立即返回任务 ID(不等待结果).
     * 适用于异步场景: 调用方拿到 taskId 后自行存储, 后续可重复调用 recognizeAudio 无意义,
     * 应配合内部轮询逻辑使用. 当前仅暴露提交能力.
     *
     * @param fileLink 音频文件的完整可访问 URL
     * @return 任务 ID; 提交失败返回 null
     */
    String submitTask(String fileLink);

    /**
     * 单次轮询任务状态(不阻塞 sleep). 用于批量识别时对所有任务做轮转轮询,
     * 让多个任务在阿里云侧并行执行, 比串行 recognizeAudio 高效得多.
     *
     * @param taskId 任务 ID
     * @return 轮询结果: completed=true 表示到达终态(看 text 是否为空判断成功/失败), completed=false 表示仍在运行
     */
    TranscriptPollResult pollTaskOnce(String taskId);
}
