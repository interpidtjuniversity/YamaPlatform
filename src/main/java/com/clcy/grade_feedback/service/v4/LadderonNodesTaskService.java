package com.clcy.grade_feedback.service.v4;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.clcy.grade_feedback.dao.ClassInfoDao;
import com.clcy.grade_feedback.dao.GroupExamMetaDao;
import com.clcy.grade_feedback.dao.pg.AudioTranscriptTaskDao;
import com.clcy.grade_feedback.dao.pg.AudioTranscriptsDao;
import com.clcy.grade_feedback.dao.pg.StudentLadderonNodesDao;
import com.clcy.grade_feedback.dao.pg.StudentLadderonNodesTaskDao;
import com.clcy.grade_feedback.entity.AudioTranscriptTask;
import com.clcy.grade_feedback.entity.GroupExamMeta;
import com.clcy.grade_feedback.entity.StudentLadderonNode;
import com.clcy.grade_feedback.entity.StudentLadderonNodesTask;
import com.clcy.grade_feedback.model.v2.GroupClassInfoModel;
import com.clcy.grade_feedback.model.v2.GroupInstanceModel;
import com.clcy.grade_feedback.model.v4.LadderonNodesTaskStatusModel;
import com.clcy.grade_feedback.service.v2.GroupService;
import com.clcy.grade_feedback.utils.LadderApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 梯径节点提取任务服务.
 *
 * 流程:
 *   1. 前置条件: examName 之前(含)的所有考试的 audio_transcript_task 必须都是 DONE
 *   2. 遍历班级每个学生, 拼接该学生在这些考试的全部转录文本
 *   3. 调用 LadderApi.getAllNodes 得到候选节点
 *   4. 分批(每批10个)调 LLM 过滤节点
 *   5. 增量插入 student_ladderon_nodes(node_text 已存在则跳过)
 *   6. 任意学生失败则标记任务 FAILED, 不保留检查点
 */
@Service
public class LadderonNodesTaskService {

    private static final Logger log = LoggerFactory.getLogger(LadderonNodesTaskService.class);

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
    private DeepSeekLlmService deepSeekLlmService;

