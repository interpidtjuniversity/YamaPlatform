package com.clcy.grade_feedback.service.v4;

import com.clcy.grade_feedback.dao.pg.VideoConversationDao;
import com.clcy.grade_feedback.entity.VideoConversationRecord;
import com.clcy.grade_feedback.model.v4.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 视频生成对话业务实现.
 */
@Service
public class VideoConversationServiceImpl implements VideoConversationService {

    private static final Logger log = LoggerFactory.getLogger(VideoConversationServiceImpl.class);
    private static final int DEFAULT_HISTORY_LIMIT = 10;
    private static final int MAX_HISTORY_LIMIT = 50;
    private static final int MAX_PROMPT_LENGTH = 10000;

    @Autowired
    private VideoConversationDao videoConversationDao;

    @Autowired
    private VideoGenerationClient videoGenerationClient;

    @Value("${video-generation.poll-delay-ms:5000}")
    private long pollDelayMs;

    @Override
    public List<VideoConversationModel> queryConversations(String userId) {
        if (!StringUtils.hasText(userId)) {
            return new ArrayList<>();
        }
        return videoConversationDao.queryAllByUser(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public VideoGenerateStatusModel queryGenerateStatus(String userId, String scriptName) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(scriptName)) {
            return null;
        }
        VideoConversationRecord record = videoConversationDao.queryByUserAndScriptName(
                userId, scriptName.trim());
        if (null == record) {
            return null;
        }
        return VideoGenerateStatusModel.builder()
                .scriptName(record.getScriptName())
                .status(record.getStatus())
                .errorCode(record.getErrorCode())
                .errorMessage(record.getErrorMessage())
                .build();
    }

    @Override
    public VideoConversationSubmitResult generate(String userId, String prompt, Integer historyLimit) {
        if (!StringUtils.hasText(userId)) {
            return rejected("登录用户信息无效", null);
        }
        String normalizedPrompt = null == prompt ? null : prompt.trim();
        if (!StringUtils.hasText(normalizedPrompt)) {
            return rejected("user_prompt不能为空", null);
        }
        if (normalizedPrompt.length() > MAX_PROMPT_LENGTH) {
            return rejected("user_prompt长度不能超过" + MAX_PROMPT_LENGTH + "个字符", null);
        }

        int limit = normalizeHistoryLimit(historyLimit);
        Long recordId = videoConversationDao.insertSubmitting(userId, normalizedPrompt);
        if (null == recordId) {
            return rejected("请等待当前视频生成完毕", videoConversationDao.queryActiveByUser(userId));
        }

        try {
            List<VideoHistoryMessage> historyMessages = buildHistoryMessages(userId, limit);
            VideoUpstreamResult upstream = videoGenerationClient.generateVideo(normalizedPrompt, historyMessages);
            if (!upstream.isSuccessful()) {
                String errorCode = defaultText(upstream.getErrorCode(), "VIDEO_SUBMIT_FAILED");
                String errorMessage = defaultText(upstream.getError(), "视频生成任务提交失败");
                videoConversationDao.markSubmissionFailed(recordId, errorCode, errorMessage);
                return rejected(errorMessage, videoConversationDao.findById(recordId));
            }

            int affected = videoConversationDao.markQueued(recordId, upstream.getScriptName(), pollDelayMs);
            if (affected == 0) {
                log.error("保存视频生成任务失败, recordId={}, scriptName={}", recordId, upstream.getScriptName());
                videoConversationDao.markSubmissionFailed(recordId, "TASK_STATE_CONFLICT", "保存视频任务状态失败");
                return rejected("保存视频任务状态失败", videoConversationDao.findById(recordId));
            }
            return VideoConversationSubmitResult.builder()
                    .accepted(Boolean.TRUE)
                    .message("视频生成任务已提交")
                    .record(toModel(videoConversationDao.findById(recordId)))
                    .build();
        } catch (Exception e) {
            log.error("提交视频生成任务异常, recordId={}, userId={}", recordId, userId, e);
            videoConversationDao.markSubmissionFailed(recordId, "VIDEO_SUBMIT_EXCEPTION", safeMessage(e));
            return rejected("视频生成任务提交失败，请稍后重试", videoConversationDao.findById(recordId));
        }
    }

    private List<VideoHistoryMessage> buildHistoryMessages(String userId, int limit) {
        List<VideoHistoryMessage> messages = new ArrayList<>();
        for (VideoConversationRecord record : videoConversationDao.queryRecentCompleted(userId, limit)) {
            if (!StringUtils.hasText(record.getUserPrompt()) || !StringUtils.hasText(record.getCode())) {
                continue;
            }
            messages.add(new VideoHistoryMessage("user", record.getUserPrompt()));
            messages.add(new VideoHistoryMessage("assistant", record.getCode() + record.getErrorMessage()));
        }
        return messages;
    }

    private int normalizeHistoryLimit(Integer historyLimit) {
        if (null == historyLimit) {
            return DEFAULT_HISTORY_LIMIT;
        }
        if (historyLimit < 1) {
            return 1;
        }
        return Math.min(historyLimit, MAX_HISTORY_LIMIT);
    }

    private VideoConversationSubmitResult rejected(String message, VideoConversationRecord record) {
        return VideoConversationSubmitResult.builder()
                .accepted(Boolean.FALSE)
                .message(message)
                .record(toModel(record))
                .build();
    }

    private VideoConversationModel toModel(VideoConversationRecord record) {
        if (null == record) {
            return null;
        }
        return VideoConversationModel.builder()
                .id(record.getId())
                .userPrompt(record.getUserPrompt())
                .scriptName(record.getScriptName())
                .code(record.getCode())
                .videoUrl(record.getVideoUrl())
                .status(record.getStatus())
                .errorCode(record.getErrorCode())
                .errorMessage(record.getErrorMessage())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .finishedAt(record.getFinishedAt())
                .build();
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String safeMessage(Exception e) {
        String message = null == e ? null : e.getMessage();
        if (!StringUtils.hasText(message)) {
            return "视频生成任务提交异常";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
