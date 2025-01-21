package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.GroupExamDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GroupExamDetailDao {

    /**
     * 查询某个分组某次考试的所有题目以及答案
     * */
    List<GroupExamDetail> queryGroupExamDetailByIdAndName(@Param("groupId") int groupId, @Param("examName") String examName);

    /**
     * 查询某个分组的所有题目以及答案
     * */
    List<GroupExamDetail> batchQueryGroupExamDetailById(@Param("groupId") int groupId);

}
