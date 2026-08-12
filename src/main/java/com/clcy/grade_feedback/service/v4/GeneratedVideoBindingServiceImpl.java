package com.clcy.grade_feedback.service.v4;

import com.clcy.grade_feedback.dao.OwnerVideoHierarchyDao;
import com.clcy.grade_feedback.dao.pg.GeneratedVideoBindingDao;
import com.clcy.grade_feedback.dao.pg.VideoConversationDao;
import com.clcy.grade_feedback.entity.GeneratedVideoBinding;
import com.clcy.grade_feedback.entity.OwnerVideoHierarchyRow;
import com.clcy.grade_feedback.entity.VideoConversationRecord;
import com.clcy.grade_feedback.model.v4.GeneratedVideoBindingQueryResult;
import com.clcy.grade_feedback.model.v4.GeneratedVideoBindingRequest;
import com.clcy.grade_feedback.model.v4.GeneratedVideoBindingSaveResult;
import com.clcy.grade_feedback.model.v4.GeneratedVideoBindingTreeModel;
import com.clcy.grade_feedback.model.v4.VideoBindingClassNode;
import com.clcy.grade_feedback.model.v4.VideoBindingClassRequest;
import com.clcy.grade_feedback.model.v4.VideoBindingExamNode;
import com.clcy.grade_feedback.model.v4.VideoBindingGroupNode;
import com.clcy.grade_feedback.model.v4.VideoBindingGroupRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生成视频绑定业务实现. MySQL 提供 owner 的合法层级，PostgreSQL 保存视频及其绑定关系.
 */
@Service
public class GeneratedVideoBindingServiceImpl implements GeneratedVideoBindingService {

    private static final Logger log = LoggerFactory.getLogger(GeneratedVideoBindingServiceImpl.class);
    private static final int MAX_VIDEO_NAME_LENGTH = 255;
    private static final int MAX_BINDING_COUNT = 10000;

    @Autowired
    private OwnerVideoHierarchyDao ownerVideoHierarchyDao;

    @Autowired
    private VideoConversationDao videoConversationDao;

    @Autowired
    private GeneratedVideoBindingDao generatedVideoBindingDao;

    @Autowired
    @Qualifier("pgTransactionTemplate")
    private TransactionTemplate pgTransactionTemplate;

    @Override
    public GeneratedVideoBindingSaveResult replaceBindings(String ownerNumber,
                                                            GeneratedVideoBindingRequest request) {
        String requestError = validateBasicRequest(ownerNumber, request);
        if (null != requestError) {
            return saveFailure(requestError);
        }

        Hierarchy hierarchy = buildHierarchy(ownerNumber);
        ExpansionResult expansion = expandAndValidate(request.getClassInfo(), hierarchy);
        if (null != expansion.errorMessage) {
            return saveFailure(expansion.errorMessage);
        }
        if (expansion.paths.size() > MAX_BINDING_COUNT) {
            return saveFailure("单次视频绑定数量不能超过" + MAX_BINDING_COUNT);
        }

        try {
            GeneratedVideoBindingSaveResult result = pgTransactionTemplate.execute(status -> {
                VideoConversationRecord video = videoConversationDao.queryByUserAndScriptNameForUpdate(
                        ownerNumber, request.getScriptName().trim());
                String videoError = validateVideo(video, request.getVideoUrl());
                if (null != videoError) {
                    return saveFailure(videoError);
                }

                List<GeneratedVideoBinding> bindings = toBindings(
                        expansion.paths, video, ownerNumber, request.getVideoName().trim());
                generatedVideoBindingDao.deleteByVideoConversationId(video.getId());
                int inserted = generatedVideoBindingDao.batchInsert(bindings);
                return GeneratedVideoBindingSaveResult.builder()
                        .success(Boolean.TRUE)
                        .message("视频绑定保存成功")
                        .savedCount(inserted)
                        .build();
            });
            return null == result ? saveFailure("视频绑定保存失败") : result;
        } catch (DataAccessException e) {
            log.error("覆盖保存视频绑定失败, ownerNumber={}, scriptName={}",
                    ownerNumber, request.getScriptName(), e);
            return saveFailure("视频绑定保存失败，原绑定信息已保留");
        } catch (RuntimeException e) {
            log.error("覆盖保存视频绑定异常, ownerNumber={}, scriptName={}",
                    ownerNumber, request.getScriptName(), e);
            return saveFailure("视频绑定保存失败，原绑定信息已保留");
        }
    }

