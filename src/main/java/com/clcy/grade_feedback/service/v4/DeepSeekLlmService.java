package com.clcy.grade_feedback.service.v4;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * DeepSeek LLM 服务: 通过 HTTP 调用 DeepSeek 的 /chat/completions 接口.
 *
 * 请求格式(参考 curl):
 *   POST https://api.deepseek.com/chat/completions
 *   Header: Authorization: Bearer ${DEEPSEEK_API_KEY}
 *   Body: { "model", "messages", "thinking": {"type": "enabled"}, "reasoning_effort": "high", "stream": false }
 */
@Service
public class DeepSeekLlmService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmService.class);

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用 DeepSeek, 发送 system + user 消息, 返回 LLM 回复文本.
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return LLM 回复内容; 失败返回 null
     */
    public String chat(String systemPrompt, String userMessage) {
        String url = baseUrl + "/chat/completions";

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", false);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        body.put("messages", messages);

        // 开启思考模式
        JSONObject thinking = new JSONObject();
        thinking.put("type", "enabled");
        body.put("thinking", thinking);
        body.put("reasoning_effort", "high");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        try {
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            String response = restTemplate.postForObject(url, entity, String.class);
            if (null == response) {
                return null;
            }
            // 解析 choices[0].message.content
            JSONObject resp = JSON.parseObject(response);
            JSONArray choices = resp.getJSONArray("choices");
            if (null == choices || choices.isEmpty()) {
                return null;
            }
            return choices.getJSONObject(0).getJSONObject("message").getString("content");
        } catch (Exception e) {
            log.error("DeepSeek 调用失败", e);
            return null;
        }
    }
}
