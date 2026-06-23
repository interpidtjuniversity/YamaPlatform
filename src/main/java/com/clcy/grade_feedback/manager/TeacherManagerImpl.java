package com.clcy.grade_feedback.manager;

import com.clcy.grade_feedback.dao.ClassInfoDao;
import com.clcy.grade_feedback.dao.GroupExamMetaDao;
import com.clcy.grade_feedback.entity.ClassInfo;
import com.clcy.grade_feedback.entity.GroupExamMeta;
import com.clcy.grade_feedback.model.v2.*;
import com.clcy.grade_feedback.service.ALiYunOssService;
import com.clcy.grade_feedback.service.v2.ClassService;
import com.clcy.grade_feedback.service.v2.GroupService;
import com.clcy.grade_feedback.service.v4.AudioTranscriptTaskService;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeacherManagerImpl implements TeacherManager{

    @Autowired
    private ClassService classService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Resource
    private ClassInfoDao classInfoDao;

    @Resource
    private GroupExamMetaDao groupExamMetaDao;

    @Autowired
    private ALiYunOssService aLiYunOssService;

    @Autowired
    private AudioTranscriptTaskService audioTranscriptTaskService;

    // 音频文件在 OSS 上的目录前缀, 与 ALiYunOssServiceImpl.audioDir 保持一致
    private static final String AUDIO_DIR = "feed_back/";


    @Override
    public boolean createClass(OwnerClassModel createModel) {
        transactionTemplate.execute(status -> {
            try {
                // 1.创建班级
                int classId = classService.createClass(ClassInfoModel.builder().className(createModel.getClassName()).ownerNumber(createModel.getOwnerNumber()).build());
                // 2.创建班级内的学生
                StudentClassInfoModel studentClassInfoModel =
                        StudentClassInfoModel.builder()
                                .classId(classId)
                                .className(createModel.getClassName())
                                .students(createModel.getStudentClassInfoModel().getStudents())
                                .build();
                classService.addStudentsToClass(studentClassInfoModel);
                // 3.创建班级的分组
                createModel.getGroupClassInfoModel().forEach(groupClassInfoModel -> groupClassInfoModel.setClassId(classId));
                groupService.createGroupsForClass(createModel.getGroupClassInfoModel());
                // 4.回调分组策略 默认default的话创建两个初始分组

                // TODO 策略模式 reconstruct
                List<GroupClassInfoModel> groups = groupService.queryClassGroups(classId);
                Map<Integer, List<String>> partitionRatioMap = partitionListByRatio(new ArrayList<>(createModel.getStudentClassInfoModel().getStudents().keySet()), groups);
                groups.forEach(group -> {
                    if ("default".equalsIgnoreCase(group.getGroupingStrategy())) {
                        // 初始随机分组
                        groupService.createGroupInstance(GroupInstanceModel
                                .builder()
                                        .groupId(group.getGroupId())
                                        .groupName(group.getGroupName())
                                        .classId(group.getClassId())
                                        .className(group.getClassName())
                                        .examName("default")
                                        .studentsId(partitionRatioMap.get(group.getGroupId()))
                                .build());
                    }
                });

                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                return false;
            }
        });

        return true;
    }

    @Override
    public List<OwnerClassModel> queryClasses(String ownerNumber) {
        // 查询班级列表
        List<ClassInfoModel> ownerClasses = classService.queryClassForOwner(ownerNumber);
        return ownerClasses.stream().map(model -> {
            // 查询该班级下的学生列表
            StudentClassInfoModel studentClassInfoModel = classService.queryClassStudents(model.getClassId());
            // 查询该班级下的学生分组信息
            List<GroupClassInfoModel> groupClassInfoModels = groupService.queryClassGroups(model.getClassId());

            return OwnerClassModel.builder()
                    .ownerNumber(ownerNumber)
                    .classId(model.getClassId())
                    .className(model.getClassName())
                    .studentClassInfoModel(studentClassInfoModel)
                    .groupClassInfoModel(groupClassInfoModels)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<ClassExamStatModel> queryClassExams(int classId, String ownerNumber) {
        // 0.权限校验: 该班级必须属于当前登录用户
        ClassInfo classInfo = classInfoDao.queryClassByIdAndOwner(classId, ownerNumber);
        if (null == classInfo) {
            return null;
        }

        // 1.根据 classId 匹配所有 groupId(group_class_info)
        List<GroupClassInfoModel> groups = groupService.queryClassGroups(classId);
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }

        // 2.根据所有 groupId 聚合 examName -> 考试元信息(group_exam_meta)
        Map<String, Pair<Timestamp, Timestamp>> examMetaMap = new HashMap<>();
        for (GroupClassInfoModel group : groups) {
            List<GroupExamMeta> metas = groupExamMetaDao.batchQueryGroupExamMeta(group.getGroupId());
            for (GroupExamMeta meta : metas) {
                examMetaMap.putIfAbsent(meta.getExamName(), Pair.of(meta.getStartTime(), meta.getEndTime()));
            }
        }
        if (examMetaMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 3.根据 classId 找出所有学生学号(student_class_info)
        StudentClassInfoModel classStudents = classService.queryClassStudents(classId);
        Set<String> studentIds = (null == classStudents || null == classStudents.getStudents())
                ? Collections.emptySet()
                : classStudents.getStudents().keySet();

        // 4.遍历每个学生, 按学号前缀列举 OSS 音频文件, 在内存按 examName 聚合统计
        //    文件名格式: feed_back/{studentId}_{examName}_{puzzleIdx}_{ts}_{originName}
        //    studentId 为纯数字不含下划线, 故以第一个下划线拆分得到 examName 起始, 再与已知 examName 列表做前缀匹配.
        Map<String, Set<String>> examSubmitStudents = new HashMap<>(); // examName -> 去重学号集合
        Map<String, Integer> examSubmitCount = new HashMap<>();        // examName -> 文件条数
        for (String examName : examMetaMap.keySet()) {
            examSubmitStudents.put(examName, Sets.newHashSet());
            examSubmitCount.put(examName, 0);
        }

        for (String studentId : studentIds) {
            if (null == studentId || studentId.isEmpty()) {
                continue;
            }
            List<String> keys = aLiYunOssService.listAudioKeysByPrefix(AUDIO_DIR + studentId + "_");
            for (String key : keys) {
                // 去掉目录前缀, 得到文件名主体
                String fileName = key.startsWith(AUDIO_DIR) ? key.substring(AUDIO_DIR.length()) : key;
                // fileName = {studentId}_{examName}_{...}, 跳过学号段后剩余部分以 examName 开头才算命中
                int firstUnder = fileName.indexOf('_');
                if (firstUnder < 0) {
                    continue;
                }
                String rest = fileName.substring(firstUnder + 1);
                for (String examName : examMetaMap.keySet()) {
                    if (rest.startsWith(examName + "_")) {
                        examSubmitStudents.get(examName).add(studentId);
                        examSubmitCount.put(examName, examSubmitCount.get(examName) + 1);
                        break;
                    }
                }
            }
        }

        // 5.组装结果, 按开始时间倒序
        List<ClassExamStatModel> result = new ArrayList<>();
        for (Map.Entry<String, Pair<Timestamp, Timestamp>> entry : examMetaMap.entrySet()) {
            Pair<Timestamp, Timestamp> tRange = entry.getValue();
            result.add(ClassExamStatModel.builder()
                    .examName(entry.getKey())
                    .submitStudentCount(examSubmitStudents.get(entry.getKey()).size())
                    .submitCount(examSubmitCount.get(entry.getKey()))
                    .startTime(tRange.getLeft())
                    .endTime(tRange.getRight())
                    .transcriptStatus(audioTranscriptTaskService.queryStatusNoAuth(classId, entry.getKey()))
                    .build());
        }
        result.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));
        return result;
    }

    /**
     * @param list 是学生Map的keys
     * @param groups 是按照创建顺序排列好的班级分组
     * */
    public static <T> Map<Integer, List<T>> partitionListByRatio(List<T> list, List<GroupClassInfoModel> groups) {
        // TODO 检查比例总和是否为1.0
        int totalSize = list.size();
        // 随机打乱
        Collections.shuffle(list);

        Map<Integer, List<T>> ans = Maps.newHashMap();
        int startIndex = 0;

        // 按照比例计算每部分的起始和结束索引
        for (int i = 0; i < groups.size(); i++) {
            GroupClassInfoModel group = groups.get(i);
            double ratio = Integer.parseInt(group.getGroupingInfo().replace("%", "")) / 100.0;
            int partitionSize = (int) Math.round(totalSize * ratio); // 使用四舍五入
            if (i == groups.size() - 1) {
                partitionSize = totalSize - startIndex;
            }
            int endIndex = startIndex + partitionSize;
            ans.put(group.getGroupId(), list.subList(startIndex, endIndex));
            startIndex = endIndex;
        }
        return ans;
    }
}