    @Override
    public GeneratedVideoBindingQueryResult queryBindingTree(String ownerNumber, String scriptName) {
        if (!StringUtils.hasText(ownerNumber) || !StringUtils.hasText(scriptName)) {
            return queryNotFound("视频生成任务不存在");
        }
        VideoConversationRecord video = videoConversationDao.queryByUserAndScriptName(
                ownerNumber, scriptName.trim());
        if (null == video) {
            return queryNotFound("视频生成任务不存在");
        }

        List<GeneratedVideoBinding> bindings = generatedVideoBindingDao.queryByVideoConversationId(video.getId());
        Hierarchy hierarchy = buildHierarchy(ownerNumber);
        markBindings(hierarchy, bindings);

        String videoName = bindings.isEmpty() ? null : bindings.get(0).getVideoName();
        GeneratedVideoBindingTreeModel tree = GeneratedVideoBindingTreeModel.builder()
                .scriptName(video.getScriptName())
                .videoName(videoName)
                .videoUrl(video.getVideoUrl())
                .classInfo(new ArrayList<>(hierarchy.classNodes.values()))
                .build();
        return GeneratedVideoBindingQueryResult.builder()
                .found(Boolean.TRUE)
                .message("查询成功")
                .bindingTree(tree)
                .build();
    }

    private String validateBasicRequest(String ownerNumber, GeneratedVideoBindingRequest request) {
        if (!StringUtils.hasText(ownerNumber) || null == request) {
            return "请求参数无效";
        }
        if (!StringUtils.hasText(request.getScriptName())) {
            return "scriptName不能为空";
        }
        if (!StringUtils.hasText(request.getVideoName())) {
            return "videoName不能为空";
        }
        if (request.getVideoName().trim().length() > MAX_VIDEO_NAME_LENGTH) {
            return "videoName长度不能超过" + MAX_VIDEO_NAME_LENGTH + "个字符";
        }
        if (!StringUtils.hasText(request.getVideoUrl())) {
            return "videoUrl不能为空";
        }
        return null;
    }

    private String validateVideo(VideoConversationRecord video, String requestVideoUrl) {
        if (null == video) {
            return "视频生成任务不存在";
        }
        if (!"COMPLETED".equals(video.getStatus())) {
            return "视频尚未生成完成，不能保存绑定信息";
        }
        if (!StringUtils.hasText(video.getVideoUrl())) {
            return "视频任务没有可用的视频地址";
        }
        if (!video.getVideoUrl().equals(requestVideoUrl.trim())) {
            return "videoUrl与视频生成任务不匹配";
        }
        return null;
    }

    private ExpansionResult expandAndValidate(List<VideoBindingClassRequest> classRequests,
                                               Hierarchy hierarchy) {
        if (null == classRequests || classRequests.isEmpty()) {
            return new ExpansionResult(Collections.emptyList(), null);
        }
        LinkedHashMap<String, BindingPath> uniquePaths = new LinkedHashMap<>();
        for (VideoBindingClassRequest classRequest : classRequests) {
            if (null == classRequest || null == classRequest.getClassId()) {
                return new ExpansionResult(null, "classId不能为空");
            }
            int classId = classRequest.getClassId();
            if (!hierarchy.classIds.contains(classId)) {
                return new ExpansionResult(null, "无权访问班级或班级不存在: " + classId);
            }
            List<VideoBindingGroupRequest> groupRequests = classRequest.getGroupInfo();
            if (null == groupRequests || groupRequests.isEmpty()) {
                putPath(uniquePaths, new BindingPath(classId, null, null));
                continue;
            }
            for (VideoBindingGroupRequest groupRequest : groupRequests) {
                if (null == groupRequest || null == groupRequest.getGroupId()) {
                    return new ExpansionResult(null, "groupId不能为空");
                }
                int groupId = groupRequest.getGroupId();
                if (!hierarchy.groupKeys.contains(groupKey(classId, groupId))) {
                    return new ExpansionResult(null,
                            "分组不属于指定班级或分组不存在: classId=" + classId + ", groupId=" + groupId);
                }
                List<String> examNames = groupRequest.getExamNameInfo();
                if (null == examNames || examNames.isEmpty()) {
                    putPath(uniquePaths, new BindingPath(classId, groupId, null));
                    continue;
                }
                for (String examName : examNames) {
                    if (!StringUtils.hasText(examName)) {
                        return new ExpansionResult(null, "examName不能为空");
                    }
                    String normalizedExamName = examName.trim();
                    if (!hierarchy.examKeys.contains(examKey(classId, groupId, normalizedExamName))) {
                        return new ExpansionResult(null,
                                "测试不属于指定班级和分组: classId=" + classId
                                        + ", groupId=" + groupId + ", examName=" + normalizedExamName);
                    }
                    putPath(uniquePaths, new BindingPath(classId, groupId, normalizedExamName));
                }
            }
        }
        List<BindingPath> paths = new ArrayList<>(uniquePaths.values());
        String scopeConflict = validateScopeConflicts(paths);
        if (null != scopeConflict) {
            return new ExpansionResult(null, scopeConflict);
        }
        return new ExpansionResult(paths, null);
    }

