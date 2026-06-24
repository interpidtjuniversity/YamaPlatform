package com.clcy.grade_feedback.dao.pg;

import com.clcy.grade_feedback.entity.StudentLadderonNodesTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * student_ladderon_nodes_task 表的增删改查(PostgreSQL, JdbcTemplate).
 * 通过 (class_id, exam_name) 唯一索引保证同一班级同一考试只有一个任务.
 * 不保留检查点: 失败直接标记 FAILED, 重跑从头开始.
 */
@Repository
public class StudentLadderonNodesTaskDao {

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<StudentLadderonNodesTask> ROW_MAPPER = (rs, rowNum) -> StudentLadderonNodesTask.builder()
            .id(rs.getInt("id"))
            .classId(rs.getInt("class_id"))
            .examName(rs.getString("exam_name"))
            .status(rs.getString("status"))
            .totalCount(rs.getInt("total_count"))
            .doneCount(rs.getInt("done_count"))
            .startedAt(rs.getTimestamp("started_at"))
            .finishedAt(rs.getTimestamp("finished_at"))
            .errorMessage(rs.getString("error_message"))
            .build();

    /**
     * 原子地启动/重启任务:
     * 不存在则插入; 存在且非 RUNNING(或超过 1 小时的僵尸 RUNNING) 则重置为 RUNNING.
     * 返回受影响行数: 1=成功启动, 0=已有 RUNNING 任务(拒绝重复提交).
     */
    public int startOrRestartTask(int classId, String examName) {
        String sql = "insert into student_ladderon_nodes_task (class_id, exam_name, status, total_count, done_count, started_at) "
                + "values (?, ?, 'RUNNING', 0, 0, NOW()) "
                + "on conflict (class_id, exam_name) do update "
                + "set status = 'RUNNING', total_count = 0, done_count = 0, started_at = NOW(), "
                + "    finished_at = NULL, error_message = NULL "
                + "where student_ladderon_nodes_task.status <> 'RUNNING' "
                + "   or student_ladderon_nodes_task.started_at < NOW() - INTERVAL '1 hour'";
        return jdbcTemplate.update(sql, classId, examName);
    }

    public int updateCounts(int classId, String examName, int totalCount, int doneCount) {
        String sql = "update student_ladderon_nodes_task set total_count = ?, done_count = ? "
                + "where class_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, totalCount, doneCount, classId, examName);
    }

    public int markDone(int classId, String examName) {
        String sql = "update student_ladderon_nodes_task set status = 'DONE', finished_at = NOW() "
                + "where class_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, classId, examName);
    }

    public int markFailed(int classId, String examName, String errorMessage) {
        String sql = "update student_ladderon_nodes_task set status = 'FAILED', finished_at = NOW(), error_message = ? "
                + "where class_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, errorMessage, classId, examName);
    }

    public StudentLadderonNodesTask queryByClassAndExam(int classId, String examName) {
        String sql = "select id, class_id, exam_name, status, total_count, done_count, started_at, finished_at, error_message "
                + "from student_ladderon_nodes_task where class_id = ? and exam_name = ?";
        List<StudentLadderonNodesTask> list = jdbcTemplate.query(sql, ROW_MAPPER, classId, examName);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 批量查询: 按 class_id + exam_name IN (...) 一次查出多个考试的任务状态.
     * 用于前置条件批量检查, 避免循环内逐条查询.
     */
    public List<StudentLadderonNodesTask> queryByClassAndExams(int classId, List<String> examNames) {
        if (null == examNames || examNames.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(examNames.size(), "?"));
        String sql = "select id, class_id, exam_name, status, total_count, done_count, started_at, finished_at, error_message "
                + "from student_ladderon_nodes_task where class_id = ? and exam_name in (" + placeholders + ")";
        Object[] params = new Object[examNames.size() + 1];
        params[0] = classId;
        for (int i = 0; i < examNames.size(); i++) {
            params[i + 1] = examNames.get(i);
        }
        return jdbcTemplate.query(sql, ROW_MAPPER, params);
    }
}
