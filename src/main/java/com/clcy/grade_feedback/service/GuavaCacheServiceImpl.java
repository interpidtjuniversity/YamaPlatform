package com.clcy.grade_feedback.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class GuavaCacheServiceImpl implements GuavaCacheService{

    private static final Cache<String, Object> CACHE = CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .maximumSize(100)
            .expireAfterAccess(600, TimeUnit.SECONDS)
            .build();

    private static final String TOKEN_PREFIX = "TOKEN_";

    @Override
    public Object getToken(String key) {
        return CACHE.getIfPresent(TOKEN_PREFIX + key);
    }

    @Override
    public Object putToken(String key, Object value) {
        CACHE.put(TOKEN_PREFIX + key, value);
        return value;
    }

    @Override
    public void deleteToken(String key) {
        CACHE.invalidate(TOKEN_PREFIX + key);
    }
}