    /**
     * 绑定记录表达最小可见范围，同一班级内不能同时保存父范围和其子范围：
     * 班级公共绑定排斥该班级的所有分组/测试绑定；分组公共绑定排斥该分组的测试绑定。
     */
    private String validateScopeConflicts(List<BindingPath> paths) {
        Set<Integer> classLevelBindings = new HashSet<>();
        Set<String> groupLevelBindings = new HashSet<>();
        for (BindingPath path : paths) {
            if (null == path.groupId) {
                classLevelBindings.add(path.classId);
            } else if (null == path.examName) {
                groupLevelBindings.add(groupKey(path.classId, path.groupId));
            }
        }
        for (BindingPath path : paths) {
            if (null != path.groupId && classLevelBindings.contains(path.classId)) {
                return "同一班级不能同时绑定班级公共范围和分组/测试范围: classId=" + path.classId;
            }
            if (null != path.examName
                    && groupLevelBindings.contains(groupKey(path.classId, path.groupId))) {
                return "同一分组不能同时绑定分组公共范围和测试范围: classId="
                        + path.classId + ", groupId=" + path.groupId;
            }
        }
        return null;
    }

    private List<GeneratedVideoBinding> toBindings(List<BindingPath> paths,
                                                   VideoConversationRecord video,
                                                   String ownerNumber,
                                                   String videoName) {
        List<GeneratedVideoBinding> result = new ArrayList<>(paths.size());
        for (BindingPath path : paths) {
            result.add(GeneratedVideoBinding.builder()
                    .videoConversationId(video.getId())
                    .ownerNumber(ownerNumber)
                    .classId(path.classId)
                    .groupId(path.groupId)
                    .examName(path.examName)
                    .scriptName(video.getScriptName())
                    .videoName(videoName)
                    .videoUrl(video.getVideoUrl())
                    .build());
        }
        return result;
    }

    private Hierarchy buildHierarchy(String ownerNumber) {
        Hierarchy hierarchy = new Hierarchy();
        List<OwnerVideoHierarchyRow> rows = ownerVideoHierarchyDao.queryByOwnerNumber(ownerNumber);
        if (null == rows) {
            return hierarchy;
        }
        for (OwnerVideoHierarchyRow row : rows) {
            if (null == row || null == row.getClassId()) {
                continue;
            }
            int classId = row.getClassId();
            hierarchy.classIds.add(classId);
            VideoBindingClassNode classNode = hierarchy.classNodes.computeIfAbsent(classId,
                    id -> VideoBindingClassNode.builder()
                            .classId(id)
                            .className(row.getClassName())
                            .selected(Boolean.FALSE)
                            .directlyBound(Boolean.FALSE)
                            .groupInfo(new ArrayList<>())
                            .build());
            if (null == row.getGroupId()) {
                continue;
            }
            int groupId = row.getGroupId();
            String groupKey = groupKey(classId, groupId);
            hierarchy.groupKeys.add(groupKey);
            VideoBindingGroupNode groupNode = hierarchy.groupNodes.computeIfAbsent(groupKey,
                    key -> {
                        VideoBindingGroupNode node = VideoBindingGroupNode.builder()
                                .groupId(groupId)
                                .groupName(row.getGroupName())
                                .selected(Boolean.FALSE)
                                .directlyBound(Boolean.FALSE)
                                .examNameInfo(new ArrayList<>())
                                .build();
                        classNode.getGroupInfo().add(node);
                        return node;
                    });
            if (!StringUtils.hasText(row.getExamName())) {
                continue;
            }
            String examName = row.getExamName().trim();
            String examKey = examKey(classId, groupId, examName);
            hierarchy.examKeys.add(examKey);
            if (hierarchy.examNodes.containsKey(examKey)) {
                continue;
            }
            VideoBindingExamNode examNode = VideoBindingExamNode.builder()
                    .examName(examName)
                    .selected(Boolean.FALSE)
                    .build();
            hierarchy.examNodes.put(examKey, examNode);
            groupNode.getExamNameInfo().add(examNode);
        }
        return hierarchy;
    }

