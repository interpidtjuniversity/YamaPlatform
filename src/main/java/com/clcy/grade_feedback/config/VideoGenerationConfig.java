package com.clcy.grade_feedback.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 视频生成上游专用 HTTP 客户端配置.
 */
@Configuration
public class VideoGenerationConfig {

    @Value("${video-generation.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${video-generation.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Bean(name = "videoGenerationRestTemplate")
    public RestTemplate videoGenerationRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }
}
