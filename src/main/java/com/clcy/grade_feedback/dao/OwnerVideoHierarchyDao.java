package com.clcy.grade_feedback.dao;

import com.clcy.grade_feedback.entity.OwnerVideoHierarchyRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Read-only queries for the owner's complete class, group, and exam hierarchy.
 */
public interface OwnerVideoHierarchyDao {

    // 查级联
    List<OwnerVideoHierarchyRow> queryByOwnerNumber(@Param("ownerNumber") String ownerNumber);
}
