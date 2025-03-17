package com.clcy.grade_feedback.service.v3;

import com.clcy.grade_feedback.model.v2.GroupExamMetaModel;
import com.clcy.grade_feedback.model.v3.SyncExamModel;
import com.clcy.grade_feedback.model.v3.SyncPuzzleModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExamStateServiceImpl implements ExamStateService{

    private static final String EXAM_STATE_PREFIX = "EXAM_STATE";

    private static final String PUZZLE_STATE_PREFIX = "PUZZLE_STATE";

    @Autowired
    private RedisTemplate<String, Serializable> redisTemplate;

    /**
     * 存储考试状态到redis中，考试结束后如果没有提交则自动提交
     * 永远不自动过期, 通过定时任务移除
     * */
    @Override
    public SyncExamModel storeExamState(GroupExamMetaModel metaModel, SyncExamModel syncExamModel) {
        String key = String.format("%s:studentId:%s,groupId:%d,examName:%s", EXAM_STATE_PREFIX, syncExamModel.getStudentId(), syncExamModel.getGroupId(), syncExamModel.getExamName());
        // 按照metaModel中存储的考试分钟数设置syncExamModel
        int duration = metaModel.getDuration();
        syncExamModel.setStartTime(new Date());
        syncExamModel.setEndTime(new Date(syncExamModel.getStartTime().getTime() + duration * 60000L));
        // 设置永久存储, 然后手动移除
        redisTemplate.opsForValue().set(key, syncExamModel);
        return syncExamModel;
    }

    @Override
    public SyncExamModel queryExamState(String studentId, int groupId, String examName) {
        String key = String.format("%s:studentId:%s,groupId:%d,examName:%s", EXAM_STATE_PREFIX, studentId, groupId, examName);
        return (SyncExamModel) redisTemplate.opsForValue().get(key);
    }

    @Override
    public Boolean deleteExamState(String studentId, int groupId, String examName) {
        String key = String.format("%s:studentId:%s,groupId:%d,examName:%s", EXAM_STATE_PREFIX, studentId, groupId, examName);
        return redisTemplate.delete(key);
    }

    /**
     * 这里，如果对应的考试过期(通过new Date()和endDate判断), 则puzzle过期
     * */
    @Override
    public Boolean syncPuzzleRecord(String studentId, GroupExamMetaModel metaModel, SyncPuzzleModel puzzleModel) {
        String key = String.format("%s:studentId:%s,groupId:%d,examName:%s", PUZZLE_STATE_PREFIX, studentId, metaModel.getGroupId(), metaModel.getExamName());
        // 设置永久存储, 然后手动移除
        redisTemplate.opsForHash().put(key, puzzleModel.getPuzzleIdx(), puzzleModel);
        return true;
    }

    @Override
    public Boolean deletePuzzleState(String studentId, int groupId, String examName) {
        String key = String.format("%s:studentId:%s,groupId:%d,examName:%s", PUZZLE_STATE_PREFIX, studentId, groupId, examName);
        return redisTemplate.delete(key);
    }

    @Override
    public List<SyncPuzzleModel> queryPuzzleRecords(String studentId, int groupId, String examName) {
        String key = String.format("%s:studentId:%s,groupId:%d,examName:%s", PUZZLE_STATE_PREFIX, studentId, groupId, examName);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        return entries.values().stream()
                .map(o -> (SyncPuzzleModel) o)
                .sorted(Comparator.comparingInt(o -> Integer.parseInt(o.getPuzzleIdx())))
                .collect(Collectors.toList());
    }
}
