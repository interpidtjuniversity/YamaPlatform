package com.clcy.grade_feedback.service.v3;

import com.clcy.grade_feedback.model.v2.GroupClassInfoModel;
import com.clcy.grade_feedback.model.v2.GroupExamStudentAnswerRecordModel;
import com.clcy.grade_feedback.model.v3.SyncExamModel;
import com.clcy.grade_feedback.model.v3.SyncPuzzleModel;
import com.clcy.grade_feedback.service.LoginService;
import com.clcy.grade_feedback.service.v2.ExamService;
import com.clcy.grade_feedback.service.v2.GroupService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;

@Component
@EnableScheduling
public class GroupExamAutoSubmitter {

    @Autowired
    private RedisTemplate<String, Serializable> redisTemplate;

    @Autowired
    private ExamService examService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private ExamStateService examStateService;

    @Autowired
    private RedisLockService redisLockService;


    private static final String EXAM_STATE_PREFIX = "EXAM_STATE:*";

    /**
     * 每5分钟扫描一次
     * */
    @Scheduled(initialDelay = 0, fixedDelay = 5 * 60 * 1000)
    public void autoSubmit() {
        Set<String> keys = scanKeys(EXAM_STATE_PREFIX);
        keys.forEach(examKey -> {
            SyncExamModel examState = (SyncExamModel) redisTemplate.opsForValue().get(examKey);
            // 倒计时已经结束
            if (null != examState && new Date().getTime() > examState.getEndTime().getTime() ) {
                String studentId = examState.getStudentId();
                int groupId = examState.getGroupId();
                String examName = examState.getExamName();
                RLock lock = redisLockService.acquireLock(studentId, groupId, examName);
                try{
                    List<SyncPuzzleModel> puzzleStates = examStateService.queryPuzzleRecords(studentId, groupId, examName);
                    Map<String, String> answers = Maps.newHashMap();
                    List<Long> clickNextTimeList = Lists.newArrayList();

                    puzzleStates.forEach(ps -> {
                        answers.put(ps.getPuzzleIdx(), ps.getAnswer());
                        clickNextTimeList.add(ps.getClickNextTime());
                    });
                    GroupClassInfoModel classInfoModel = groupService.queryGroupClass(groupId);
                    String studentName = loginService.studentName(studentId);

                    boolean success = examService.addStudentAnswerRecord(
                            GroupExamStudentAnswerRecordModel.builder()
                                    .classId(classInfoModel.getClassId())
                                    .className(classInfoModel.getClassName())
                                    .groupId(groupId)
                                    .groupName(classInfoModel.getGroupName())
                                    .studentId(studentId)
                                    .studentName(null == studentName ? "" : studentName)
                                    .examName(examName)
                                    .answers(answers)
                                    .clickNextTimeList(clickNextTimeList)
                                    .build()
                    ) == 1;
                    if (success) {
                        examStateService.deleteExamState(studentId, groupId, examName);
                        examStateService.deletePuzzleState(studentId, groupId, examName);
                    }
                } finally {
                    redisLockService.releaseLock(lock);
                }
            }
        });
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> result = new HashSet<>();
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(100) // 每次扫描数量
                .build());
        while (cursor.hasNext()) {
            result.add(new String(cursor.next()));
        }
        cursor.close();
        return result;
    }
}
