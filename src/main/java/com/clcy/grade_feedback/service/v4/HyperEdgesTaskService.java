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
import com.clcy.grade_feedback.entity.*;
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

    private static final String HYPER_EDGE_SYSTEM_PROMPT =
            "你是一个严格的物理知识图谱超边抽取助手，具备高中和大学基础物理知识。"
                    + "你的任务是：依据给定的候选节点列表 {nodes}，从给定学生语音转写文本 {text} 中抽取明确存在的物理推理结构，也就是超边。"
                    + "\n\n"
                    + "【图谱目标】"
                    + "构建学生个性化物理知识逻辑图谱。"
                    + "图谱应保留学生在解题中实际表达或实际使用的物理知识、公式、定律、物理量依赖和推理关系，"
                    + "但不要保留过于具体的题目情景、选项判断、口语过程、纯数值计算或无明确物理逻辑的先后叙述。"
                    + "\n\n"
                    + "【超边含义】"
                    + "一条超边表示一个物理推理单元，可理解为：inputs -> type -> outputs。"
                    + "inputs 表示该推理所依赖的物理量、物理概念、公式、定律、条件或前提；"
                    + "outputs 表示由这些 inputs 推出的物理量、物理结论、公式表达、状态变化或关系。"
                    + "\n\n"
                    + "【候选节点使用规则】"
                    + "1. inputs 和 outputs 必须从给定的 {nodes} 中选择，并原样使用（强制）。"
                    + "2. 只有当文本 {text} 中出现明显有用、短小、物理语义完整、且确实不在 {nodes} 中的表达或者{nodes}中绝对无相似表达时，才允许少量使用文本原文中的短语作为节点。"
                    + "3. 不能凭空创造学生没有表达过的规范物理概念、定律、公式或结论。"
                    + "4. 不要为了让推理显得完整而补充文本中没有出现的节点。"
                    + "5. 不要把“然后、所以、根据、代入、可以求出、这个、那个、选项、是错的、答案、因为、的话”等口语词、过程词或连接词作为节点。"
                    + "6. 不要使用具体题目数值、单位、答案或局部情景作为节点，例如：十五米、二十米、六牛、两秒、四米、三千克、前四秒、A点到B点、离地面h处、木板长2米、选B。"
                    + "\n\n"
                    + "【必须抽取的推理关系】"
                    + "当文本中明确表达以下物理逻辑关系时，应抽取为超边："
                    + "1. 定律/定理/原理/公式调用关系：文本中出现“根据、由、利用、用、带入、套用、满足、符合”等表达，且后面推出某个物理量、公式或结论时，应抽取。例如：根据牛顿第二定律得到加速度；由动能定理求速度；利用机械能守恒得到高度和速度关系。"
                    + "2. 定义或公式表达，文本中表达某物理量等于某公式、由某公式定义、就是某表达式时，应抽取。例如：动能就是二分之一mv方；向心加速度等于v方除以r；角速度等于角度除以时间。"
                    + "3. 文本中表达一个物理量与另一个或多个物理量有关、取决于、由其决定、成正比、成反比、随其变化时，应抽取。例如：加速度和合外力有关；向心力由速度和半径决定；周期和角速度有关。"
                    + "4. 多前提共同推出单一结论的关系：如果文本表达多个条件、物理量或定律共同推出一个结论，应放入同一个超边，而不要拆成多条单输入边。例如：在质量不变时，根据F=ma，合外力增大推出加速度增大。"
                    + "5. 守恒关系：文本中出现机械能守恒、动量守恒、角动量守恒、电荷守恒、能量守恒等，并据此推出某些物理量关系时，应抽取。例如：机械能守恒推出动能增加势能减少；动量守恒推出碰撞前后总动量相等。"
                    + "6. 能量或物理量转化关系：文本中表达一种能量、动量、冲量、功或其他物理量转化为另一种形式时，应抽取。例如：重力势能转化为动能；弹性势能转化为动能；合外力做功导致动能变化。"
                    + "7. 力或机制提供某种效果的关系：文本中表达某个力、场、约束、接触或机制提供加速度、向心力、回复力、支持、约束等效果时，应抽取。例如：摩擦力提供向心力；洛伦兹力提供向心力；弹力提供回复力。"
                    + "8. 约束条件关系：文本中表达物理约束、几何约束、运动约束、接触约束、临界条件、平衡条件时，应抽取。例如：绳长不变约束速度方向；刚体约束角速度相同；平衡条件推出合力为零。"
                    + "9. 方向、阻碍、反向或抵消关系：文本中明确表达某物理量方向相反、阻碍运动、抵消、反向、指向圆心、沿切线方向、垂直于速度方向等关系时，应抽取。例如：摩擦力阻碍相对运动；向心力指向圆心；洛伦兹力垂直于速度方向。"
                    + "10. 比较关系：文本中表达物理量大小、方向、增减、相等、不变、最大、最小、正负、先增后减等比较或状态变化时，应抽取，前提是比较对象是抽象物理节点而非具体数值答案。例如：质量不变时合外力越大加速度越大；速度增大导致动能增大；合力为零则速度不变。"
                    + "11. 公式变形关系：文本中明确表达对公式进行移项、整理、变形、等价改写，并得到另一个物理表达式时，应抽取。例如：由F=ma变形得到a=F/m；由v方等于v0方加2ax推出位移表达式。"
                    + "12. 代入关系：文本中表达把某个抽象物理条件、物理量关系或公式代入另一个公式，并推出新的抽象表达时，应抽取。但如果只是代入具体数值求答案，不要抽取。"
                    + "13. 图像物理意义关系：文本中表达物理图像的斜率、面积、截距、交点、变化趋势代表某个物理量或物理意义时，应抽取。例如：速度-时间图像的斜率表示加速度；速度-时间图像面积表示位移；力-位移图像面积表示功。"
                    + "14. 模型适用关系：文本中表达某物理情景可视为某模型，或满足某模型条件并据此使用模型规律时，应抽取。例如：这个过程可以看作匀变速直线运动；小球做圆周运动所以有向心加速度。"
                    + "15. 条件触发关系：文本中表达某条件成立会触发某物理结论、状态或规律时，应抽取。例如：合力为零推出物体处于平衡或匀速直线运动；速度为零但加速度不一定为零。"
                    + "16. 显式因果关系：文本中表达某物理量变化导致另一物理量变化，例如“因为速度变大所以动能变大”，应抽取。"
                    + "17. 抽象计算关系：当计算结果仍是通用抽象物理表达，而不是具体题目数值答案时，可以抽取。例如：由周期得到角速度等于2π除以T；由频率得到周期等于1/f。"
                    + "\n\n"
                    + "【禁止抽取的内容】"
                    + "以下内容不要抽取为超边："
                    + "1. 无物理逻辑的文本先后顺序：不要因为文本中先说A后说B，就机械抽取 A -> B。只有A在物理上推出、定义、约束、影响或依赖B时，才允许抽取。"
                    + "2. 单纯口语化解题过程，例如：然后代进去、所以可以算、接着看下一步；"
                    + "3. 纯数值计算过程或具体题目答案，例如：算出a为二米每二次方秒、解得m为三千克；"
                    + "4. 过于具体的题目情景关系，例如：小球从A点到B点、木板长2米、离地面h处；"
                    + "5. 选项判断或答案判断，例如：A是错的、选B；"
                    + "6. 只有一个节点、无法形成 inputs -> outputs 的表达；"
                    + "7. 文本中没有明确表达、只是你根据物理知识补全出来的推理。"
                    + "\n\n"
                    + "【重要建模原则】"
                    + "1. 不要把文本先后顺序机械地抽成线性链条。"
                    + "2. 只有存在明确物理逻辑关系时才构造超边。"
                    + "3. 如果多个前提共同推出一个结论，应放在同一个超边中，而不是拆成多个单输入单输出的边。"
                    + "4. 如果一个定律、公式和若干物理量共同推出结论，应把定律或公式与相关物理量一起放入 inputs。"
                    + "5. 如果某句话只是说明两个表达等价，可以使用 equivalent。"
                    + "6. 如果某句话是在给出公式定义，可以使用 defines。"
                    + "7. 如果不确定是否存在可靠推理关系，必须不抽取。"
                    + "8. inputs 和 outputs 都不能为空；如果无法同时确定非空 inputs 和非空 outputs，则不要返回该超边。"
                    + "9. {text}表达中可能存在错误的超边（逻辑错误、参量错误、公式变形错误等等），你一定要忠于{text}原文，绝对不要修改其本来的意思，你只管抽取，绝对不要管对错。（非常重要！！！）"
                    + "\n\n"
                    + "【允许的 type 类型】"
                    + "type 必须是以下类型之一："
                    + "invoke_rule：调用定律、公式、规律作为依据；"
                    + "defines：定义、公式表达；"
                    + "equivalent：等价表达、同义改写；"
                    + "depends_on：一个物理量依赖另一个或多个物理量；"
                    + "provides：某个力、条件或机制提供某种物理效果；"
                    + "conserves：守恒关系；"
                    + "transforms_to：能量或物理量转化；"
                    + "opposes：方向相反、阻碍关系；"
                    + "constraint：物理条件约束；"
                    + "infer：从物理前提推出物理结论；"
                    + "transform：公式变形、整理、化简；"
                    + "substitute：代入抽象物理量或条件；"
                    + "compare：比较物理量大小、方向或状态；"
                    + "compute：数值计算，只有当计算结果仍是抽象物理表达时才使用；"
                    + "unknown：存在关系但无法判断类型。"
                    + "\n\n"
                    + "【type 选择规则】"
                    + "1. 文本明确说“根据某定律/公式/规律得到……”时，优先使用 invoke_rule。"
                    + "2. 文本表达“某物理量就是/等于/定义为某公式”时，使用 defines。"
                    + "3. 文本表达两个说法、公式或物理量表达等价时，使用 equivalent。"
                    + "4. 文本表达某物理量由其他物理量决定、有关、依赖时，使用 depends_on。"
                    + "5. 文本表达某机制产生或提供某效果时，使用 provides。"
                    + "6. 文本表达守恒时，使用 conserves。"
                    + "7. 文本表达能量或物理量从一种形式转化为另一种形式时，使用 transforms_to。"
                    + "8. 文本表达阻碍、反向、抵消时，使用 opposes。"
                    + "9. 文本表达约束条件，例如方向约束、几何约束、接触约束、临界条件时，使用 constraint。"
                    + "10. 文本只是一般性推出关系，但无法归入以上更具体类型时，使用 infer。"
                    + "11. 文本表达公式整理、变形、移项、化简时，使用 transform。"
                    + "12. 文本表达把抽象物理量或条件代入公式时，使用 substitute。"
                    + "13. 文本表达比较大小、方向、增减、状态时，使用 compare。"
                    + "14. compute 只允许用于抽象物理表达计算，不要用于具体数值答案。"
                    + "\n\n"
                    + "【节点选择优先级】"
                    + "1. 优先选择 {nodes} 中已经存在且语义最完整、最规范的节点。"
                    + "2. 如果 {nodes} 中同时存在公式名和公式表达，例如“牛顿第二定律”和“F=ma”，文本二者都表达了，可以都放入 inputs；如果只表达其中一个，不要强行补另一个。"
                    + "3. 如果 {nodes} 中存在较规范节点和残缺节点，优先使用规范节点。"
                    + "4. 如果候选节点中只有残缺表达，但文本能明确支持其物理含义，可以谨慎使用。"
                    + "5. 不要把具体物体对象作为 inputs 或 outputs，除非它参与了明确的物理作用关系且没有更合适的物理量节点。"
                    + "\n\n"
                    + "【置信度规则】"
                    + "confidence 是 0 到 1 的数值。"
                    + "0.90 到 1.00：文本明确表达，inputs、outputs、type 都清楚；"
                    + "0.75 到 0.89：文本基本明确，但存在少量口语、省略或 ASR 噪声；"
                    + "0.60 到 0.74：关系可能成立但不够完整，只有在对图谱有明显价值时才保留；"
                    + "低于 0.60：不要返回。"
                    + "\n\n"
                    + "【输出格式】"
                    + "请只返回一个 JSON 数组，不要返回解释，不要返回 Markdown，不要返回多余字段。"
                    + "数组元素必须是对象，且只能包含以下字段："
                    + "inputs、outputs、type、confidence。"
                    + "格式如下："
                    + "[{\"inputs\":[\"合外力\",\"质量\",\"牛顿第二定律\"],\"outputs\":[\"加速度\"],\"type\":\"invoke_rule\",\"confidence\":0.99}]"
                    + "\n"
                    + "inputs 必须是字符串数组，不能为空。"
                    + "outputs 必须是字符串数组，不能为空。"
                    + "type 必须是允许的 type 类型之一。"
                    + "confidence 必须是 0 到 1 的数字。"
                    + "如果没有任何明确可抽取的物理推理结构，返回空数组 []。"
                    + "\n\n"
                    + "【示例1】"
                    + "nodes = [\"牛顿第二定律\", \"加速度\", \"合外力\", \"质量\"]"
                    + "text = \"根据牛顿第二定律，可以得到加速度等于合外力除以质量。\""
                    + "输出："
                    + "[{\"inputs\":[\"牛顿第二定律\",\"合外力\",\"质量\"],\"outputs\":[\"加速度\"],\"type\":\"invoke_rule\",\"confidence\":0.95}]"
                    + "\n\n"
                    + "【示例2】"
                    + "nodes = [\"动能\", \"二分之一mv方\", \"速度\"]"
                    + "text = \"动能就是二分之一mv方，和速度的平方有关。\""
                    + "输出："
                    + "[{\"inputs\":[\"速度\"],\"outputs\":[\"动能\",\"二分之一mv方\"],\"type\":\"defines\",\"confidence\":0.95}]"
                    + "\n\n"
                    + "【示例3】"
                    + "nodes = [\"速度\", \"加速度\", \"然后\", \"代入\"]"
                    + "text = \"然后我们把这个代进去，可以算出来。\""
                    + "输出："
                    + "[]";


    private static final int LLM_MAX_RETRY = 3;

    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
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
        AudioTranscriptTask at =
                audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
        if (null == at || !"DONE".equals(at.getStatus())) {
            return toStatusModel(null, "前置条件不满足: 考试[" + examName + "]的音频转录未完成");
        }
        // 1.2 student_ladderon_nodes_task(examName) 必须为 DONE
        StudentLadderonNodesTask st =
                studentLadderonNodesTaskDao.queryByClassAndExam(classId, examName);
        if (null == st || !"DONE".equals(st.getStatus())) {
            return toStatusModel(null, "前置条件不满足: 考试[" + examName + "]的节点提取未完成");
        }

        // 2.原子防重
        int affected = studentExamHyperEdgesTaskDao.startOrRestartTask(classId, studentIdInt, examName);
        if (affected == 0) {
            StudentExamHyperEdgesTask task = studentExamHyperEdgesTaskDao.queryByStudentAndExam(classId, studentIdInt, examName);
            return toStatusModel(task, null);
        }

        // 3.异步执行
        executor.submit(() -> runExtract(classId, examName, studentIdInt));
        StudentExamHyperEdgesTask task = studentExamHyperEdgesTaskDao.queryByStudentAndExam(classId, studentIdInt, examName);
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
        StudentExamHyperEdgesTask task = studentExamHyperEdgesTaskDao.queryByStudentAndExam(classId, studentIdInt, examName);
        return toStatusModel(task, null);
    }

    /**
     * 核心异步执行.
     */
    private void runExtract(int classId, String examName, int studentId) {
        try {
            // 1.查该学生该考试的所有音频转录文本
            List<AudioTranscript> transcripts = audioTranscriptsDao.queryByStudentAndExam(classId, studentId, examName);
            if (null == transcripts || transcripts.isEmpty()) {
                studentExamHyperEdgesTaskDao.markFailed(classId, studentId, examName, "无音频转录记录");
                return;
            }

            // 2.查该学生 examName 之前(含)所有考试的 node_text 作为候选节点, 同时拿到 examName 的 startTime
            PriorExamInfo priorInfo = collectPriorExamInfo(classId, examName);
            List<String> nodeTexts = studentLadderonNodesDao.queryDistinctNodeTextsByStudentAndExams(classId, studentId, priorInfo.priorExamNames);
            if (null == nodeTexts || nodeTexts.isEmpty()) {
                studentExamHyperEdgesTaskDao.markFailed(classId, studentId, examName, "无候选节点");
                return;
            }

            // 3.查 groupId, startTime 从上一步已拿到
            int groupId = queryGroupId(classId, examName, studentId);
            java.sql.Timestamp examStartTime = priorInfo.examStartTime;

            // 4.重跑时清空旧超边
            studentExamHyperEdgesDao.deleteByStudentAndExam(classId, studentId, examName);

            int total = transcripts.size();
            studentExamHyperEdgesTaskDao.updateCounts(classId, studentId, examName, total, 0);

            // 5.逐个音频调用 LLM 提取超边
            int doneCount = 0;
            for (AudioTranscript transcript : transcripts) {
                String transcriptText = transcript.getTranscriptText();
                int puzzleIndex = transcript.getPuzzleIdx();
                if (null == transcriptText || transcriptText.isEmpty()) {
                    doneCount++;
                    studentExamHyperEdgesTaskDao.updateCounts(classId, studentId, examName, total, doneCount);
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
                studentExamHyperEdgesTaskDao.updateCounts(classId, studentId, examName, total, doneCount);
            }

            // 6.完成
            studentExamHyperEdgesTaskDao.markDone(classId, studentId, examName);
        } catch (Exception e) {
            log.error("超边提取任务异常 classId={} examName={} studentId={}", classId, examName, studentId, e);
            studentExamHyperEdgesTaskDao.markFailed(classId, studentId, examName, "任务异常: " + e.getMessage());
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
            String response = deepSeekLlmService.chat(HYPER_EDGE_SYSTEM_PROMPT, userMessage);
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
