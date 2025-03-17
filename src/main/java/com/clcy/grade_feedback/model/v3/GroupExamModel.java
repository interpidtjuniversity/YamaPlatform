package com.clcy.grade_feedback.model.v3;

import com.clcy.grade_feedback.model.v2.GroupExamDetailModel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GroupExamModel {

    @Getter
    @Setter
    private List<GroupExamDetailModel> details;

    @Getter
    @Setter
    private SyncExamModel examState;

    @Getter
    @Setter
    private Map<String, String> answers;

    @Getter
    @Setter
    private List<Long> clickNextTimeList;

    @Getter
    @Setter
    private Integer currentPuzzleIdx;
}
