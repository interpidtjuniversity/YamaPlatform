package com.clcy.grade_feedback.service;

import com.clcy.grade_feedback.model.v2.GroupExamDetailModel;
import com.clcy.grade_feedback.model.v2.GroupExamMetaModel;
import com.clcy.grade_feedback.model.v2.GroupInstanceModel;
import com.clcy.grade_feedback.service.v2.ExamService;
import com.clcy.grade_feedback.service.v2.GroupService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GuavaCacheServiceImpl implements GuavaCacheService{

    @Autowired
    private GroupService groupService;

    @Autowired
    private ExamService examService;

    private static final Cache<String, Object> CACHE = CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .maximumSize(100)
            .expireAfterAccess(300, TimeUnit.SECONDS)
            .build();

    private static final Cache<Integer, List<GroupInstanceModel>> GROUP_INSTANCES_CACHE = CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .maximumSize(100)
            .expireAfterWrite(300, TimeUnit.SECONDS)
            .build();

    private static final Cache<Integer, List<GroupExamMetaModel>> GROUP_EXAMS_CACHE = CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .maximumSize(100)
            .expireAfterWrite(300, TimeUnit.SECONDS)
            .build();

    private static final Cache<String, List<GroupExamDetailModel>> GROUP_EXAM_DETAILS_CACHE = CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .maximumSize(500)
            .expireAfterWrite(300, TimeUnit.SECONDS)
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


    @Override
    public List<GroupInstanceModel> getGroupInstances(int groupId) {
        try {
            return GROUP_INSTANCES_CACHE.get(groupId, () -> groupService.queryGroupInstanceByGroupId(groupId));
        } catch (Exception e) {
            // 打日志
            return Lists.newArrayList();
        }
    }

    @Override
    public List<GroupExamMetaModel> getGroupExams(int groupId) {
        try {
            return GROUP_EXAMS_CACHE.get(groupId, () -> examService.queryGroupExamsMeta(groupId));
        } catch (Exception e) {
            // 打日志
            return Lists.newArrayList();
        }
    }

    @Override
    public List<GroupExamDetailModel> getGroupExamDetails(int groupId, String examName) {
        try {
            return GROUP_EXAM_DETAILS_CACHE.get(String.format("groupId:%d--examName:%s", groupId, examName), () -> examService.queryGroupExamDetail(groupId, examName, true, false));
        } catch (Exception e) {
            // 打日志
            return Lists.newArrayList();
        }
    }

}
