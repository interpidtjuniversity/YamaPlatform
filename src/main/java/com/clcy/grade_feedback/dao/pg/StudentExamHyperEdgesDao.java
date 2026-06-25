package com.clcy.grade_feedback.dao.pg;

import com.clcy.grade_feedback.entity.StudentExamHyperEdge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * student_exam_hyper_edges 表的增删改查(PostgreSQL, JdbcTemplate).
 */
@Repository
public class StudentExamHyperEdgesDao {

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<StudentExamHyperEdge> ROW_MAPPER = (rs, rowNum) -> StudentExamHyperEdge.builder()
            .id(rs.getInt("id"))
            .classId(rs.getInt("class_id"))
            .studentId(rs.getInt("student_id"))
            .groupId(rs.getInt("group_id"))
            .examName(rs.getString("exam_name"))
            .inputs(rs.getString("inputs"))
            .outputs(rs.getString("outputs"))
            .type(rs.getString("type"))
            .confidence(rs.getDouble("confidence"))
            .puzzleIndex(rs.getInt("puzzle_index"))
            .timestamp(rs.getTimestamp("timestamp"))
            .build();

    /**
     * 批量插入超边.
     */
    public void batchInsert(List<StudentExamHyperEdge> edges) {
        if (null == edges || edges.isEmpty()) {
            return;
        }
        String sql = "insert into student_exam_hyper_edges "
                + "(class_id, student_id, group_id, exam_name, inputs, outputs, type, confidence, puzzle_index, timestamp) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, edges, edges.size(), (ps, edge) -> {
            ps.setInt(1, edge.getClassId());
            ps.setInt(2, edge.getStudentId());
            ps.setInt(3, edge.getGroupId());
            ps.setString(4, edge.getExamName());
            ps.setString(5, edge.getInputs());
            ps.setString(6, edge.getOutputs());
            ps.setString(7, edge.getType());
            ps.setDouble(8, null != edge.getConfidence() ? edge.getConfidence() : 0.0);
            ps.setInt(9, edge.getPuzzleIndex());
            ps.setTimestamp(10, edge.getTimestamp());
        });
    }

    /**
     * 按 (class_id, student_id, exam_name) 查询全部超边.
     */
    public List<StudentExamHyperEdge> queryByStudentAndExam(int classId, int studentId, String examName) {
        String sql = "select id, class_id, student_id, group_id, exam_name, inputs, outputs, type, confidence, puzzle_index, timestamp "
                + "from student_exam_hyper_edges where class_id = ? and student_id = ? and exam_name = ? order by puzzle_index";
        return jdbcTemplate.query(sql, ROW_MAPPER, classId, studentId, examName);
    }

    /**
     * 按 (class_id, student_id, exam_name) 删除, 用于重试时清空旧超边.
     */
    public int deleteByStudentAndExam(int classId, int studentId, String examName) {
        String sql = "delete from student_exam_hyper_edges where class_id = ? and student_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, classId, studentId, examName);
    }
}
