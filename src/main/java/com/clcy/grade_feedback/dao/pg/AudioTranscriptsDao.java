package com.clcy.grade_feedback.dao.pg;

import com.clcy.grade_feedback.entity.AudioTranscript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

/**
 * audio_transcripts 表的增删改查(PostgreSQL, JdbcTemplate 实现).
 * 查询主要走 (student_id, exam_name) 联合索引.
 */
@Repository
public class AudioTranscriptsDao {

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<AudioTranscript> ROW_MAPPER = (rs, rowNum) -> AudioTranscript.builder()
            .id(rs.getInt("id"))
            .classId(rs.getInt("class_id"))
            .studentId(rs.getInt("student_id"))
            .groupId(rs.getInt("group_id"))
            .examName(rs.getString("exam_name"))
            .puzzleIdx(rs.getInt("puzzle_idx"))
            .transcriptText(rs.getString("transcript_text"))
            .timestamp(rs.getTimestamp("timestamp"))
            .build();

    // ========== 增 ==========

    /**
     * 插入一条转录记录, 返回自增主键 id; 失败返回 null.
     */
    public Integer insert(AudioTranscript record) {
        String sql = "insert into audio_transcripts (class_id, student_id, group_id, exam_name, puzzle_idx, transcript_text) "
                + "values (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int affected = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, record.getClassId());
            ps.setInt(2, record.getStudentId());
            ps.setInt(3, record.getGroupId());
            ps.setString(4, record.getExamName());
            ps.setInt(5, record.getPuzzleIdx());
            ps.setString(6, record.getTranscriptText());
            return ps;
        }, keyHolder);
        if (affected == 0) {
            return null;
        }
        Number key = keyHolder.getKey();
        return null == key ? null : key.intValue();
    }

    /**
     * 原子 upsert: 按 (student_id, exam_name, puzzle_idx) 唯一键插入或更新 transcript_text.
     * 替代之前"先 delete 再 insert"的非原子组合, 避免同一学生同一题的多个音频文件并发落库时互相覆盖.
     * 注意: 需要先在 DB 上建好 (student_id, exam_name, puzzle_idx) 唯一约束.
     */
    public int upsertByStudentExamPuzzle(AudioTranscript record) {
        String sql = "insert into audio_transcripts (class_id, student_id, group_id, exam_name, puzzle_idx, transcript_text) "
                + "values (?, ?, ?, ?, ?, ?) "
                + "on conflict (student_id, exam_name, puzzle_idx) do update "
                + "set transcript_text = excluded.transcript_text";
        return jdbcTemplate.update(sql,
                record.getClassId(),
                record.getStudentId(),
                record.getGroupId(),
                record.getExamName(),
                record.getPuzzleIdx(),
                record.getTranscriptText());
    }

    // ========== 删 ==========

    /**
     * 按 (student_id, exam_name) 删除, 返回删除条数.
     */
    public int deleteByStudentAndExam(int studentId, String examName) {
        String sql = "delete from audio_transcripts where student_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, studentId, examName);
    }

    /**
     * 按 (class_id, exam_name) 删除全部记录.
     * 用于首次模式重跑任务前清空旧识别结果, 避免重复落库堆积.
     * 注意: 重试模式(只跑 failList)不应调用此方法, 否则会删掉已成功的记录.
     */
    public int deleteByClassAndExam(int classId, String examName) {
        String sql = "delete from audio_transcripts where class_id = ? and exam_name = ?";
        return jdbcTemplate.update(sql, classId, examName);
    }

    /**
     * 按主键删除.
     */
    public int deleteById(int id) {
        String sql = "delete from audio_transcripts where id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 按 (student_id, exam_name, puzzle_idx) 删除单条, 用于重新识别前清理旧记录.
     */
    public int deleteByStudentExamPuzzle(int studentId, String examName, int puzzleIdx) {
        String sql = "delete from audio_transcripts where student_id = ? and exam_name = ? and puzzle_idx = ?";
        return jdbcTemplate.update(sql, studentId, examName, puzzleIdx);
    }

    // ========== 改 ==========

    /**
     * 更新转录文本(按主键).
     */
    public int updateTranscriptText(int id, String transcriptText) {
        String sql = "update audio_transcripts set transcript_text = ? where id = ?";
        return jdbcTemplate.update(sql, transcriptText, id);
    }

    /**
     * 按 (student_id, exam_name, puzzle_idx) 更新转录文本, 不存在则不影响.
     * 用于转录结果落库的幂等写入.
     */
    public int updateByStudentExamPuzzle(int studentId, String examName, int puzzleIdx, String transcriptText) {
        String sql = "update audio_transcripts set transcript_text = ? "
                + "where student_id = ? and exam_name = ? and puzzle_idx = ?";
        return jdbcTemplate.update(sql, transcriptText, studentId, examName, puzzleIdx);
    }

    // ========== 查 ==========

    /**
     * 按主键查询单条.
     */
    public AudioTranscript queryById(int id) {
        String sql = "select id, class_id, student_id, group_id, exam_name, puzzle_idx, transcript_text, timestamp "
                + "from audio_transcripts where id = ?";
        List<AudioTranscript> list = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按 (student_id, exam_name) 查询全部转录记录(走联合索引).
     */
    public List<AudioTranscript> queryByStudentAndExam(int classId, int studentId, String examName) {
        String sql = "select id, class_id, student_id, group_id, exam_name, puzzle_idx, transcript_text, timestamp "
                + "from audio_transcripts where class_id = ? and student_id = ? and exam_name = ? order by puzzle_idx";
        return jdbcTemplate.query(sql, ROW_MAPPER, classId, studentId, examName);
    }

    /**
     * 查询某学生某次考试的全部转录文本拼接(按 puzzle_idx 升序).
     * 主要服务于"拿到该学生这次考试的完整转录文本"的场景.
     */
    public String queryConcatTranscript(int studentId, String examName) {
        String sql = "select string_agg(transcript_text, '' order by puzzle_idx) "
                + "from audio_transcripts where student_id = ? and exam_name = ?";
        return jdbcTemplate.queryForObject(sql, String.class, studentId, examName);
    }

    /**
     * 查询某学生在多场考试中的全部转录文本拼接(按 exam_name, puzzle_idx 升序).
     * 用于梯径提取: 拼接 examName 之前(含)所有考试的转录文本.
     *
     * @param studentId 学号
     * @param examNames 考试名列表
     * @return 拼接后的文本; 无记录时返回空串(非 null)
     */
    public String queryConcatTranscriptByExams(int studentId, List<String> examNames) {
        if (null == examNames || examNames.isEmpty()) {
            return "";
        }
        // 构建 IN 占位符: ?, ?, ...
        String placeholders = String.join(",", java.util.Collections.nCopies(examNames.size(), "?"));
        String sql = "select coalesce(string_agg(transcript_text, '' order by exam_name, puzzle_idx), '') "
                + "from audio_transcripts where student_id = ? and exam_name in (" + placeholders + ")";
        Object[] params = new Object[examNames.size() + 1];
        params[0] = studentId;
        for (int i = 0; i < examNames.size(); i++) {
            params[i + 1] = examNames.get(i);
        }
        return jdbcTemplate.queryForObject(sql, String.class, params);
    }

    /**
     * 按 (student_id, exam_name, puzzle_idx) 查询单条.
     */
    public AudioTranscript queryByStudentExamPuzzle(int studentId, String examName, int puzzleIdx) {
        String sql = "select id, class_id, student_id, group_id, exam_name, puzzle_idx, transcript_text, timestamp "
                + "from audio_transcripts where student_id = ? and exam_name = ? and puzzle_idx = ?";
        List<AudioTranscript> list = jdbcTemplate.query(sql, ROW_MAPPER, studentId, examName, puzzleIdx);
        return list.isEmpty() ? null : list.get(0);
    }
}