    private static final String NODE_FILTER_SYSTEM_PROMPT =
            "你是一个严格的物理知识节点过滤器，具备高中和大学基础物理知识。"
                    + "你的任务是：从输入的候选节点列表 nodes 中，只保留适合作为物理知识图谱节点的内容。"
                    + "\n\n"
                    + "【最终目标】"
                    + "只保留两类节点："
                    + "1. 规范、清晰、可复用、与具体题目情景无关的物理概念、物理量、物理符号、定律名称、模型名称、图像名称或公式片段；"
                    + "2. 广为人知、绝对规范、绝对正确、不会造成误解的通用物理结论或公式表达。"
                    + "\n\n"
                    + "【必须保留的典型节点】"
                    + "例如："
                    + "力、位移、速度、加速度、质量、合力、摩擦力、弹力、重力、动能、动量、冲量、功、功率、机械能、"
                    + "牛顿第二定律、动能定理、动量定理、机械能守恒、匀速直线运动、匀变速直线运动、平抛运动、圆周运动、"
                    + "匀强电场、电场强度、电势能、电势差、洛伦兹力、安培力、磁通量、法拉第电磁感应定律、"
                    + "v、t、a、x、s、F、m、k、q、E、B、I、U、R、vt、at、v方、二分之一at方、二分之一kx方、mgh、qU、BLv、"
                    + "弹性势能二分之一kx方、速度-时间图像中位移等于速度曲线与时间轴所围成的面积。"
                    + "\n\n"
                    + "【必须过滤的内容】"
                    + "以下内容一律不要保留："
                    + "1. 具体题目数值、单位或数量描述，例如：十五米、二十米、六牛、两秒、四米、三千克、前四秒、二米每二次方秒；"
                    + "2. 题目条件、题目情景或局部描述，例如：从静止出发、大小为六牛的力、前四秒的位移、两秒前进了四米、小球从斜面滑下；"
                    + "3. 完整解题过程、连续推理链、计算过程或答案求解过程，例如："
                    + "大小六牛的力，两秒前进了四米，从静止出发，公式x等于二分之一at方，算出a为二米每二次方秒，由牛顿第二定律f等于ma，解得m为三千克；"
                    + "4. 依赖具体数值或具体题目变量的中间计算式，例如：v等于二十t减四t方、x等于五t方、a等于二米每二次方秒、m等于三千克；"
                    + "5. 含有具体数字系数且明显来自某道题计算结果的表达式，例如：二十t减四t方、三t方加二t、五乘十的三次方；"
                    + "6. 语义不完整、结构不完整、需要结合上下文才能理解的短语，例如：这个力、它变大、然后减小、前面那个、代进去、可以算出；"
                    + "7. 口语噪声、转折词、语气词、连接词、音频识别噪声，例如：然后、所以、就是、那个、嗯、啊、但是、因为、的话、可以得到；"
                    + "8. 希腊字母、单位、数字、公式被语音识别错误导致含义不清的内容；"
                    + "9. 任何与具体题目情景绑定的表达，即使其中包含物理词，也不要整体保留。"
                    + "\n\n"
                    + "【重要过滤原则】"
                    + "如果一个候选节点中同时包含规范物理知识和题目情景、数值、计算过程，你不能保留原句，"
                    + "只能从中抽取最小的、规范的、与题目无关的物理知识子串。"
                    + "如果无法抽取出这样的子串，则过滤该节点。"
                    + "如果你不确定是否要过滤，则坚决执行过滤"
                    + "\n\n"
                    + "【子串抽取规则】"
                    + "例如："
                    + "输入：公式x等于二分之一at方，算出a为二米每二次方秒"
                    + "可以抽取：二分之一at方"
                    + "不能保留：算出a为二米每二次方秒"
                    + "\n"
                    + "输入：由牛顿第二定律f等于ma解得m为三千克"
                    + "可以抽取：牛顿第二定律、F=ma"
                    + "不能保留：解得m为三千克"
                    + "\n"
                    + "输入：前四秒的位移"
                    + "可以抽取：位移"
                    + "不能保留：前四秒的位移"
                    + "\n"
                    + "输入：v等于二十t减四t方"
                    + "如果它明显是具体题目的函数表达式，必须过滤；不要返回该表达式。"
                    + "\n"
                    + "输入：速度-时间图像中位移等于速度曲线与时间轴所围成的面积"
                    + "这是通用物理结论，可以保留。"
                    + "\n\n"
                    + "【数字处理规则】"
                    + "含有数字的候选节点默认过滤，除非该数字属于公认物理公式或规范常数形式的一部分。"
                    + "可以保留的数字形式包括：二分之一at方、二分之一kx方、1/2mv方、1/2CU方、v方、r方、平方反比、二倍频率等规范物理表达。"
                    + "必须过滤的数字形式包括：十五米、二十米、六牛、两秒、四米、三千克、前四秒、二十t减四t方。"
                    + "\n\n"
                    + "【判断标准】"
                    + "一个节点只有同时满足以下条件才能保留："
                    + "1. 不依赖具体题目情景；"
                    + "2. 不包含具体题目数值、单位或答案；"
                    + "3. 不是解题过程或中间计算结果；"
                    + "4. 语义完整，结构清晰；"
                    + "5. 学过相应物理的人不看原题也能准确理解；"
                    + "6. 作为知识图谱节点不会造成歧义或误导。"
                    + "\n\n"
                    + "【输出要求】"
                    + "只返回一个 JSON 数组，数组元素必须是字符串。"
                    + "不要返回解释、不要返回 Markdown、不要返回多余字段、不要返回对象。"
                    + "如果没有任何节点值得保留，返回空数组 []。";


    /** LLM 分批大小: 每批 10 个节点 */
    private static final int LLM_BATCH_SIZE = 10;

