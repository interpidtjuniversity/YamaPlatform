package com.clcy.grade_feedback.service.v4;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.clcy.grade_feedback.model.v4.TranscriptPollResult;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

/**
 * 阿里云 NLS 录音文件识别服务实现.
 *
 * 参考阿里云 filetrans API:
 *   - 域名: filetrans.cn-shanghai.aliyuncs.com
 *   - 版本: 2018-08-17
 *   - SubmitTask(POST): 提交识别任务, 返回 TaskId
 *   - GetTaskResult(GET): 轮询任务结果
 *
 * 鉴权方式: 使用 AK/SK 通过 DefaultAcsClient 鉴权(与 Python 版 AcsClient 一致),
 *           文件转写 API 不需要 Token(Token 仅用于实时流式识别).
 *
 * 修复 Python 原代码的 bug:
 *   get_task_result 中 while True + sleep(5) 为无限轮询, 任务卡在 RUNNING/QUEUEING 时
 *   线程永远挂起. 这里改为带超时的有限轮询(最长 5 分钟).
 */
@Service
public class AliyunTranscriptsServiceImpl implements AliyunTranscriptsService, InitializingBean {

    // ===== 阿里云 NLS 录音文件识别配置 =====
    private static final String REGION_ID = "cn-shanghai";
    private static final String DOMAIN = "filetrans.cn-shanghai.aliyuncs.com";
    private static final String API_VERSION = "2018-08-17";
    // NLS 项目的 appkey
    private static final String APP_KEY = "9q7y2BnhAU0f0pRa";

    // ===== 请求参数 Key =====
    private static final String KEY_TASK = "Task";
    private static final String KEY_APP_KEY = "appkey";
    private static final String KEY_FILE_LINK = "file_link";
    private static final String KEY_VERSION = "version";
    private static final String KEY_ENABLE_WORDS = "enable_words";
    private static final String KEY_ENABLE_SAMPLE_RATE_ADAPTIVE = "enable_sample_rate_adaptive";
    private static final String KEY_TASK_ID = "TaskId";
    private static final String KEY_STATUS_TEXT = "StatusText";

    // ===== 任务状态值 =====
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_QUEUEING = "QUEUEING";

    // ===== 轮询与超时配置 =====
    /** 轮询最长等待时间: 5 分钟. 超过则放弃, 返回空. */
    private static final long MAX_WAIT_MS = 5 * 60 * 1000L;
    /** 轮询间隔: 5 秒 */
    private static final long POLL_INTERVAL_MS = 5 * 1000L;
    /** 单次 HTTP 请求读超时: 15 秒 */
    private static final int HTTP_READ_TIMEOUT_MS = 15 * 1000;
    /** 单次 HTTP 请求连接超时: 10 秒 */
    private static final int HTTP_CONNECT_TIMEOUT_MS = 10 * 1000;

    private IAcsClient acsClient;

    @Override
    public void afterPropertiesSet() throws Exception {
        // AK/SK 从环境变量读取, 与 ALiYunOssServiceImpl 保持一致
        String akId = System.getenv("OSS_ACCESS_KEY_ID");
        String akSecret = System.getenv("OSS_ACCESS_KEY_SECRET");
        DefaultProfile profile = DefaultProfile.getProfile(REGION_ID, akId, akSecret);
        this.acsClient = new DefaultAcsClient(profile);
    }


    @Override
    public String recognizeAudio(String fileLink) {
        if (null == fileLink || fileLink.isEmpty()) {
            return "";
        }
        String taskId = submitTask(fileLink);
        if (null == taskId) {
            // 提交失败, 快速返回空字符串
            return "";
        }
        String result = getTaskResult(taskId);
        return (null == result || result.isEmpty()) ? "" : result;
    }

