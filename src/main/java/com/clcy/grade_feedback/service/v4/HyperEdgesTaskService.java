package com.clcy.grade_feedback.service.v4;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.clcy.grade_feedback.dao.ClassInfoDao;
import com.clcy.grade_feedback.dao.GroupExamMetaDao;
import com.clcy.grade_feedback.dao.pg.AudioTranscriptTaskDao;
import com.clcy.grade_feedback.dao.pg.AudioTranscriptsDao;
import com.clcy.grade_feedback.dao.pg.StudentExamHyperEdgesDao;
import com.clcy.grade_feedback.dao.pg.StudentExamHyperEdgesTaskDao;
import com.clcy.grade_feedback.dao.pg.StudentLadderonNodesDao;
import com.clcy.grade_feedback.dao.pg.StudentLadderonNodesTaskDao;
import com.clcy.grade_feedback.entity.AudioTranscript;
import com.clcy.grade_feedback.entity.GroupExamMeta;
import com.clcy.grade_feedback.entity.StudentExamHyperEdge;
import com.clcy.grade_feedback.entity.StudentExamHyperEdgesTask;
import com.clcy.grade_feedback.model.v2.GroupClassInfoModel;
import com.clcy.grade_feedback.model.v2.GroupInstanceModel;
import com.clcy.grade_feedback.model.v4.HyperEdgeModel;
import com.clcy.grade_feedback.model.v4.HyperEdgesTaskStatusModel;
import com.clcy.grade_feedback.service.v2.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 超边(推理结构)提取任务服务.
 *
 * 流程:
 *   1. 前置条件: audio_transcript_task(examName) = DONE 且 student_ladderon_nodes_task(examName) = DONE
 *   2. 提取该学生该考试所有音频的 transcript_text(一般<=5个)
 *   3. 提取该学生 examName 之前(含)所有考试的 node_text 作为候选节点(约100个)
 *   4. 逐个音频调用 LLM 提取超边, 每次最多重试3次
 *   5. 将超边插入 student_exam_hyper_edges 表
 */
@Service
public class HyperEdgesTaskService {

    private static final Logger log = LoggerFactory.getLogger(HyperEdgesTaskService.class);

    @Autowired
    private ClassInfoDao classInfoDao;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupExamMetaDao groupExamMetaDao;

    @Autowired
    private AudioTranscriptTaskDao audioTranscriptTaskDao;

    @Autowired
    private AudioTranscriptsDao audioTranscriptsDao;

    @Autowired
    private StudentLadderonNodesDao studentLadderonNodesDao;

    @Autowired
    private StudentLadderonNodesTaskDao studentLadderonNodesTaskDao;

    @Autowired
    private StudentExamHyperEdgesDao studentExamHyperEdgesDao;

    @Autowired
    private StudentExamHyperEdgesTaskDao studentExamHyperEdgesTaskDao;

    @Autowired
    private DeepSeekLlmService deepSeekLlmService;

    private static final String SYSTEM_PROMPT =
            "你是一个物理专家，你可以依据给定节点组{nodes}从给定文本{text}中提取出存在的物理推理结构，"
            + "请你按照如下格式返回文本中存在的推理结构"
            + "[{\"inputs\":[\"合外力\",\"质量\",\"牛顿第二定律\"], \"outputs\":[\"加速度\"], \"type\":\"invoke_rule\",\"confidence\":0.99}]"
            + "请只返回一个JSON数组, 不要包含任何其他内容。";

