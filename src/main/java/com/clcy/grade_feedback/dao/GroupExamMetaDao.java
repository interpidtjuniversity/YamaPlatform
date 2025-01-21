package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.GroupExamMeta;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface GroupExamMetaDao {

    /**
     * 查询某个分组某次考试的元信息
     * */
    GroupExamMeta queryGroupExamMeta(@Param("groupId") int groupId, @Param("examName") String examName);

    /**
     * 查询某个分组所有考试的元信息
     * */
    List<GroupExamMeta> batchQueryGroupExamMeta(@Param("groupId") int groupId);

}
