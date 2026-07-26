package com.clcy.grade_feedback.service.v4;

import com.clcy.grade_feedback.dao.pg.VideoConversationDao;
import com.clcy.grade_feedback.entity.VideoConversationRecord;
import com.clcy.grade_feedback.model.v4.VideoUpstreamResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 视频生成任务状态轮询器. 数据库租约用于应用重启和多实例下的任务抢占及更新隔离.
 */
@Service
public class VideoConversationStatusPoller {

    private static final Logger log = LoggerFactory.getLogger(VideoConversationStatusPoller.class);

    @Autowired
    private VideoConversationDao videoConversationDao;

    @Autowired
    private VideoGenerationClient videoGenerationClient;

    @Value("${video-generation.poll-delay-ms:5000}")
    private long pollDelayMs;

    @Value("${video-generation.lease-seconds:60}")
    private long leaseSeconds;

    @Value("${video-generation.claim-limit:10}")
    private int claimLimit;

    @Value("${video-generation.max-poll-count:720}")
    private int maxPollCount;

    @Value("${video-generation.submitting-timeout-seconds:120}")
    private long submittingTimeoutSeconds;

    private final ThreadPoolExecutor executor;

    public VideoConversationStatusPoller(@Value("${video-generation.worker-count:4}") int workerCount) {
        int size = Math.max(1, workerCount);
        this.executor = new ThreadPoolExecutor(
                size,
                size,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                r -> {
                    Thread thread = new Thread(r, "video-status-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 定时线程只做任务领取和投递，不在 Spring 默认调度线程中执行阻塞 HTTP.
     */
    @Scheduled(fixedDelayString = "${video-generation.scan-delay-ms:2000}")
    public void scanTasks() {
        try {
            videoConversationDao.markStaleSubmittingFailed(Math.max(1L, submittingTimeoutSeconds));
            int availableWorkers = executor.getMaximumPoolSize() - executor.getActiveCount();
            if (availableWorkers <= 0) {
                return;
            }
            String leaseToken = UUID.randomUUID().toString();
            List<VideoConversationRecord> tasks = videoConversationDao.claimDueTasks(
                    Math.min(Math.max(1, claimLimit), availableWorkers), Math.max(1L, leaseSeconds), leaseToken);
            for (VideoConversationRecord task : tasks) {
                try {
                    executor.submit(() -> pollTask(task, leaseToken));
                } catch (RuntimeException e) {
                    // 未成功投递时保留租约，租约到期后可由下一轮或其他实例重新领取.
                    log.error("投递视频状态轮询任务失败, recordId={}", task.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("扫描视频生成任务失败", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private void pollTask(VideoConversationRecord task, String leaseToken) {
        try {
            if (null != task.getPollCount() && task.getPollCount() >= maxPollCount) {
                videoConversationDao.markFailed(task.getId(), leaseToken,
                        "POLL_TIMEOUT", "视频生成任务等待超时");
                return;
            }
            VideoUpstreamResult statusResult = videoGenerationClient.getStatus(task.getScriptName());
            handleStatus(task, leaseToken, statusResult);
        } catch (Exception e) {
            log.error("轮询视频生成任务异常, recordId={}, scriptName={}",
                    task.getId(), task.getScriptName(), e);
            rescheduleOrFail(task, leaseToken, task.getStatus(),
                    "POLL_EXCEPTION", safeMessage(e));
        }
    }

    private void handleStatus(VideoConversationRecord task, String leaseToken, VideoUpstreamResult result) {
        if (isHttpStatus(result, 404)) {
            videoConversationDao.markFailed(task.getId(), leaseToken,
                    defaultText(result.getErrorCode(), "VIDEO_JOB_NOT_FOUND"),
                    defaultText(result.getError(), "Video job not found"));
            return;
        }

        String status = normalizeStatus(result.getStatus());
        if ("FAILED".equals(status)) {
            fetchCodeAndMarkFailed(task, leaseToken,
                    defaultText(result.getErrorCode(), "VIDEO_GENERATION_FAILED"),
                    defaultText(result.getError(), "Video generation failed"));
            return;
        }
        if ("COMPLETED".equals(status) || "DONE".equals(status) || "SUCCESS".equals(status) || "SUCCEEDED".equals(status)) {
            fetchCodeAndComplete(task, leaseToken);
            return;
        }
        if ("QUEUED".equals(status) || "GENERATING".equals(status) || "RENDERING".equals(status)) {
            rescheduleOrFail(task, leaseToken, status, null, null);
            return;
        }

        if (result.isSuccessful() || isHttpStatus(result, 202) || result.isRetryable()) {
            rescheduleOrFail(task, leaseToken, task.getStatus(),
                    result.getErrorCode(), result.getError());
            return;
        }

        videoConversationDao.markFailed(task.getId(), leaseToken,
                defaultText(result.getErrorCode(), "STATUS_QUERY_FAILED"),
                defaultText(result.getError(), "查询视频生成状态失败"));
    }

    /**
     * 上游任务失败后仍尝试读取代码。代码生成可能已经成功，只是在后续渲染阶段失败。
     */
    private void fetchCodeAndMarkFailed(VideoConversationRecord task, String leaseToken,
                                        String errorCode, String errorMessage) {
        VideoUpstreamResult codeResult = videoGenerationClient.getCode(task.getScriptName());
        if (codeResult.isSuccessful() && StringUtils.hasText(codeResult.getCode())) {
            videoConversationDao.markFailed(task.getId(), leaseToken, codeResult.getCode(),
                    errorCode, errorMessage);
            return;
        }

        String codeStatus = normalizeStatus(codeResult.getStatus());
        if (isHttpStatus(codeResult, 202) && !"FAILED".equals(codeStatus)) {
            // 状态接口已经确认任务失败，但代码文件可能刚刚落盘，稍后再尝试读取。
            rescheduleOrFail(task, leaseToken, task.getStatus(), errorCode, errorMessage);
            return;
        }
        if (null == codeResult.getHttpStatus() && codeResult.isRetryable()) {
            // 传输异常时不立即放弃可能已生成的代码。
            rescheduleOrFail(task, leaseToken, task.getStatus(), errorCode, errorMessage);
            return;
        }

        // 代码生成阶段失败或代码文件确实不存在，保留原始任务错误并结束任务。
        videoConversationDao.markFailed(task.getId(), leaseToken, errorCode, errorMessage);
    }

    private void fetchCodeAndComplete(VideoConversationRecord task, String leaseToken) {
        VideoUpstreamResult codeResult = videoGenerationClient.getCode(task.getScriptName());
        String codeStatus = normalizeStatus(codeResult.getStatus());
        if ("FAILED".equals(codeStatus)) {
            videoConversationDao.markFailed(task.getId(), leaseToken,
                    defaultText(codeResult.getErrorCode(), "RENDER_FAILED"),
                    defaultText(codeResult.getError(), "Video generation failed"));
            return;
        }
        if (codeResult.isSuccessful() && StringUtils.hasText(codeResult.getCode())) {
            videoConversationDao.markCompleted(task.getId(), leaseToken, codeResult.getCode(),
                    videoGenerationClient.buildVideoUrl(task.getScriptName()));
            return;
        }
        if (isHttpStatus(codeResult, 202) || codeResult.isRetryable()) {
            rescheduleOrFail(task, leaseToken, "RENDERING",
                    codeResult.getErrorCode(), codeResult.getError());
            return;
        }
        videoConversationDao.markFailed(task.getId(), leaseToken,
                defaultText(codeResult.getErrorCode(), "GET_CODE_FAILED"),
                defaultText(codeResult.getError(), "获取视频生成代码失败"));
    }

    private void rescheduleOrFail(VideoConversationRecord task, String leaseToken, String status,
                                  String errorCode, String errorMessage) {
        int nextPollCount = (null == task.getPollCount() ? 0 : task.getPollCount()) + 1;
        if (nextPollCount >= maxPollCount) {
            videoConversationDao.markFailed(task.getId(), leaseToken,
                    "POLL_TIMEOUT", "视频生成任务等待超时");
            return;
        }
        String pollableStatus = normalizePollableStatus(status, task.getStatus());
        videoConversationDao.reschedule(task.getId(), leaseToken, pollableStatus,
                Math.max(0L, pollDelayMs), errorCode, errorMessage);
    }

    private String normalizePollableStatus(String status, String fallback) {
        String normalized = normalizeStatus(status);
        if ("QUEUED".equals(normalized) || "GENERATING".equals(normalized) || "RENDERING".equals(normalized)) {
            return normalized;
        }
        normalized = normalizeStatus(fallback);
        return "GENERATING".equals(normalized) || "RENDERING".equals(normalized) ? normalized : "QUEUED";
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null;
    }

    private boolean isHttpStatus(VideoUpstreamResult result, int status) {
        return null != result && null != result.getHttpStatus() && result.getHttpStatus() == status;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String safeMessage(Exception e) {
        String message = null == e ? null : e.getMessage();
        if (!StringUtils.hasText(message)) {
            return "视频状态轮询异常";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