    private static final int LLM_MAX_RETRY = 3;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "hyper-edges-worker");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * 触发超边提取任务.
     */
    public HyperEdgesTaskStatusModel triggerExtract(int classId, String examName, String studentId, String ownerNumber) {
        // 0.权限校验
        if (null == classInfoDao.queryClassByIdAndOwner(classId, ownerNumber)) {
            return null;
        }

        int studentIdInt;
        try {
            studentIdInt = Integer.parseInt(studentId);
        } catch (NumberFormatException e) {
            return toStatusModel(null, "学号非数字: " + studentId);
        }

        // 1.前置条件检查
        // 1.1 audio_transcript_task(examName) 必须为 DONE
        com.clcy.grade_feedback.entity.AudioTranscriptTask at =
                audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
        if (null == at || !"DONE".equals(at.getStatus())) {
            return toStatusModel(null, "前置条件不满足: 考试[" + examName + "]的音频转录未完成");
        }
        // 1.2 student_ladderon_nodes_task(examName) 必须为 DONE
        com.clcy.grade_feedback.entity.StudentLadderonNodesTask st =
                studentLadderonNodesTaskDao.queryByClassAndExam(classId, examName);
        if (null == st || !"DONE".equals(st.getStatus())) {
            return toStatusModel(null, "前置条件不满足: 考试[" + examName + "]的节点提取未完成");
        }

        // 2.原子防重
        int affected = studentExamHyperEdgesTaskDao.startOrRestartTask(classId, studentIdInt, examName);
        if (affected == 0) {
            StudentExamHyperEdgesTask task = studentExamHyperEdgesTaskDao.queryByStudentAndExam(studentIdInt, examName);
            return toStatusModel(task, null);
        }

        // 3.异步执行
        executor.submit(() -> runExtract(classId, examName, studentIdInt));
        StudentExamHyperEdgesTask task = studentExamHyperEdgesTaskDao.queryByStudentAndExam(studentIdInt, examName);
        return toStatusModel(task, null);
    }

    public HyperEdgesTaskStatusModel queryStatus(int classId, String examName, String studentId, String ownerNumber) {
        if (null == classInfoDao.queryClassByIdAndOwner(classId, ownerNumber)) {
            return null;
        }
        int studentIdInt;
        try {
            studentIdInt = Integer.parseInt(studentId);
        } catch (NumberFormatException e) {
            return null;
        }
        // 校验该学生是否属于该班级该考试(防止跨班级查询其他学生状态)
        if (!isStudentInClassExam(classId, examName, studentIdInt)) {
            return null;
        }
        StudentExamHyperEdgesTask task = studentExamHyperEdgesTaskDao.queryByStudentAndExam(studentIdInt, examName);
        return toStatusModel(task, null);
    }

    /**
     * 核心异步执行.
     */
    private void runExtract(int classId, String examName, int studentId) {
        try {
            // 1.查该学生该考试的所有音频转录文本
            List<AudioTranscript> transcripts = audioTranscriptsDao.queryByStudentAndExam(studentId, examName);
            if (null == transcripts || transcripts.isEmpty()) {
                studentExamHyperEdgesTaskDao.markFailed(studentId, examName, "无音频转录记录");
                return;
            }

            // 2.查该学生 examName 之前(含)所有考试的 node_text 作为候选节点, 同时拿到 examName 的 startTime
            PriorExamInfo priorInfo = collectPriorExamInfo(classId, examName);
            List<String> nodeTexts = studentLadderonNodesDao.queryDistinctNodeTextsByStudentAndExams(studentId, priorInfo.priorExamNames);
            if (null == nodeTexts || nodeTexts.isEmpty()) {
                studentExamHyperEdgesTaskDao.markFailed(studentId, examName, "无候选节点");
                return;
            }

            // 3.查 groupId, startTime 从上一步已拿到
            int groupId = queryGroupId(classId, examName, studentId);
            java.sql.Timestamp examStartTime = priorInfo.examStartTime;

            // 4.重跑时清空旧超边
            studentExamHyperEdgesDao.deleteByStudentAndExam(studentId, examName);

            int total = transcripts.size();
            studentExamHyperEdgesTaskDao.updateCounts(studentId, examName, total, 0);

            // 5.逐个音频调用 LLM 提取超边
            int doneCount = 0;
            for (AudioTranscript transcript : transcripts) {
                String transcriptText = transcript.getTranscriptText();
                int puzzleIndex = transcript.getPuzzleIdx();
                if (null == transcriptText || transcriptText.isEmpty()) {
                    doneCount++;
                    studentExamHyperEdgesTaskDao.updateCounts(studentId, examName, total, doneCount);
                    continue;
                }

                // 5.1 调 LLM 提取超边, 最多重试3次
                List<HyperEdgeModel> edges = null;
                for (int attempt = 1; attempt <= LLM_MAX_RETRY; attempt++) {
                    edges = extractHyperEdges(nodeTexts, transcriptText);
                    if (null != edges) {
                        break;
                    }
                    log.warn("超边提取 student={} puzzleIdx={} 第 {} 次调用无响应", studentId, puzzleIndex, attempt);
                }
                if (null == edges) {
                    throw new RuntimeException("超边提取失败: puzzleIdx=" + puzzleIndex + " 重试 " + LLM_MAX_RETRY + " 次仍无响应");
                }

                // 5.2 转为实体并批量插入
                if (!edges.isEmpty()) {
                    List<StudentExamHyperEdge> edgeEntities = edges.stream().map(e -> StudentExamHyperEdge.builder()
                            .classId(classId)
                            .studentId(studentId)
                            .groupId(groupId)
                            .examName(examName)
                            .inputs(JSON.toJSONString(e.getInputs()))
                            .outputs(JSON.toJSONString(e.getOutputs()))
                            .type(e.getType())
                            .confidence(e.getConfidence())
                            .puzzleIndex(puzzleIndex)
                            .timestamp(examStartTime)
                            .build()
                    ).collect(Collectors.toList());
                    studentExamHyperEdgesDao.batchInsert(edgeEntities);
                }

                doneCount++;
                studentExamHyperEdgesTaskDao.updateCounts(studentId, examName, total, doneCount);
            }

            // 6.完成
            studentExamHyperEdgesTaskDao.markDone(studentId, examName);
        } catch (Exception e) {
            log.error("超边提取任务异常 classId={} examName={} studentId={}", classId, examName, studentId, e);
            studentExamHyperEdgesTaskDao.markFailed(studentId, examName, "任务异常: " + e.getMessage());
        }
    }

    /**
     * 调 LLM 提取超边. 失败返回 null.
     */
    private List<HyperEdgeModel> extractHyperEdges(List<String> nodeTexts, String transcriptText) {
        try {
            String userMessage = JSON.toJSONString(new LinkedHashMap<String, Object>() {{
                put("nodes", nodeTexts);
                put("text", transcriptText);
            }});
            String response = deepSeekLlmService.chat(SYSTEM_PROMPT, userMessage);
            if (null == response) {
                return null;
            }
            // 解析 LLM 返回的 JSON 数组
            String trimmed = stripMarkdown(response);
            JSONArray arr = JSON.parseArray(trimmed);
            List<HyperEdgeModel> edges = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                HyperEdgeModel edge = new HyperEdgeModel();
                edge.setInputs(obj.getJSONArray("inputs").toJavaList(String.class));
                edge.setOutputs(obj.getJSONArray("outputs").toJavaList(String.class));
                edge.setType(obj.getString("type"));
                edge.setConfidence(obj.getDouble("confidence"));
                edges.add(edge);
            }
            return edges;
        } catch (Exception e) {
            log.warn("超边 LLM 返回解析失败", e);
            return null;
        }
    }

    /**
     * 去除可能的 markdown 代码块包裹.
     */
    private String stripMarkdown(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    /**
     * 收集 examName 之前(含)的所有考试名(按 startTime 升序).
     */
    private PriorExamInfo collectPriorExamInfo(int classId, String examName) {
        List<GroupClassInfoModel> groups = groupService.queryClassGroups(classId);
        if (groups.isEmpty()) {
            return new PriorExamInfo(Collections.emptyList(), null);
        }
        // 遍历所有分组查 group_exam_meta, 聚合 examName -> 最早 startTime
        Map<String, java.sql.Timestamp> examStartTimeMap = new HashMap<>();
        for (GroupClassInfoModel group : groups) {
            List<GroupExamMeta> metas = groupExamMetaDao.batchQueryGroupExamMeta(group.getGroupId());
            for (GroupExamMeta meta : metas) {
                java.sql.Timestamp exist = examStartTimeMap.get(meta.getExamName());
                if (null == exist || meta.getStartTime().before(exist)) {
                    examStartTimeMap.put(meta.getExamName(), meta.getStartTime());
                }
            }
        }
        java.sql.Timestamp targetStart = examStartTimeMap.get(examName);
        if (null == targetStart) {
            return new PriorExamInfo(Collections.emptyList(), null);
        }
        List<String> priorExamNames = examStartTimeMap.entrySet().stream()
                .filter(e -> !e.getValue().after(targetStart))
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return new PriorExamInfo(priorExamNames, targetStart);
    }

    /**
     * 考试前置信息: 前序考试名列表 + 目标考试的 startTime.
     */
    private static class PriorExamInfo {
        final List<String> priorExamNames;
        final java.sql.Timestamp examStartTime;

        PriorExamInfo(List<String> priorExamNames, java.sql.Timestamp examStartTime) {
            this.priorExamNames = priorExamNames;
            this.examStartTime = examStartTime;
        }
    }

    /**
     * 判断学生是否属于该班级该考试(通过 group_instance 的 studentsId 查找).
     */
    private boolean isStudentInClassExam(int classId, String examName, int studentId) {
        List<GroupInstanceModel> instances = groupService.queryGroupInstanceByClassIdAndExamName(classId, examName);
        String studentIdStr = String.valueOf(studentId);
        for (GroupInstanceModel instance : instances) {
            if (null != instance.getStudentsId() && instance.getStudentsId().contains(studentIdStr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询学生所属 groupId(同时校验学生是否属于该班级该考试).
     */
    private int queryGroupId(int classId, String examName, int studentId) {
        List<GroupInstanceModel> instances = groupService.queryGroupInstanceByClassIdAndExamName(classId, examName);
        String studentIdStr = String.valueOf(studentId);
        for (GroupInstanceModel instance : instances) {
            if (null != instance.getStudentsId() && instance.getStudentsId().contains(studentIdStr)) {
                return instance.getGroupId();
            }
        }
        return 0;
    }

    private HyperEdgesTaskStatusModel toStatusModel(StudentExamHyperEdgesTask task, String rejectReason) {
        if (null != rejectReason) {
            return HyperEdgesTaskStatusModel.builder()
                    .status("NONE")
                    .totalCount(0)
                    .doneCount(0)
                    .hint(rejectReason)
                    .build();
        }
        if (null == task) {
            return HyperEdgesTaskStatusModel.builder()
                    .status("NONE")
                    .totalCount(0)
                    .doneCount(0)
                    .hint("未执行, 开始提取")
                    .build();
        }
        String hint;
        switch (task.getStatus()) {
            case "RUNNING":
                hint = "提取中...";
                break;
            case "DONE":
                hint = "已完成, 重新提取";
                break;
            case "FAILED":
                hint = "提取失败, 重新提取";
                break;
            default:
                hint = "未执行, 开始提取";
        }
        return HyperEdgesTaskStatusModel.builder()
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .doneCount(task.getDoneCount())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .errorMessage(task.getErrorMessage())
                .hint(hint)
                .build();
    }
}