    private void markBindings(Hierarchy hierarchy, List<GeneratedVideoBinding> bindings) {
        for (GeneratedVideoBinding binding : bindings) {
            VideoBindingClassNode classNode = hierarchy.classNodes.get(binding.getClassId());
            if (null == classNode) {
                continue;
            }
            classNode.setSelected(Boolean.TRUE);
            // 单班级绑定
            if (null == binding.getGroupId()) {
                classNode.setDirectlyBound(Boolean.TRUE);
                continue;
            }
            VideoBindingGroupNode groupNode = hierarchy.groupNodes.get(
                    groupKey(binding.getClassId(), binding.getGroupId()));
            if (null == groupNode) {
                continue;
            }
            groupNode.setSelected(Boolean.TRUE);
            // 单组绑定，级联班级信息
            if (!StringUtils.hasText(binding.getExamName())) {
                groupNode.setDirectlyBound(Boolean.TRUE);
                continue;
            }
            VideoBindingExamNode examNode = hierarchy.examNodes.get(
                    examKey(binding.getClassId(), binding.getGroupId(), binding.getExamName()));
            if (null != examNode) {
                examNode.setSelected(Boolean.TRUE);
            }
        }
    }

    private void putPath(Map<String, BindingPath> paths, BindingPath path) {
        paths.putIfAbsent(examKey(path.classId, path.groupId, path.examName), path);
    }

    private String groupKey(int classId, int groupId) {
        return classId + "\u0001" + groupId;
    }

    private String examKey(int classId, Integer groupId, String examName) {
        return classId + "\u0001" + (null == groupId ? "" : groupId)
                + "\u0001" + (null == examName ? "" : examName);
    }

    private GeneratedVideoBindingSaveResult saveFailure(String message) {
        return GeneratedVideoBindingSaveResult.builder()
                .success(Boolean.FALSE)
                .message(message)
                .savedCount(0)
                .build();
    }

    private GeneratedVideoBindingQueryResult queryNotFound(String message) {
        return GeneratedVideoBindingQueryResult.builder()
                .found(Boolean.FALSE)
                .message(message)
                .bindingTree(null)
                .build();
    }

    private static class Hierarchy {
        private final Set<Integer> classIds = new HashSet<>();
        private final Set<String> groupKeys = new HashSet<>();
        private final Set<String> examKeys = new HashSet<>();
        private final LinkedHashMap<Integer, VideoBindingClassNode> classNodes = new LinkedHashMap<>();
        private final Map<String, VideoBindingGroupNode> groupNodes = new LinkedHashMap<>();
        private final Map<String, VideoBindingExamNode> examNodes = new LinkedHashMap<>();
    }

    private static class BindingPath {
        private final int classId;
        private final Integer groupId;
        private final String examName;

        private BindingPath(int classId, Integer groupId, String examName) {
            this.classId = classId;
            this.groupId = groupId;
            this.examName = examName;
        }
    }

    private static class ExpansionResult {
        private final List<BindingPath> paths;
        private final String errorMessage;

        private ExpansionResult(List<BindingPath> paths, String errorMessage) {
            this.paths = paths;
            this.errorMessage = errorMessage;
        }
    }
}
