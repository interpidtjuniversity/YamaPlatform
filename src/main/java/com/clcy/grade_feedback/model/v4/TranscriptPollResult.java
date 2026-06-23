package com.clcy.grade_feedback.model.v4;

import lombok.Data;

/**
 * 单次轮询 NLS 任务的结果(不阻塞 sleep).
 */
@Data
public class TranscriptPollResult {

    /** 是否已到达终态(成功或失败) */
    private boolean completed;

    /** 识别文本; 成功时非空, 失败或运行中为 null */
    private String text;

    public static TranscriptPollResult running() {
        TranscriptPollResult r = new TranscriptPollResult();
        r.setCompleted(false);
        return r;
    }

    public static TranscriptPollResult done(String text) {
        TranscriptPollResult r = new TranscriptPollResult();
        r.setCompleted(true);
        r.setText(text);
        return r;
    }

    public static TranscriptPollResult failed() {
        TranscriptPollResult r = new TranscriptPollResult();
        r.setCompleted(true);
        r.setText(null);
        return r;
    }
}
