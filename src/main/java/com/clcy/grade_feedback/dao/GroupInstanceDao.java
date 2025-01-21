package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.GroupInstance;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GroupInstanceDao {

    int createGroupInstance(@Param("instance") GroupInstance instance);

    List<GroupInstance> queryInstanceByGroupId(@Param("groupId") int groupId);

    int updateGroupInstanceExam(@Param("groupId") int groupId, @Param("oldExamName") String oldExamName, @Param("newExamName") String newExamName);
}
