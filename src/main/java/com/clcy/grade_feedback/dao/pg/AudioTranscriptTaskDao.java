package com.clcy.grade_feedback.dao.pg;

import com.clcy.grade_feedback.entity.AudioTranscriptTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * audio_transcript_task 表的增删改查(PostgreSQL, JdbcTemplate).
 * 通过 (class_id, exam_name) 唯一索引保证同一班级同一考试只有一个任务.
 */
@Repository
public class AudioTranscriptTaskDao {

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<AudioTranscriptTask> ROW_MAPPER = (rs, rowNum) -> AudioTranscriptTask.builder()
            .id(rs.getInt("id"))
            .classId(rs.getInt("class_id"))
            .examName(rs.getString("exam_name"))
            .status(rs.getString("status"))
            .totalCount(rs.getInt("total_count"))
            .doneCount(rs.getInt("done_count"))
            .startedAt(rs.getTimestamp("started_at"))
            .finishedAt(rs.getTimestamp("finished_at"))
            .errorMessage(rs.getString("error_message"))
            .failList(rs.getString("fail_list"))
            .build();

    /**
     * 原子地启动/重启任务:
     * 若 (class_id, exam_name) 不存在则插入; 若存在且非 RUNNING(或为超过 1 小时的僵尸 RUNNING) 则重置为 RUNNING.
     * 注意: 不清空 fail_list —— runTranscript 会读取它判断是首次(跑全部)还是重试(只跑失败文件).
     * 返回受影响行数: 1=成功启动, 0=已有 RUNNING 任务(拒绝重复提交).
     */
    public int startOrRestartTask(int classId, String examName) {
        String sql = "insert into audio_transcript_task (class_id, exam_name, status, total_count, done_count, started_at) "
                + "values (?, ?, 'RUNNING', 0, 0, NOW()) "
                + "on conflict (class_id, exam_name) do update "
                + "set status = 'RUNNING', total_count = 0, done_count = 0, started_at = NOW(), "
                + "    finished_at = NULL, error_message = NULL "
                + "where audio_transcript_task.status <> 'RUNNING' "
                + "   or audio_transcript_task.started_at < NOW() - INTERVAL '1 hour'";
        return jdbcTemplate.update(sql, classId, examName);
    }

    /**
     * 更新进度计数.
     */
    public int updateCounts(int classId, String examName, int totalCount, int doneCount) {
        String sql = "update audio_transcript_task set total_count = ?, done_count = ? "
                + "where class_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, totalCount, doneCount, classId, examName);
    }

    /**
     * 标记任务完成, 同时清空 fail_list(全部成功, 无需重试).
     */
    public int markDone(int classId, String examName) {
        String sql = "update audio_transcript_task set status = 'DONE', finished_at = NOW(), fail_list = NULL "
                + "where class_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, classId, examName);
    }

    /**
     * 标记任务失败, 并记录本次仍失败的文件列表(重试时只跑这些).
     */
    public int markFailed(int classId, String examName, String errorMessage, String failListJson) {
        String sql = "update audio_transcript_task set status = 'FAILED', finished_at = NOW(), "
                + "error_message = ?, fail_list = ? "
                + "where class_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, errorMessage, failListJson, classId, examName);
    }

    /**
     * 查询任务状态(供前端轮询).
     */
    public AudioTranscriptTask queryByClassAndExam(int classId, String examName) {
        String sql = "select id, class_id, exam_name, status, total_count, done_count, started_at, finished_at, error_message, fail_list "
                + "from audio_transcript_task where class_id = ? and exam_name = ?";
        List<AudioTranscriptTask> list = jdbcTemplate.query(sql, ROW_MAPPER, classId, examName);
        return list.isEmpty() ? null : list.get(0);
    }
}
