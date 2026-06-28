package com.clcy.grade_feedback.service.v4;

import com.alibaba.fastjson.JSON;
import com.clcy.grade_feedback.dao.ClassInfoDao;
import com.clcy.grade_feedback.dao.FeedBackPuzzleDao;
import com.clcy.grade_feedback.dao.pg.AudioTranscriptTaskDao;
import com.clcy.grade_feedback.dao.pg.AudioTranscriptsDao;
import com.clcy.grade_feedback.entity.AudioTranscript;
import com.clcy.grade_feedback.entity.AudioTranscriptTask;
import com.clcy.grade_feedback.entity.ClassInfo;
import com.clcy.grade_feedback.entity.FeedBackPuzzle;
import com.clcy.grade_feedback.model.v2.GroupInstanceModel;
import com.clcy.grade_feedback.model.v4.AudioTranscriptTaskStatusModel;
import com.clcy.grade_feedback.model.v4.FailedFileInfo;
import com.clcy.grade_feedback.model.v4.TranscriptPollResult;
import com.clcy.grade_feedback.service.ALiYunOssService;
import com.clcy.grade_feedback.service.v2.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 音频转录任务服务实现.
 *
 * 核心流程(triggerTranscript 异步执行部分):
 *   1. 按 (classId, examName) 查 group_instance, 拿到每个学生所属分组
 *   2. 对每个学生用 OSS prefix 列举音频文件, 解析出 puzzleIdx
 *   3. 为每个音频文件生成签名 URL, 提交 NLS 识别任务, 收集 (fileMeta, taskId)
 *   4. 轮转轮询所有未完成任务(pollTaskOnce 不 sleep), 让任务在阿里云侧并行执行
 *   5. 完成的任务把识别文本写入 audio_transcripts 表
 *   6. 更新 audio_transcript_task 状态为 DONE/FAILED
 *
 * 防重提交: startOrRestartTask 用 PG 的 INSERT ... ON CONFLICT 原子保证同一
 * (classId, examName) 只有一个 RUNNING 任务(超过 1 小时的僵尸 RUNNING 可重启).
 */
