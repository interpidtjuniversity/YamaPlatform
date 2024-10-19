package com.clcy.grade_feedback.service;

public interface GuavaCacheService {

    Object getToken(String key);

    Object putToken(String key, Object Value);

    void deleteToken(String key);
}
