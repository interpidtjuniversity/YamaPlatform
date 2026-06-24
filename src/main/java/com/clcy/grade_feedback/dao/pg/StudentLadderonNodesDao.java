package com.clcy.grade_feedback.dao.pg;

import com.clcy.grade_feedback.entity.StudentLadderonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * student_ladderon_nodes 表的增删改查(PostgreSQL, JdbcTemplate).
 * 增量插入: node_text 已存在(对 classId+studentId+groupId+examName)则跳过.
 * 注意: 需先建唯一索引 uk_ladderon_node (class_id, student_id, group_id, exam_name, node_text).
 */
@Repository
public class StudentLadderonNodesDao {

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<StudentLadderonNode> ROW_MAPPER = (rs, rowNum) -> StudentLadderonNode.builder()
            .id(rs.getInt("id"))
            .classId(rs.getInt("class_id"))
            .studentId(rs.getInt("student_id"))
            .groupId(rs.getInt("group_id"))
            .examName(rs.getString("exam_name"))
            .nodeText(rs.getString("node_text"))
            .timestamp(rs.getTimestamp("timestamp"))
            .build();

    /**
     * 增量插入: 若 (class_id, student_id, node_text) 已存在则跳过.
     */
    public int insertIfAbsent(StudentLadderonNode node) {
        String sql = "insert into student_ladderon_nodes (class_id, student_id, group_id, exam_name, node_text, timestamp) "
                + "values (?, ?, ?, ?, ?, ?) "
                + "on conflict (class_id, student_id, node_text) do nothing";
        return jdbcTemplate.update(sql,
                node.getClassId(),
                node.getStudentId(),
                node.getGroupId(),
                node.getExamName(),
                node.getNodeText(),
                node.getTimestamp());
    }

    /**
     * 按 (class_id, student_id, exam_name) 查询该学生该考试的已提取节点.
     */
    public List<StudentLadderonNode> queryByStudentAndExam(int classId, int studentId, String examName) {
        String sql = "select id, class_id, student_id, group_id, exam_name, node_text, timestamp "
                + "from student_ladderon_nodes where class_id = ? and student_id = ? and exam_name = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, classId, studentId, examName);
    }

    /**
     * 查询某学生在多场考试中的全部 node_text(去重).
     * 用于超边提取: 收集 examName 之前(含)所有考试的节点作为候选输入.
     */
    public List<String> queryDistinctNodeTextsByStudentAndExams(int studentId, List<String> examNames) {
        if (null == examNames || examNames.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(examNames.size(), "?"));
        String sql = "select distinct node_text from student_ladderon_nodes "
                + "where student_id = ? and exam_name in (" + placeholders + ")";
        Object[] params = new Object[examNames.size() + 1];
        params[0] = studentId;
        for (int i = 0; i < examNames.size(); i++) {
            params[i + 1] = examNames.get(i);
        }
        return jdbcTemplate.queryForList(sql, String.class, params);
    }

    /**
     * 查询某学生在多场考试中的全部 node_text(去重).
     * 用于超边提取: 拿到 examName 之前(含)所有考试的节点列表.
     */
    public List<String> queryNodeTextsByStudentAndExams(int studentId, List<String> examNames) {
        if (null == examNames || examNames.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(examNames.size(), "?"));
        String sql = "select distinct node_text from student_ladderon_nodes "
                + "where student_id = ? and exam_name in (" + placeholders + ")";
        Object[] params = new Object[examNames.size() + 1];
        params[0] = studentId;
        for (int i = 0; i < examNames.size(); i++) {
            params[i + 1] = examNames.get(i);
        }
        return jdbcTemplate.queryForList(sql, String.class, params);
    }

    public List<String> findMissingLadderonNodes(int classId, int studentId, List<String> nodeTextList) {
        if (nodeTextList == null || nodeTextList.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用 NamedParameterJdbcTemplate 方便构造 IN 子句
        String sql = "SELECT node_text FROM (VALUES " +
                String.join(", ", Collections.nCopies(nodeTextList.size(), "(?)")) +
                ") AS t(node_text) " +
                "WHERE NOT EXISTS ( " +
                "    SELECT 1 FROM student_ladderon_nodes s " +
                "    WHERE s.class_id = ? AND s.student_id = ? AND s.node_text = t.node_text " +
                ")";

        // 构建参数列表：先放所有 node_text，再放 class_id, student_id
        List<Object> params = new ArrayList<>(nodeTextList);
        params.add(classId);
        params.add(studentId);

        return jdbcTemplate.queryForList(sql, String.class, params.toArray());
    }

    /**
     * 按 (class_id, exam_name) 删除全部节点, 用于重新提取前清空.
     */
    public int deleteByClassAndExam(int classId, String examName) {
        String sql = "delete from student_ladderon_nodes where class_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, classId, examName);
    }
}
