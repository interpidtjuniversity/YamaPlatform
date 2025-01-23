package com.clcy.grade_feedback.manager;

import com.clcy.grade_feedback.model.v2.OwnerClassModel;

import java.util.List;

public interface TeacherManager {

    boolean createClass(OwnerClassModel createModel);

    List<OwnerClassModel> queryClasses(String ownerNumber);
}
