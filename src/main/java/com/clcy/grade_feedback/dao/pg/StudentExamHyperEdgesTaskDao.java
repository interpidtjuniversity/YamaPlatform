package com.clcy.grade_feedback.dao.pg;

import com.clcy.grade_feedback.entity.StudentExamHyperEdgesTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * student_exam_hyper_edges_task 表的增删改查(PostgreSQL, JdbcTemplate).
 * 任务粒度为 (student_id, exam_name) —— 某个学生某次考试.
 */
@Repository
public class StudentExamHyperEdgesTaskDao {

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<StudentExamHyperEdgesTask> ROW_MAPPER = (rs, rowNum) -> StudentExamHyperEdgesTask.builder()
            .id(rs.getInt("id"))
            .classId(rs.getInt("class_id"))
            .studentId(rs.getInt("student_id"))
            .examName(rs.getString("exam_name"))
            .status(rs.getString("status"))
            .totalCount(rs.getInt("total_count"))
            .doneCount(rs.getInt("done_count"))
            .startedAt(rs.getTimestamp("started_at"))
            .finishedAt(rs.getTimestamp("finished_at"))
            .errorMessage(rs.getString("error_message"))
            .build();

    /**
     * 原子地启动/重启任务: 不存在则插入, 存在且非 RUNNING(或超 1 小时僵尸) 则重置.
     * 唯一键为 (student_id, exam_name).
     */
    public int startOrRestartTask(int classId, int studentId, String examName) {
        String sql = "insert into student_exam_hyper_edges_task (class_id, student_id, exam_name, status, total_count, done_count, started_at) "
                + "values (?, ?, ?, 'RUNNING', 0, 0, NOW()) "
                + "on conflict (student_id, exam_name) do update "
                + "set status = 'RUNNING', total_count = 0, done_count = 0, started_at = NOW(), "
                + "    finished_at = NULL, error_message = NULL "
                + "where student_exam_hyper_edges_task.status <> 'RUNNING' "
                + "   or student_exam_hyper_edges_task.started_at < NOW() - INTERVAL '1 hour'";
        return jdbcTemplate.update(sql, classId, studentId, examName);
    }

    public int updateCounts(int studentId, String examName, int totalCount, int doneCount) {
        String sql = "update student_exam_hyper_edges_task set total_count = ?, done_count = ? "
                + "where student_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, totalCount, doneCount, studentId, examName);
    }

    public int markDone(int studentId, String examName) {
        String sql = "update student_exam_hyper_edges_task set status = 'DONE', finished_at = NOW() "
                + "where student_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, studentId, examName);
    }

    public int markFailed(int studentId, String examName, String errorMessage) {
        String sql = "update student_exam_hyper_edges_task set status = 'FAILED', finished_at = NOW(), error_message = ? "
                + "where student_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, errorMessage, studentId, examName);
    }

    public StudentExamHyperEdgesTask queryByStudentAndExam(int studentId, String examName) {
        String sql = "select id, class_id, student_id, exam_name, status, total_count, done_count, started_at, finished_at, error_message "
                + "from student_exam_hyper_edges_task where student_id = ? and exam_name = ?";
        List<StudentExamHyperEdgesTask> list = jdbcTemplate.query(sql, ROW_MAPPER, studentId, examName);
        return list.isEmpty() ? null : list.get(0);
    }
}