    /** 自管理线程池 */
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ladderon-nodes-worker");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * 触发梯径节点提取任务.
     * @return 任务状态; 前置条件不满足返回 status=NONE 且 hint 含拒绝原因; 无权限返回 null
     */
    public LadderonNodesTaskStatusModel triggerExtract(int classId, String examName, String ownerNumber) {
        // 0.权限校验
        if (null == classInfoDao.queryClassByIdAndOwner(classId, ownerNumber)) {
            return null;
        }
        // 1.前置条件检查: examName 之前(含)的所有考试的 audio_transcript_task 必须都是 DONE，
        //   examName 之前(不含)的所有考试的 student_ladderon_nodes_task 也必须都是DONE
        //   批量查询避免循环内逐条查库.
        List<String> priorExamNames = collectPriorExamNames(classId, examName);

        // 1.1 批量查 audio_transcript_task: 一次查出所有 priorExamNames 的转录任务状态
        List<AudioTranscriptTask> atList = audioTranscriptTaskDao.queryByClassAndExams(classId, priorExamNames);
        Map<String, String> atStatusMap = atList.stream()
                .collect(Collectors.toMap(AudioTranscriptTask::getExamName, AudioTranscriptTask::getStatus, (a, b) -> a));

        // 1.2 批量查 student_ladderon_nodes_task: examName 之前的(不含 examName 自身)的节点提取任务
        List<String> priorExamNamesExcludingSelf = priorExamNames.stream()
                .filter(e -> !e.equals(examName))
                .collect(Collectors.toList());
        Map<String, String> stStatusMap;
        if (priorExamNamesExcludingSelf.isEmpty()) {
            stStatusMap = java.util.Collections.emptyMap();
        } else {
            List<StudentLadderonNodesTask> stList = studentLadderonNodesTaskDao.queryByClassAndExams(classId, priorExamNamesExcludingSelf);
            stStatusMap = stList.stream()
                    .collect(Collectors.toMap(StudentLadderonNodesTask::getExamName, StudentLadderonNodesTask::getStatus, (a, b) -> a));
        }

        // 1.3 内存判断
        for (String priorExam : priorExamNames) {
            if (!"DONE".equals(atStatusMap.get(priorExam))) {
                return toStatusModel(null, "前置条件不满足: 考试[" + priorExam + "]的音频转录未完成, 拒绝执行");
            }
            if (!priorExam.equals(examName) && !"DONE".equals(stStatusMap.get(priorExam))) {
                return toStatusModel(null, "前置条件不满足: 考试[" + priorExam + "]的节点提取未完成, 拒绝执行");
            }
        }

        // 2.原子防重: 启动或重启任务
        int affected = studentLadderonNodesTaskDao.startOrRestartTask(classId, examName);
        if (affected == 0) {
            StudentLadderonNodesTask task = studentLadderonNodesTaskDao.queryByClassAndExam(classId, examName);
            return toStatusModel(task, null);
        }

        // 3.异步执行
        executor.submit(() -> runExtract(classId, examName, priorExamNames));
        StudentLadderonNodesTask task = studentLadderonNodesTaskDao.queryByClassAndExam(classId, examName);
        return toStatusModel(task, null);
    }

    public LadderonNodesTaskStatusModel queryStatus(int classId, String examName, String ownerNumber) {
        if (null == classInfoDao.queryClassByIdAndOwner(classId, ownerNumber)) {
            return null;
        }
        StudentLadderonNodesTask task = studentLadderonNodesTaskDao.queryByClassAndExam(classId, examName);
        return toStatusModel(task, null);
    }

