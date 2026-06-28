package com.clcy.grade_feedback.service.v4;

import com.clcy.grade_feedback.model.v4.AudioTranscriptTaskStatusModel;

/**
 * 音频转录任务服务: 负责对某个班级某次考试的全部学生音频做批量语音识别,
 * 并将结果写入 audio_transcripts 表. 任务状态记录在 audio_transcript_task 表中,
 * 同一 (classId, examName) 不允许重复提交(已有 RUNNING 任务时拒绝).
 */
public interface AudioTranscriptTaskService {

    /**
     * 触发转录任务.
     * 若该 (classId, examName) 已有 RUNNING 任务则拒绝, 返回当前状态;
     * 否则启动后台异步执行, 立即返回 RUNNING 状态供前端转圈.
     *
     * @param classId  班级 id
     * @param examName 考试名
     * @param ownerNumber 当前登录用户(用于权限校验)
     * @return 任务状态; 无权限返回 null
     */
    AudioTranscriptTaskStatusModel triggerTranscript(int classId, String examName, String ownerNumber);

    /**
     * 查询任务状态(供前端轮询, 展示进度/按钮).
     */
    AudioTranscriptTaskStatusModel queryStatus(int classId, String examName, String ownerNumber);

    /**
     * 查询任务状态(不校验权限). 专供已做过权限校验的内部聚合调用, 如 queryClassExams 批量填充状态.
     */
    AudioTranscriptTaskStatusModel queryStatusNoAuth(int classId, String examName);

    /**
     * 增量同步转录: 找出该班级所有学生在 OSS 上存在但尚未入库 audio_transcripts 的音频(新增/补充上传的文件),
     * 重新启动任务并只转录这部分增量文件, 落库时同样写入 audio_url 与 tag. 不删除已有转录记录.
     * 若该 (classId, examName) 已有 RUNNING 任务则拒绝, 返回当前状态; 否则后台异步执行, 立即返回 RUNNING.
     *
     * @param classId  班级 id
     * @param examName 考试名
     * @param ownerNumber 当前登录用户(用于权限校验)
     * @return 任务状态; 无权限返回 null
     */
    AudioTranscriptTaskStatusModel syncRecognizeAudio(int classId, String examName, String ownerNumber);
}
