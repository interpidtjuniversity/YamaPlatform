package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.GroupClassInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GroupClassInfoDao {

    int createGroupsForClass(@Param("groups") List<GroupClassInfo> groups);

    List<GroupClassInfo> queryGroupsInClass(@Param("classId") int classId);

    GroupClassInfo queryGroupClass(@Param("groupId") int groupId);
}