    /**
     * 核心异步执行.
     */
    private void runExtract(int classId, String examName, List<String> priorExamNames) {
        try {
            // 1.收集班级所有学生 (studentId -> groupId)
            Map<String, Integer> studentGroupMap = collectStudents(classId, examName);
            if (studentGroupMap.isEmpty()) {
                studentLadderonNodesTaskDao.markFailed(classId, examName, "未找到该考试的学生分组记录");
                return;
            }

            // 2.查询 examName 对应的 startTime(用于 student_ladderon_nodes.timestamp)
            java.sql.Timestamp examStartTime = queryExamStartTime(classId, examName);

            // 3.首次执行清空旧节点(重跑场景)
            studentLadderonNodesDao.deleteByClassAndExam(classId, examName);

            int total = studentGroupMap.size();
            studentLadderonNodesTaskDao.updateCounts(classId, examName, total, 0);

            // 4.逐个学生处理: 任意失败即整体失败
            int doneCount = 0;
            for (Map.Entry<String, Integer> entry : studentGroupMap.entrySet()) {
                String studentIdStr = entry.getKey();
                int groupId = entry.getValue();
                int studentId;
                try {
                    studentId = Integer.parseInt(studentIdStr);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("学号非数字: " + studentIdStr);
                }

                // 4.1 拼接该学生在 priorExamNames 所有考试的转录文本
                String transcriptText = audioTranscriptsDao.queryConcatTranscriptByExams(studentId, priorExamNames);
                if (null == transcriptText || transcriptText.isEmpty()) {
                    log.warn("学生 {} 无转录文本, 跳过", studentIdStr);
                    doneCount++;
                    studentLadderonNodesTaskDao.updateCounts(classId, examName, total, doneCount);
                    continue;
                }

                // 4.2 调用 LadderApi 得到候选节点
                List<String> candidateNodes = LadderApi.getAllNodes(transcriptText);
                if (null == candidateNodes || candidateNodes.isEmpty()) {
                    log.info("学生 {} 无候选节点， 测试 {}", studentIdStr, examName);
                    doneCount++;
                    studentLadderonNodesTaskDao.updateCounts(classId, examName, total, doneCount);
                    continue;
                }

                // 4.3 分批调 LLM 过滤
                // 这里拿到节点后先根据student_ladderon_nodes表过滤一遍，如果不在表中则再送给llm做判断，因为不过滤的话插入时也会触发唯一索引，导致极大的网络开销，所以不如先过滤掉
                List<String> missingCandidateNodes = studentLadderonNodesDao.findMissingLadderonNodes(classId, studentId, candidateNodes);
                List<String> filteredNodes = filterNodesByLlm(missingCandidateNodes);

                // 4.4 增量插入
                for (String nodeText : filteredNodes) {
                    if (null == nodeText || nodeText.trim().isEmpty()) {
                        continue;
                    }
                    studentLadderonNodesDao.insertIfAbsent(StudentLadderonNode.builder()
                            .classId(classId)
                            .studentId(studentId)
                            .groupId(groupId)
                            .examName(examName)
                            .nodeText(nodeText.trim())
                            .timestamp(examStartTime)
                            .build());
                }

                doneCount++;
                studentLadderonNodesTaskDao.updateCounts(classId, examName, total, doneCount);
            }

            // 5.全部完成
            studentLadderonNodesTaskDao.markDone(classId, examName);
        } catch (Exception e) {
            log.error("梯径节点提取任务异常 classId={} examName={}", classId, examName, e);
            studentLadderonNodesTaskDao.markFailed(classId, examName, "任务异常: " + e.getMessage());
        }
    }

    /** LLM 调用最大重试次数 */
    private static final int LLM_MAX_RETRY = 3;

