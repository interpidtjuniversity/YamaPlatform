package com.clcy.grade_feedback.service.v3;

import com.clcy.grade_feedback.model.v2.GroupExamMetaModel;
import com.clcy.grade_feedback.model.v3.SyncExamModel;
import com.clcy.grade_feedback.model.v3.SyncPuzzleModel;

import java.util.List;

public interface ExamStateService {

    SyncExamModel storeExamState(GroupExamMetaModel metaModel, SyncExamModel syncExamModel);

    SyncExamModel queryExamState(String studentId, int groupId, String examName);

    Boolean deleteExamState(String studentId, int groupId, String examName);

    Boolean syncPuzzleRecord(String studentId, GroupExamMetaModel metaModel, SyncPuzzleModel puzzleModel);

    Boolean deletePuzzleState(String studentId, int groupId, String examName);

    List<SyncPuzzleModel> queryPuzzleRecords(String studentId, int groupId, String examName);
}
