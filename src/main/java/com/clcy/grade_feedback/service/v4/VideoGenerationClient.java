package com.clcy.grade_feedback.service.v4;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.clcy.grade_feedback.model.v4.VideoHistoryMessage;
import com.clcy.grade_feedback.model.v4.VideoUpstreamResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频生成上游 HTTP 客户端.
 * 将 HTTP 状态、上游错误和传输异常统一转换为 VideoUpstreamResult,
 * 避免业务层通过 null 或异常推断任务状态.
 */
@Service
public class VideoGenerationClient {

    private static final Logger log = LoggerFactory.getLogger(VideoGenerationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public VideoGenerationClient(
            @Qualifier("videoGenerationRestTemplate") RestTemplate restTemplate,
            @Value("${video-generation.base-url:http://47.108.214.199:5000}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    /**
     * 提交视频生成任务. 成功响应必须包含 script_name.
     */
    public VideoUpstreamResult generateVideo(String prompt, List<VideoHistoryMessage> historyMessages) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("user_prompt", prompt);
        requestBody.put("history_messages",
                null == historyMessages ? Collections.emptyList() : historyMessages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        VideoUpstreamResult result = exchange(
                UriComponentsBuilder.fromHttpUrl(baseUrl).path("/generate_video").build().encode().toUri(),
                HttpMethod.POST,
                entity,
                false);

        if (result.isSuccessful() && !StringUtils.hasText(result.getScriptName())) {
            result.setErrorCode("MISSING_SCRIPT_NAME");
            result.setError("Video generation upstream response did not contain script_name");
            result.setRetryable(false);
        }
        return result;
    }

    /**
     * 查询任务状态. 200 表示已返回状态, 202 表示仍在处理,
     * 404 表示任务不存在, 500 表示上游故障.
     */
    public VideoUpstreamResult getStatus(String scriptName) {
        return exchange(buildScriptUri("/get_status", scriptName), HttpMethod.GET,
                HttpEntity.EMPTY, false);
    }

    /**
     * 获取生成代码. 上游既可返回 JSON 的 code 字段, 也可直接返回代码文本.
     */
    public VideoUpstreamResult getCode(String scriptName) {
        return exchange(buildScriptUri("/get_code", scriptName), HttpMethod.GET,
                HttpEntity.EMPTY, true);
    }

    /**
     * 构造供前端访问的视频地址; script_name 始终按 query 参数编码.
     */
    public String buildVideoUrl(String scriptName) {
        return buildScriptUri("/get_video", scriptName).toASCIIString();
    }

    private URI buildScriptUri(String path, String scriptName) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .queryParam("script_name", scriptName)
                .build()
                .encode()
                .toUri();
    }

    private VideoUpstreamResult exchange(URI uri, HttpMethod method,
                                         HttpEntity<?> entity, boolean plainBodyIsCode) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, method, entity, String.class);
            return parseResponse(response.getStatusCodeValue(), response.getBody(), plainBodyIsCode);
        } catch (HttpStatusCodeException e) {
            VideoUpstreamResult result = parseResponse(
                    e.getRawStatusCode(), e.getResponseBodyAsString(), plainBodyIsCode);
            applyHttpErrorDefaults(result, e.getStatusText());
            log.warn("Video generation upstream returned HTTP {} for {}", e.getRawStatusCode(), uri);
            return result;
        } catch (ResourceAccessException e) {
            log.warn("Video generation upstream transport failure for {}: {}", uri, e.getMessage());
            return transientFailure("UPSTREAM_TRANSPORT_ERROR", e.getMessage());
        } catch (RestClientException e) {
            log.warn("Video generation upstream client failure for {}: {}", uri, e.getMessage());
            return transientFailure("UPSTREAM_CLIENT_ERROR", e.getMessage());
        }
    }

    private VideoUpstreamResult parseResponse(int httpStatus, String body, boolean plainBodyIsCode) {
        VideoUpstreamResult result = new VideoUpstreamResult();
        result.setHttpStatus(httpStatus);
        result.setRetryable(isRetryableStatus(httpStatus));

        if (!StringUtils.hasText(body)) {
            return result;
        }

        try {
            JSONObject json = JSON.parseObject(body);
            if (null == json) {
                return result;
            }
            result.setScriptName(firstText(json, "script_name", "scriptName"));
            result.setStatus(firstText(json, "status", "state"));
            result.setCode(firstText(json, "code"));
            result.setErrorCode(firstText(json, "error_code", "errorCode"));
            result.setError(firstText(json, "error", "error_message", "errorMessage", "message", "detail"));
        } catch (JSONException e) {
            if (plainBodyIsCode && httpStatus >= 200 && httpStatus < 300) {
                result.setCode(body);
            } else {
                result.setError(body);
            }
        }
        return result;
    }

    private void applyHttpErrorDefaults(VideoUpstreamResult result, String statusText) {
        int httpStatus = null == result.getHttpStatus() ? 0 : result.getHttpStatus();
        if (!StringUtils.hasText(result.getErrorCode())) {
            result.setErrorCode("UPSTREAM_HTTP_" + httpStatus);
        }
        if (!StringUtils.hasText(result.getError())) {
            result.setError(StringUtils.hasText(statusText) ? statusText : "Video generation upstream error");
        }
    }

    private VideoUpstreamResult transientFailure(String errorCode, String error) {
        VideoUpstreamResult result = new VideoUpstreamResult();
        result.setErrorCode(errorCode);
        result.setError(error);
        result.setRetryable(true);
        return result;
    }

    private boolean isRetryableStatus(int httpStatus) {
        return httpStatus == 202 || httpStatus == 408 || httpStatus == 429 || httpStatus >= 500;
    }

    private String firstText(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.getString(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String stripTrailingSlash(String value) {
        String result = null == value ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