    /**
     * 分批调 LLM 过滤节点. 每批 LLM_BATCH_SIZE 个, 每批最多重试 LLM_MAX_RETRY 次,
     * 任意一批重试耗尽仍失败则抛异常导致整体任务失败.
     */
    private List<String> filterNodesByLlm(List<String> candidateNodes) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < candidateNodes.size(); i += LLM_BATCH_SIZE) {
            int end = Math.min(i + LLM_BATCH_SIZE, candidateNodes.size());
            List<String> batch = candidateNodes.subList(i, end);
            int batchNo = i / LLM_BATCH_SIZE + 1;

            // 构建用户消息: {"nodes": [...]}
            String userMessage = JSON.toJSONString(Collections.singletonMap("nodes", batch));

            // 重试调用, 最多 LLM_MAX_RETRY 次
            String response = null;
            for (int attempt = 1; attempt <= LLM_MAX_RETRY; attempt++) {
                response = deepSeekLlmService.chat(NODE_FILTER_SYSTEM_PROMPT, userMessage);
                if (null != response) {
                    break;
                }
                log.warn("LLM 过滤批次 {} 第 {} 次调用无响应", batchNo, attempt);
            }
            if (null == response) {
                throw new RuntimeException("LLM 过滤失败: 批次 " + batchNo + " 重试 " + LLM_MAX_RETRY + " 次仍无响应");
            }

            // 解析 LLM 返回的 JSON 数组
            List<String> filtered = parseLlmResponse(response);
            result.addAll(filtered);
        }
        return result;
    }

    /**
     * 解析 LLM 返回. 期望是 JSON 数组 ["node1", "node2", ...], 做容错处理.
     */
    private List<String> parseLlmResponse(String response) {
        if (null == response || response.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String trimmed = response.trim();
        // 去除可能的 markdown 代码块包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        try {
            JSONArray arr = JSON.parseArray(trimmed);
            List<String> nodes = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                nodes.add(arr.getString(i));
            }
            return nodes;
        } catch (Exception e) {
            log.warn("LLM 返回解析失败, 原样保留: {}", trimmed.substring(0, Math.min(100, trimmed.length())));
            // 解析失败时把原始文本作为一个节点保留(不丢失)
            return Collections.singletonList(trimmed);
        }
    }

    /**
     * 收集 examName 之前(含)的所有考试名(按 startTime 升序).
     * "之前"指 group_exam_meta.start_time <= 目标考试的 start_time.
     */
    private List<String> collectPriorExamNames(int classId, String examName) {
        // 1.查班级所有分组
        List<GroupClassInfoModel> groups = groupService.queryClassGroups(classId);
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }
        // 2.聚合所有考试 meta (examName -> startTime), 同名取最早
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
        // 3.找目标考试的 startTime
        java.sql.Timestamp targetStart = examStartTimeMap.get(examName);
        if (null == targetStart) {
            return Collections.emptyList();
        }
        // 4.筛选 startTime <= targetStart 的考试, 按时间升序
        return examStartTimeMap.entrySet().stream()
                .filter(e -> !e.getValue().after(targetStart))
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 查询 examName 对应的 startTime(取所有分组中最早的).
     */
    private java.sql.Timestamp queryExamStartTime(int classId, String examName) {
        List<GroupClassInfoModel> groups = groupService.queryClassGroups(classId);
        java.sql.Timestamp earliest = null;
        for (GroupClassInfoModel group : groups) {
            List<GroupExamMeta> metas = groupExamMetaDao.batchQueryGroupExamMeta(group.getGroupId());
            for (GroupExamMeta meta : metas) {
                if (examName.equals(meta.getExamName())) {
                    if (null == earliest || meta.getStartTime().before(earliest)) {
                        earliest = meta.getStartTime();
                    }
                }
            }
        }
        return earliest;
    }

    /**
     * 收集班级参加 examName 的所有学生 (studentId -> groupId).
     */
    private Map<String, Integer> collectStudents(int classId, String examName) {
        List<GroupInstanceModel> instances = groupService.queryGroupInstanceByClassIdAndExamName(classId, examName);
        Map<String, Integer> map = new HashMap<>();
        for (GroupInstanceModel instance : instances) {
            if (null == instance.getStudentsId()) {
                continue;
            }
            for (String studentId : instance.getStudentsId()) {
                map.put(studentId, instance.getGroupId());
            }
        }
        return map;
    }

    private LadderonNodesTaskStatusModel toStatusModel(StudentLadderonNodesTask task, String rejectReason) {
        if (null != rejectReason) {
            // 前置条件不满足, 返回 NONE 状态 + 拒绝原因
            return LadderonNodesTaskStatusModel.builder()
                    .status("NONE")
                    .totalCount(0)
                    .doneCount(0)
                    .hint(rejectReason)
                    .build();
        }
        if (null == task) {
            return LadderonNodesTaskStatusModel.builder()
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
        return LadderonNodesTaskStatusModel.builder()
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