    @Override
    public String submitTask(String fileLink) {
        if (null == fileLink || fileLink.isEmpty()) {
            return null;
        }
        try {
            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain(DOMAIN);
            request.setSysVersion(API_VERSION);
            request.setSysAction("SubmitTask");
            request.setSysReadTimeout(HTTP_READ_TIMEOUT_MS);
            request.setSysConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);

            // 构建任务参数: 新接入使用 version=4.0, 开启采样率自适应
            JSONObject task = new JSONObject();
            task.put(KEY_APP_KEY, APP_KEY);
            task.put(KEY_FILE_LINK, fileLink);
            task.put(KEY_VERSION, "4.0");
            task.put(KEY_ENABLE_WORDS, false);
            task.put(KEY_ENABLE_SAMPLE_RATE_ADAPTIVE, true);

            // Task 作为 body 参数传入(JSON 字符串)
            request.putBodyParameter(KEY_TASK, task.toJSONString());

            CommonResponse response = acsClient.getCommonResponse(request);
            JSONObject body = JSON.parseObject(response.getData());
            String statusText = body.getString(KEY_STATUS_TEXT);
            if (STATUS_SUCCESS.equals(statusText)) {
                return body.getString(KEY_TASK_ID);
            }
            // 提交失败
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 轮询获取任务识别结果, 最多等待 MAX_WAIT_MS.
     *
     * 修复 Python 原代码的 bug: 原代码 while True + sleep(5) 无限轮询, 没有超时,
     * 任务卡在 RUNNING/QUEUEING 时线程永远挂起. 这里改为带 deadline 的有限轮询.
     *
     * @param taskId 任务 ID
     * @return 识别结果文本; 超时、失败或结果为空时返回 null
     */
    private String getTaskResult(String taskId) {
        if (null == taskId) {
            return null;
        }
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        try {
            while (System.currentTimeMillis() < deadline) {
                CommonRequest request = new CommonRequest();
                request.setSysMethod(MethodType.GET);
                request.setSysDomain(DOMAIN);
                request.setSysVersion(API_VERSION);
                request.setSysAction("GetTaskResult");
                request.setSysReadTimeout(HTTP_READ_TIMEOUT_MS);
                request.setSysConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
                request.putQueryParameter(KEY_TASK_ID, taskId);

                CommonResponse response = acsClient.getCommonResponse(request);
                JSONObject body = JSON.parseObject(response.getData());
                String statusText = body.getString(KEY_STATUS_TEXT);

                if (STATUS_RUNNING.equals(statusText) || STATUS_QUEUEING.equals(statusText)) {
                    // 任务仍在进行中, 等待后继续轮询
                    Thread.sleep(POLL_INTERVAL_MS);
                    continue;
                }
                if (STATUS_SUCCESS.equals(statusText)) {
                    return extractText(body);
                }
                // 其他状态(如失败)直接返回 null
                return null;
            }
        } catch (InterruptedException e) {
            // 被中断, 恢复中断标志并退出
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 超时或异常
        return null;
    }

    /**
     * 从识别结果中提取所有句子的文本并拼接.
     * 响应结构: { "Result": { "Sentences": [ { "Text": "..." }, ... ] } }
     */
    private String extractText(JSONObject body) {
        try {
            JSONObject result = body.getJSONObject("Result");
            if (null == result) {
                return null;
            }
            JSONArray sentences = result.getJSONArray("Sentences");
            if (null == sentences || sentences.isEmpty()) {
                return null;
            }
            StringBuilder txt = new StringBuilder();
            for (int i = 0; i < sentences.size(); i++) {
                JSONObject sentence = sentences.getJSONObject(i);
                if (null != sentence) {
                    String text = sentence.getString("Text");
                    if (null != text) {
                        txt.append(text);
                    }
                }
            }
            return txt.length() == 0 ? null : txt.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TranscriptPollResult pollTaskOnce(String taskId) {
        if (null == taskId) {
            return TranscriptPollResult.failed();
        }
        try {
            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.GET);
            request.setSysDomain(DOMAIN);
            request.setSysVersion(API_VERSION);
            request.setSysAction("GetTaskResult");
            request.setSysReadTimeout(HTTP_READ_TIMEOUT_MS);
            request.setSysConnectTimeout(HTTP_CONNECT_TIMEOUT_MS);
            request.putQueryParameter(KEY_TASK_ID, taskId);

            CommonResponse response = acsClient.getCommonResponse(request);
            JSONObject body = JSON.parseObject(response.getData());
            String statusText = body.getString(KEY_STATUS_TEXT);

            if (STATUS_RUNNING.equals(statusText) || STATUS_QUEUEING.equals(statusText)) {
                // 仍在进行中, 不 sleep, 由调用方控制轮转节奏
                return TranscriptPollResult.running();
            }
            if (STATUS_SUCCESS.equals(statusText)) {
                return TranscriptPollResult.done(extractText(body));
            }
            // 其他状态视为失败
            return TranscriptPollResult.failed();
        } catch (Exception e) {
            e.printStackTrace();
            return TranscriptPollResult.failed();
        }
    }
}