@Service
public class AudioTranscriptTaskServiceImpl implements AudioTranscriptTaskService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptTaskServiceImpl.class);

    @Autowired
    private ClassInfoDao classInfoDao;

    @Autowired
    private GroupService groupService;

    @Autowired
    private ALiYunOssService aLiYunOssService;

    @Autowired
    private AliyunTranscriptsService aliyunTranscriptsService;

    @Autowired
    private AudioTranscriptsDao audioTranscriptsDao;

    @Autowired
    private AudioTranscriptTaskDao audioTranscriptTaskDao;

    @Autowired
    private FeedBackPuzzleDao feedBackPuzzleDao;

    private static final String AUDIO_DIR = "feed_back/";

    /** 自管理线程池, 避免依赖 @EnableAsync 的 AOP 代理; 任务在独立线程跑, 不阻塞 HTTP 请求 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "audio-transcript-worker");
        t.setDaemon(true);
        return t;
    });

    /** 轮询轮转间隔: 每轮询完所有任务后 sleep 多久再进入下一轮 */
    private static final long POLL_ROUND_INTERVAL_MS = 5 * 1000L;
    /** 整体最长等待: 30 分钟(大班级可能音频多) */
    private static final long MAX_WAIT_MS = 30 * 60 * 1000L;

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public AudioTranscriptTaskStatusModel queryStatusNoAuth(int classId, String examName) {
        AudioTranscriptTask task = audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
        return toStatusModel(task);
    }

    @Override
    public AudioTranscriptTaskStatusModel triggerTranscript(int classId, String examName, String ownerNumber) {
        // 0.权限校验
        if (!hasPermission(classId, ownerNumber)) {
            return null;
        }
        // 1.原子防重: 启动或重启任务. 返回 0 说明已有 RUNNING 任务, 拒绝重复提交.
        int affected = audioTranscriptTaskDao.startOrRestartTask(classId, examName);
        if (affected == 0) {
            AudioTranscriptTask task = audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
            return toStatusModel(task);
        }
        // 2.异步执行识别流程, 立即返回 RUNNING 给前端转圈
        executor.submit(() -> runTranscript(classId, examName));
        AudioTranscriptTask task = audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
        return toStatusModel(task);
    }

    @Override
    public AudioTranscriptTaskStatusModel syncRecognizeAudio(int classId, String examName, String ownerNumber) {
        // 0.权限校验
        if (!hasPermission(classId, ownerNumber)) {
            return null;
        }
        // 1.原子防重: 启动或重启任务(与全量转录共用任务行, 已有 RUNNING 则拒绝)
        int affected = audioTranscriptTaskDao.startOrRestartTask(classId, examName);
        if (affected == 0) {
            AudioTranscriptTask task = audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
            return toStatusModel(task);
        }
        // 2.异步执行增量识别流程, 立即返回 RUNNING 给前端转圈
        executor.submit(() -> runSyncTranscript(classId, examName));
        AudioTranscriptTask task = audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
        return toStatusModel(task);
    }

    @Override
    public AudioTranscriptTaskStatusModel queryStatus(int classId, String examName, String ownerNumber) {
        if (!hasPermission(classId, ownerNumber)) {
            return null;
        }
        AudioTranscriptTask task = audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
        return toStatusModel(task);
    }

    /**
     * 核心异步执行逻辑.
     * 若 task.failList 非空(上次有失败), 则只跑 failList 里的文件; 否则跑全部文件.
     * 本轮仍失败的文件收集到 failList, 全部成功则清空.
     */
    private void runTranscript(int classId, String examName) {
        try {
            // 0.读取任务记录, 判断首次还是重试
            AudioTranscriptTask task = audioTranscriptTaskDao.queryByClassAndExam(classId, examName);
            List<FailedFileInfo> previousFails = parseFailList(null == task ? null : task.getFailList());

            // 1.确定本轮要处理的文件列表
            //    fileMetas: 待提交识别的文件信息(studentId, groupId, puzzleIdx, fileName)
            List<FileMeta> fileMetas;
            if (!previousFails.isEmpty()) {
                // 重试模式: 只跑上次失败的文件, 同时回填 audio_url/tag
                log.info("重试模式 classId={} examName={}, 只跑失败文件 {} 个", classId, examName, previousFails.size());
                fileMetas = rebuildMetasFromFails(previousFails, examName);
            } else {
                // 首次模式: 收集全部学生的音频文件，删除已经存在的识别结果
                audioTranscriptsDao.deleteByClassAndExam(classId, examName);
                fileMetas = collectAllFiles(classId, examName);
            }

            // 2.为每个文件提交 NLS 识别任务, 同时记录提交失败的文件
            List<PendingTask> pending = new ArrayList<>();
            List<FailedFileInfo> currentFails = new ArrayList<>();
            for (FileMeta meta : fileMetas) {
                String url = aLiYunOssService.gerAudioUrl(meta.fileName);
                String taskId = aliyunTranscriptsService.submitTask(url);
                if (null == taskId) {
                    log.warn("提交识别任务失败: {}", meta.fileName);
                    currentFails.add(toFailedFileInfo(meta));
                    continue;
                }
                pending.add(new PendingTask(meta, taskId));
            }

            int total = pending.size();
            audioTranscriptTaskDao.updateCounts(classId, examName, total, 0);
            if (total == 0) {
                // 全部提交失败(或没有文件)则立即完成任务
                if (currentFails.isEmpty()) {
                    audioTranscriptTaskDao.markDone(classId, examName);
                } else {
                    audioTranscriptTaskDao.markFailed(classId, examName,
                            "全部文件提交失败: " + currentFails.size(), JSON.toJSONString(currentFails));
                }
                return;
            }

            // 3.轮转轮询, 让所有任务在阿里云侧并行执行
            long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
            int doneCount = 0;
            while (!pending.isEmpty() && System.currentTimeMillis() < deadline) {
                Iterator<PendingTask> it = pending.iterator();
                while (it.hasNext()) {
                    PendingTask pt = it.next();
                    TranscriptPollResult result = aliyunTranscriptsService.pollTaskOnce(pt.taskId);
                    if (result.isCompleted()) {
                        // 落库: 识别文本(失败时写空串, 保持有记录)
                        String text = (null == result.getText()) ? "" : result.getText();
                        saveTranscript(classId, pt, text);
                        doneCount++;
                        audioTranscriptTaskDao.updateCounts(classId, examName, total, doneCount);
                        it.remove();
                    }
                }
                if (!pending.isEmpty()) {
                    try {
                        Thread.sleep(POLL_ROUND_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // 4.超时未完成的加入失败列表
            for (PendingTask pt : pending) {
                currentFails.add(toFailedFileInfo(pt.meta));
            }

            // 5.根据失败列表更新状态
            if (currentFails.isEmpty()) {
                // 全部成功, 清空 failList
                audioTranscriptTaskDao.markDone(classId, examName);
            } else {
                // 仍有失败, 记录到 failList 供下次重试
                audioTranscriptTaskDao.markFailed(classId, examName,
                        "部分文件未完成: " + currentFails.size() + "/" + (total + currentFails.size() - doneCount),
                        JSON.toJSONString(currentFails));
            }
        } catch (Exception e) {
            log.error("音频转录任务异常 classId={} examName={}", classId, examName, e);
            audioTranscriptTaskDao.markFailed(classId, examName, "任务异常: " + e.getMessage(), null);
        }
    }

    /**
     * 增量同步识别: 收集该班级某次考试的全部音频, 过滤掉已入库 audio_url 的文件, 只转录增量部分.
     * 与 runTranscript 的区别: 不删除已有转录记录; total_count 只统计增量文件数.
     */
    private void runSyncTranscript(int classId, String examName) {
        try {
            // 1.收集全部学生的音频文件(已带 audioUrl 与 tag)
            List<FileMeta> allMetas = collectAllFiles(classId, examName);
            // 2.查已入库的 audio_url 集合, 过滤出增量文件
            Set<String> existingUrls = audioTranscriptsDao.queryAudioUrlsByClassAndExam(classId, examName);
            List<FileMeta> fileMetas = new ArrayList<>();
            for (FileMeta meta : allMetas) {
                if (!existingUrls.contains(meta.audioUrl)) {
                    fileMetas.add(meta);
                }
            }
            log.info("增量同步 classId={} examName={}, 全部 {} 个, 已入库 {} 个, 增量 {} 个",
                    classId, examName, allMetas.size(), existingUrls.size(), fileMetas.size());

            // 3.为每个增量文件提交 NLS 识别任务
            List<PendingTask> pending = new ArrayList<>();
            List<FailedFileInfo> currentFails = new ArrayList<>();
            for (FileMeta meta : fileMetas) {
                String url = aLiYunOssService.gerAudioUrl(meta.fileName);
                String taskId = aliyunTranscriptsService.submitTask(url);
                if (null == taskId) {
                    log.warn("提交识别任务失败: {}", meta.fileName);
                    currentFails.add(toFailedFileInfo(meta));
                    continue;
                }
                pending.add(new PendingTask(meta, taskId));
            }

            // total/doneCount 在已有基础上累加:
            //   total = 已入库数 + 本轮成功提交的增量数
            //   doneCount 从已入库数起步, 随增量文件完成递增到 total
            int baseDone = existingUrls.size();
            int total = baseDone + pending.size();
            int doneCount = baseDone;
            audioTranscriptTaskDao.updateCounts(classId, examName, total, doneCount);
            if (pending.isEmpty()) {
                // 无可轮询任务(无增量或全部提交失败)则立即结束
                if (currentFails.isEmpty()) {
                    audioTranscriptTaskDao.markDone(classId, examName);
                } else {
                    audioTranscriptTaskDao.markFailed(classId, examName,
                            "全部增量文件提交失败: " + currentFails.size(), JSON.toJSONString(currentFails));
                }
                return;
            }

            // 4.轮转轮询, 让所有任务在阿里云侧并行执行(doneCount 已从 baseDone 起步)
            long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
            while (!pending.isEmpty() && System.currentTimeMillis() < deadline) {
                Iterator<PendingTask> it = pending.iterator();
                while (it.hasNext()) {
                    PendingTask pt = it.next();
                    TranscriptPollResult result = aliyunTranscriptsService.pollTaskOnce(pt.taskId);
                    if (result.isCompleted()) {
                        String text = (null == result.getText()) ? "" : result.getText();
                        saveTranscript(classId, pt, text);
                        doneCount++;
                        audioTranscriptTaskDao.updateCounts(classId, examName, total, doneCount);
                        it.remove();
                    }
                }
                if (!pending.isEmpty()) {
                    try {
                        Thread.sleep(POLL_ROUND_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // 5.超时未完成的加入失败列表
            for (PendingTask pt : pending) {
                currentFails.add(toFailedFileInfo(pt.meta));
            }

            // 6.根据失败列表更新状态
            if (currentFails.isEmpty()) {
                audioTranscriptTaskDao.markDone(classId, examName);
            } else {
                audioTranscriptTaskDao.markFailed(classId, examName,
                        "部分增量文件未完成: " + currentFails.size() + "/" + (total + currentFails.size() - doneCount),
                        JSON.toJSONString(currentFails));
            }
        } catch (Exception e) {
            log.error("增量同步转录任务异常 classId={} examName={}", classId, examName, e);
            audioTranscriptTaskDao.markFailed(classId, examName, "任务异常: " + e.getMessage(), null);
        }
    }

    /**
     * 首次模式: 收集某班级某次考试的所有学生音频文件.
     */
    private List<FileMeta> collectAllFiles(int classId, String examName) {
        List<GroupInstanceModel> instances = groupService.queryGroupInstanceByClassIdAndExamName(classId, examName);
        Map<String, Integer> studentGroupMap = new HashMap<>();
        for (GroupInstanceModel instance : instances) {
            if (null == instance.getStudentsId()) {
                continue;
            }
            for (String studentId : instance.getStudentsId()) {
                studentGroupMap.put(studentId, instance.getGroupId());
            }
        }

        List<FileMeta> fileMetas = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : studentGroupMap.entrySet()) {
            String studentId = entry.getKey();
            int groupId = entry.getValue();
            int studentIdInt;
            try {
                studentIdInt = Integer.parseInt(studentId);
            } catch (NumberFormatException e) {
                log.warn("学号非数字, 跳过: {}", studentId);
                continue;
            }
            // B: 该生该考试全部 feedback_puzzle 记录(不限 deadline), 按 puzzleIdx 分组, 用于回填 tag.
            //   历史/已结束考试的 deadline 多已过期, 现有 queryByStudentId 带 deadline > NOW() 会漏掉, 故用 queryAllByStudentAndExam.
            List<FeedBackPuzzle> bAll = feedBackPuzzleDao.queryAllByStudentAndExam(studentId, groupId, examName);
            Map<Integer, List<FeedBackPuzzle>> bByPuzzle = (null == bAll || bAll.isEmpty())
                    ? new HashMap<>()
                    : bAll.stream().collect(Collectors.groupingBy(FeedBackPuzzle::getPuzzleIdx));
            List<String> keys = aLiYunOssService.listAudioKeysByPrefix(AUDIO_DIR + studentId + "_" + examName + "_");
            for (String key : keys) {
                String fileName = key.startsWith(AUDIO_DIR) ? key.substring(AUDIO_DIR.length()) : key;
                Integer puzzleIdx = parsePuzzleIdx(fileName, examName);
                if (null == puzzleIdx) {
                    continue; // 不是本次考试的音频
                }
                String tag = resolveTag(fileName, bByPuzzle.get(puzzleIdx));
                fileMetas.add(new FileMeta(studentIdInt, groupId, examName, puzzleIdx, fileName, key, tag));
            }
        }
        return fileMetas;
    }

    /**
     * 解析 fail_list JSON 字符串为 FailedFileInfo 列表.
     */
    private List<FailedFileInfo> parseFailList(String failListJson) {
        if (null == failListJson || failListJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return JSON.parseArray(failListJson, FailedFileInfo.class);
        } catch (Exception e) {
            log.warn("解析 fail_list 失败: {}", failListJson, e);
            return Collections.emptyList();
        }
    }

    private FailedFileInfo toFailedFileInfo(FileMeta meta) {
        return new FailedFileInfo(meta.studentId, meta.groupId, meta.puzzleIdx, meta.fileName);
    }

    /**
     * 写入识别结果. 重新识别场景下先删旧记录再插, 保证幂等.
     */
    private void saveTranscript(int classId, PendingTask task, String text) {
        try {
            // 直接插入, 不做去重: 同一学生同一题可能有多个音频文件, 每个音频的识别结果都应保留.
            audioTranscriptsDao.insert(AudioTranscript.builder()
                    .classId(classId)
                    .studentId(task.meta.studentId)
                    .groupId(task.meta.groupId)
                    .examName(task.meta.examName)
                    .puzzleIdx(task.meta.puzzleIdx)
                    .transcriptText(text)
                    .audioUrl(task.meta.audioUrl)
                    .tag(task.meta.tag)
                    .build());
        } catch (Exception e) {
            log.error("写入转录结果失败 student={} exam={} puzzle={}",
                    task.meta.studentId, task.meta.examName, task.meta.puzzleIdx, e);
        }
    }

    /**
     * 从文件名解析 puzzleIdx.
     * 文件名格式: {studentId}_{examName}_{puzzleIdx}_{ts}_{originName}
     * 跳过 studentId 和 examName 段后, 下一段即 puzzleIdx.
     */
    private Integer parsePuzzleIdx(String fileName, String examName) {
        // 必须以 {studentId}_{examName}_ 开头才算本次考试音频
        int examStart = fileName.indexOf('_');
        if (examStart < 0) {
            return null;
        }
        String rest = fileName.substring(examStart + 1);
        if (!rest.startsWith(examName + "_")) {
            return null;
        }
        rest = rest.substring(examName.length() + 1);
        int under = rest.indexOf('_');
        if (under < 0) {
            return null;
        }
        try {
            return Integer.parseInt(rest.substring(0, under));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 重试模式: 从 fail_list 重建 FileMeta, 同时回填 audio_url(完整 OSS key) 与 tag.
     * 按 studentId 分组, 每个学生只查一次 feedback_puzzle_table(B), 避免逐文件查库.
     */
    private List<FileMeta> rebuildMetasFromFails(List<FailedFileInfo> fails, String examName) {
        Map<String, List<FailedFileInfo>> failsByStudent = new HashMap<>();
        for (FailedFileInfo f : fails) {
            failsByStudent.computeIfAbsent(String.valueOf(f.getStudentId()), k -> new ArrayList<>()).add(f);
        }
        List<FileMeta> fileMetas = new ArrayList<>();
        for (Map.Entry<String, List<FailedFileInfo>> e : failsByStudent.entrySet()) {
            String studentId = e.getKey();
            List<FailedFileInfo> studentFails = e.getValue();
            int studentIdInt = studentFails.get(0).getStudentId();
            int groupId = studentFails.get(0).getGroupId();
            List<FeedBackPuzzle> bAll = feedBackPuzzleDao.queryAllByStudentAndExam(studentId, groupId, examName);
            Map<Integer, List<FeedBackPuzzle>> bByPuzzle = (null == bAll || bAll.isEmpty())
                    ? new HashMap<>()
                    : bAll.stream().collect(Collectors.groupingBy(FeedBackPuzzle::getPuzzleIdx));
            for (FailedFileInfo f : studentFails) {
                String fileName = f.getFileName();
                String tag = resolveTag(fileName, bByPuzzle.get(f.getPuzzleIdx()));
                fileMetas.add(new FileMeta(studentIdInt, groupId, examName, f.getPuzzleIdx(), fileName, AUDIO_DIR + fileName, tag));
            }
        }
        return fileMetas;
    }

    /**
     * 为单个 OSS 音频文件解析 tag: 用文件名与 feedback_puzzle_table 中同参数记录的 feedBackAudio 匹配.
     * feedBackAudio 现为公共读 URL(无查询参数), 以文件名为结尾, 归一化后直接比对文件名即可.
     * 命中则取该记录的 tag(examName 或 yyMMdd); 否则视为幽灵音频(学生上传但未最终提交), 返回 null.
     */
    private String resolveTag(String fileName, List<FeedBackPuzzle> bRecords) {
        if (null == bRecords) {
            return null;
        }
        for (FeedBackPuzzle b : bRecords) {
            String bFileName = decodeToFileName(b.getFeedBackAudio());
            if (null != bFileName && bFileName.equals(fileName)) {
                return b.getTag();
            }
        }
        return null;
    }

    /**
     * 将公共读 URL 或完整 OSS key 归一化为不带目录前缀的文件名, 并 URL 解码.
     * feedBackAudio 现为公共读 URL(无 Expires/Signature 等查询参数), 末尾即文件名,
     * 这里去掉域名与目录前缀取最后一段, 再解码以兼容路径中可能残留的 URL 编码(如中文 originalFilename).
     */
    private String decodeToFileName(String keyOrUrl) {
        if (null == keyOrUrl || keyOrUrl.isEmpty()) {
            return null;
        }
        String s = keyOrUrl;
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q); // 去查询串(Expires/Signature 等)
        }
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1); // 去目录前缀
        }
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s; // 解码失败则用原始字符串
        }
    }

    private boolean hasPermission(int classId, String ownerNumber) {
        ClassInfo classInfo = classInfoDao.queryClassByIdAndOwner(classId, ownerNumber);
        return null != classInfo;
    }

    /**
     * 任务实体 -> 前端状态模型, 并填充按钮提示文案.
     */
    private AudioTranscriptTaskStatusModel toStatusModel(AudioTranscriptTask task) {
        if (null == task) {
            // 从未跑过
            return AudioTranscriptTaskStatusModel.builder()
                    .status("NONE")
                    .totalCount(0)
                    .doneCount(0)
                    .hint("未完成, 开始识别")
                    .build();
        }
        String hint;
        switch (task.getStatus()) {
            case "RUNNING":
                hint = "识别中...";
                break;
            case "DONE":
                hint = "已完成, 重新识别";
                break;
            case "FAILED":
                hint = "识别失败, 重新识别";
                break;
            default:
                hint = "未完成, 开始识别";
        }
        return AudioTranscriptTaskStatusModel.builder()
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .doneCount(task.getDoneCount())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .errorMessage(task.getErrorMessage())
                .hint(hint)
                .build();
    }

    /**
     * 音频文件元信息(待识别或已识别的上下文).
     */
    private static class FileMeta {
        final int studentId;
        final int groupId;
        final String examName;
        final int puzzleIdx;
        final String fileName;
        final String audioUrl; // 完整 OSS key(含 feed_back/ 前缀), 落库到 audio_transcripts.audio_url
        final String tag;      // 音频来源: examName / yyMMdd / null(幽灵, 未在 feedback_puzzle_table 中)

        FileMeta(int studentId, int groupId, String examName, int puzzleIdx, String fileName, String audioUrl, String tag) {
            this.studentId = studentId;
            this.groupId = groupId;
            this.examName = examName;
            this.puzzleIdx = puzzleIdx;
            this.fileName = fileName;
            this.audioUrl = audioUrl;
            this.tag = tag;
        }
    }

    /**
     * 一个已提交 NLS 识别任务的音频文件.
     */
    private static class PendingTask {
        final FileMeta meta;
        final String taskId;

        PendingTask(FileMeta meta, String taskId) {
            this.meta = meta;
            this.taskId = taskId;
        }
    }
}
