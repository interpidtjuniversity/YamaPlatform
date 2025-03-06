package com.clcy.grade_feedback.service.v2;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.clcy.grade_feedback.dao.GroupExamDetailDao;
import com.clcy.grade_feedback.dao.GroupExamMetaDao;
import com.clcy.grade_feedback.dao.GroupExamStudentAnswerRecordDao;
import com.clcy.grade_feedback.entity.GroupExamDetail;
import com.clcy.grade_feedback.entity.GroupExamMeta;
import com.clcy.grade_feedback.entity.GroupExamStudentAnswerRecord;
import com.clcy.grade_feedback.model.v2.GroupExamDetailModel;
import com.clcy.grade_feedback.model.v2.GroupExamMetaModel;
import com.clcy.grade_feedback.model.v2.GroupExamStudentAnswerRecordModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExamServiceImpl implements ExamService{

    @Resource
    private GroupExamDetailDao groupExamDetailDao;

    @Resource
    private GroupExamMetaDao groupExamMetaDao;

    @Resource
    private GroupExamStudentAnswerRecordDao groupExamStudentAnswerRecordDao;


    @Override
    public List<GroupExamMetaModel> queryGroupExamsMeta(int groupId) {
        List<GroupExamMeta> metas = groupExamMetaDao.batchQueryGroupExamMeta(groupId);
        return metas.stream().map(meta ->
                GroupExamMetaModel.builder()
                        .id(meta.getId())
                        .startTime(meta.getStartTime())
                        .endTime(meta.getEndTime())
                        .examName(meta.getExamName())
                        .duration(meta.getDuration())
                        .build()
        ).sorted(Comparator.comparingInt(GroupExamMetaModel::getId)).collect(Collectors.toList());
    }

    @Override
    public GroupExamMetaModel queryGroupExamMeta(int groupId, String examName) {
        GroupExamMeta meta = groupExamMetaDao.queryGroupExamMeta(groupId, examName);
        if (null != meta) {
            return GroupExamMetaModel.builder()
                    .id(meta.getId())
                    .startTime(meta.getStartTime())
                    .endTime(meta.getEndTime())
                    .build();
        }
        return null;
    }

    @Override
    public List<GroupExamDetailModel> queryGroupExamDetail(int groupId, String examName, boolean containsAnswer, boolean containsAnalysis) {
        List<GroupExamDetail> details = groupExamDetailDao.queryGroupExamDetailByIdAndName(groupId, examName, containsAnswer, containsAnalysis);
        return details.stream()
                .sorted(Comparator.comparingInt(GroupExamDetail::getPuzzleIdx))
                .map(detail -> GroupExamDetailModel
                        .builder()
                        .puzzleIdx(detail.getPuzzleIdx())
                        .content(detail.getContent())
                        .images(JSONArray.parseArray(detail.getImages(), String.class))
                        .choices(JSONArray.parseArray(detail.getChoices(), String.class))
                        .answer(detail.getAnswer())
                        .analysis(detail.getAnalysis())
                        .knowledgePoints(detail.getKnowledgePoints())
                        .build()
                ).collect(Collectors.toList());
    }

    @Override
    public List<GroupExamStudentAnswerRecordModel> queryStudentAnswerRecords(int classId, String studentId) {
        List<GroupExamStudentAnswerRecord> records = groupExamStudentAnswerRecordDao.queryStudentAnswerRecords(classId, studentId);
        return records.stream().map(record -> GroupExamStudentAnswerRecordModel
                .builder()
                .groupId(record.getGroupId())
                .groupName(record.getGroupName())
                .examName(record.getExamName())
                .answers(JSONObject.parseObject(record.getAnswers(), new TypeReference<Map<String, String>>() {}))
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    public GroupExamStudentAnswerRecordModel queryStudentAnswerRecord(int groupId, String examName, String studentId) {
        GroupExamStudentAnswerRecord record = groupExamStudentAnswerRecordDao.queryStudentAnswerRecord(groupId, examName, studentId);
        if (record != null) {
            return GroupExamStudentAnswerRecordModel
                    .builder()
                    .answers(JSONObject.parseObject(record.getAnswers(), new TypeReference<Map<String, String>>() {
                    }))
                    .build();
        }
        return null;
    }

    @Override
    public int addStudentAnswerRecord(GroupExamStudentAnswerRecordModel model) {
        GroupExamStudentAnswerRecord record = GroupExamStudentAnswerRecord
                .builder()
                .classId(model.getClassId())
                .className(model.getClassName())
                .groupId(model.getGroupId())
                .groupName(model.getGroupName())
                .examName(model.getExamName())
                .studentId(model.getStudentId())
                .studentName(model.getStudentName())
                .answers(JSONObject.toJSONString(model.getAnswers()))
                .clickNextTimeList(JSONObject.toJSONString(model.getClickNextTimeList()))
                .build();

        return groupExamStudentAnswerRecordDao.addStudentAnswerRecord(record);
    }
}
