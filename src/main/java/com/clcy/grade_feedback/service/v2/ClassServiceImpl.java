package com.clcy.grade_feedback.service.v2;

import com.clcy.grade_feedback.dao.ClassInfoDao;
import com.clcy.grade_feedback.dao.StudentClassInfoDao;
import com.clcy.grade_feedback.entity.ClassInfo;
import com.clcy.grade_feedback.entity.StudentClassInfo;
import com.clcy.grade_feedback.model.v2.ClassInfoModel;
import com.clcy.grade_feedback.model.v2.StudentClassInfoModel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClassServiceImpl implements ClassService {

    @Resource
    private ClassInfoDao classInfoDao;

    @Resource
    private StudentClassInfoDao studentClassInfoDao;

    @Override
    public int createClass(ClassInfoModel classModel) {
        return classInfoDao.createClass(classModel.getClassName(), classModel.getOwnerNumber());
    }

    @Override
    public int addStudentsToClass(StudentClassInfoModel infoModel) {
        List<StudentClassInfo> infos = new ArrayList<>();
        infoModel.getStudents().forEach((studentId, studentName) -> {
            infos.add(StudentClassInfo.builder()
                    .classId(infoModel.getClassId())
                    .className(infoModel.getClassName())
                    .studentId(studentId)
                    .studentName(studentName)
                    .build());
        });
        return studentClassInfoDao.batchInsertClassStudents(infos);
    }

    @Override
    public List<ClassInfoModel> queryClassForOwner(String ownerNumber) {
        List<ClassInfo> infos = classInfoDao.queryClassForOwner(ownerNumber);
        return infos.stream().map(info -> ClassInfoModel.builder()
                .id(info.getId())
                .className(info.getClassName())
                .ownerNumber(info.getOwnerNumber())
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<StudentClassInfoModel> queryStudentClasses(String studentId) {
        List<StudentClassInfo> infos = studentClassInfoDao.queryStudentClasses(studentId);
        return infos.stream().map(info -> StudentClassInfoModel.builder()
                .classId(info.getClassId())
                .className(info.getClassName())
                .build()).collect(Collectors.toList());
    }

    @Override
    public StudentClassInfoModel queryClassStudents(int classId) {
        List<StudentClassInfo> infos = studentClassInfoDao.queryClassStudents(classId);
        Map<String, String> studentsMap = new HashMap<>();
        infos.forEach(info -> {
            studentsMap.put(info.getStudentId(), info.getStudentName());
        });
        return StudentClassInfoModel.builder().students(studentsMap).build();
    }

}
