package com.clcy.grade_feedback.dao.pg;

import com.clcy.grade_feedback.entity.VideoConversationRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;

/**
 * PostgreSQL DAO for asynchronous video conversation records.
 */
@Repository
public class VideoConversationDao {

    private static final String COLUMNS = "id, user_id, user_prompt, script_name, status, code, video_url, "
            + "error_code, error_message, poll_count, next_poll_at, lease_token, lease_until, "
            + "created_at, updated_at, finished_at";

    private static final RowMapper<VideoConversationRecord> ROW_MAPPER = (rs, rowNum) ->
            VideoConversationRecord.builder()
                    .id(rs.getLong("id"))
                    .userId(rs.getString("user_id"))
                    .userPrompt(rs.getString("user_prompt"))
                    .scriptName(rs.getString("script_name"))
                    .status(rs.getString("status"))
                    .code(rs.getString("code"))
                    .videoUrl(rs.getString("video_url"))
                    .errorCode(rs.getString("error_code"))
                    .errorMessage(rs.getString("error_message"))
                    .pollCount(rs.getInt("poll_count"))
                    .nextPollAt(rs.getTimestamp("next_poll_at"))
                    .leaseToken(rs.getString("lease_token"))
                    .leaseUntil(rs.getTimestamp("lease_until"))
                    .createdAt(rs.getTimestamp("created_at"))
                    .updatedAt(rs.getTimestamp("updated_at"))
                    .finishedAt(rs.getTimestamp("finished_at"))
                    .build();

    private static final RowMapper<VideoConversationRecord> SIMPLE_ROW_MAPPER = (rs, rowNum) ->
            VideoConversationRecord.builder()
                    .id(rs.getLong("id"))
                    .userId(rs.getString("user_id"))
                    .userPrompt(rs.getString("user_prompt"))
                    .scriptName(rs.getString("script_name"))
                    .status(rs.getString("status"))
                    .videoUrl(rs.getString("video_url"))
                    .errorCode(rs.getString("error_code"))
                    .errorMessage(rs.getString("error_message"))
                    .createdAt(rs.getTimestamp("created_at"))
                    .updatedAt(rs.getTimestamp("updated_at"))
                    .finishedAt(rs.getTimestamp("finished_at"))
                    .build();

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /**
     * Creates a SUBMITTING record. A collision with the partial unique active-user index returns null;
     * all database errors other than SQLState 23505 are propagated.
     */
    public Long insertSubmitting(String userId, String prompt) {
        String sql = "insert into video_conversation_records (user_id, user_prompt, status) "
                + "values (?, ?, 'SUBMITTING') returning id";
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, userId, prompt);
        } catch (DataAccessException ex) {
            if (hasSqlState(ex, "23505")) {
                return null;
            }
            throw ex;
        }
    }

    /**
     * 按上游任务标识查询当前用户的任务，防止跨用户读取任务状态.
     */
    public VideoConversationRecord queryByUserAndScriptName(String userId, String scriptName) {
        String sql = "select " + "id, user_id, user_prompt, script_name, status, video_url, error_code, error_message, created_at, updated_at, finished_at " + " from video_conversation_records "
                + "where user_id = ? and script_name = ? limit 1";
        List<VideoConversationRecord> records = jdbcTemplate.query(sql, SIMPLE_ROW_MAPPER, userId, scriptName);
        return records.isEmpty() ? null : records.get(0);
    }

    public VideoConversationRecord findById(Long id) {
        String sql = "select " + COLUMNS + " from video_conversation_records where id = ?";
        List<VideoConversationRecord> records = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * Returns all records for a user, newest first.
     */
    public List<VideoConversationRecord> queryAllByUser(String userId) {
        String sql = "select " + "id, user_id, user_prompt, script_name, status, video_url, error_code, error_message, created_at, updated_at, finished_at "
                + "from video_conversation_records "
                + "where user_id = ? order by created_at desc, id desc";
        return jdbcTemplate.query(sql, SIMPLE_ROW_MAPPER, userId);
    }

    /**
     * Returns the user's active record, if one exists.
     */
    public VideoConversationRecord queryActiveByUser(String userId) {
        String sql = "select " + COLUMNS + " from video_conversation_records "
                + "where user_id = ? and status in ('SUBMITTING', 'QUEUED', 'GENERATING', 'RENDERING') "
                + "order by created_at desc, id desc limit 1";
        List<VideoConversationRecord> records = jdbcTemplate.query(sql, ROW_MAPPER, userId);
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * Selects the newest completed records but returns that bounded conversation history oldest first.
     * 成功失败都查询
     */
    public List<VideoConversationRecord> queryRecentCompleted(String userId, int limit) {
        requirePositive("limit", limit);
        String sql = "select " + COLUMNS + " from ("
                + "select " + COLUMNS + " from video_conversation_records "
                + "where user_id = ? "
                + "order by created_at desc, id desc limit ?"
                + ") recent order by created_at asc, id asc";
        return jdbcTemplate.query(sql, ROW_MAPPER, userId, limit);
    }

    public int markQueued(Long id, String scriptName, long pollDelayMs) {
        requireNonNegative("pollDelayMs", pollDelayMs);
        String sql = "update video_conversation_records "
                + "set status = 'QUEUED', script_name = ?, "
                + "next_poll_at = now() + (? * interval '1 millisecond'), updated_at = now() "
                + "where id = ? and status = 'SUBMITTING'";
        return jdbcTemplate.update(sql, scriptName, pollDelayMs, id);
    }

    public int markSubmissionFailed(Long id, String errorCode, String errorMessage) {
        String sql = "update video_conversation_records "
                + "set status = 'FAILED', error_code = ?, error_message = ?, "
                + "finished_at = now(), updated_at = now() "
                + "where id = ? and status = 'SUBMITTING'";
        return jdbcTemplate.update(sql, errorCode, errorMessage, id);
    }

    /**
     * 应用在调用上游期间崩溃时，SUBMITTING 可能无法进入后续状态；定时释放此类过期占位.
     */
    public int markStaleSubmittingFailed(long timeoutSeconds) {
        requirePositive("timeoutSeconds", timeoutSeconds);
        String sql = "update video_conversation_records "
                + "set status = 'FAILED', error_code = 'SUBMISSION_TIMEOUT', "
                + "error_message = '视频任务提交超时，请重新提交', finished_at = now(), updated_at = now() "
                + "where status = 'SUBMITTING' "
                + "and created_at < now() - (? * interval '1 second')";
        return jdbcTemplate.update(sql, timeoutSeconds);
    }

    /**
     * Claims due work in one statement. Row locks are held only for the duration of this statement;
     * lease-token fencing protects all subsequent worker updates.
     */
    public List<VideoConversationRecord> claimDueTasks(int limit, long leaseSeconds, String leaseToken) {
        requirePositive("limit", limit);
        requirePositive("leaseSeconds", leaseSeconds);
        requireNonBlank("leaseToken", leaseToken);

        String sql = "with due as ("
                + "select id from video_conversation_records "
                + "where status in ('QUEUED', 'GENERATING', 'RENDERING') "
                + "and next_poll_at <= now() "
                + "and (lease_token is null or lease_until is null or lease_until <= now()) "
                + "order by next_poll_at asc, created_at asc, id asc "
                + "for update skip locked limit ?"
                + ") update video_conversation_records vcr "
                + "set lease_token = ?, lease_until = now() + (? * interval '1 second'), updated_at = now() "
                + "from due where vcr.id = due.id returning vcr." + COLUMNS.replace(", ", ", vcr.");
        return jdbcTemplate.query(sql, ROW_MAPPER, limit, leaseToken, leaseSeconds);
    }

    public int reschedule(Long id, String leaseToken, String status, long pollDelayMs,
                          String errorCode, String errorMessage) {
        requireNonBlank("leaseToken", leaseToken);
        requirePollableStatus(status);
        requireNonNegative("pollDelayMs", pollDelayMs);
        String sql = "update video_conversation_records "
                + "set status = ?, poll_count = coalesce(poll_count, 0) + 1, "
                + "next_poll_at = now() + (? * interval '1 millisecond'), "
                + "error_code = ?, error_message = ?, lease_token = null, lease_until = null, updated_at = now() "
                + "where id = ? and lease_token = ? "
                + "and status in ('QUEUED', 'GENERATING', 'RENDERING')";
        return jdbcTemplate.update(sql, status, pollDelayMs, errorCode, errorMessage, id, leaseToken);
    }

    public int markCompleted(Long id, String leaseToken, String code, String videoUrl) {
        requireNonBlank("leaseToken", leaseToken);
        String sql = "update video_conversation_records "
                + "set status = 'COMPLETED', code = ?, video_url = ?, error_code = null, error_message = null, "
                + "lease_token = null, lease_until = null, finished_at = now(), updated_at = now() "
                + "where id = ? and lease_token = ? "
                + "and status in ('QUEUED', 'GENERATING', 'RENDERING')";
        return jdbcTemplate.update(sql, code, videoUrl, id, leaseToken);
    }

    public int markFailed(Long id, String leaseToken, String errorCode, String errorMessage) {
        return markFailed(id, leaseToken, null, errorCode, errorMessage);
    }

    /**
     * 标记任务失败并保留已经成功生成的代码。渲染阶段失败时视频不可用，但代码仍需返回给用户。
     */
    public int markFailed(Long id, String leaseToken, String code, String errorCode, String errorMessage) {
        requireNonBlank("leaseToken", leaseToken);
        String sql = "update video_conversation_records "
                + "set status = 'FAILED', code = coalesce(?, code), video_url = null, "
                + "error_code = ?, error_message = ?, "
                + "lease_token = null, lease_until = null, finished_at = now(), updated_at = now() "
                + "where id = ? and lease_token = ? "
                + "and status in ('QUEUED', 'GENERATING', 'RENDERING')";
        return jdbcTemplate.update(sql, code, errorCode, errorMessage, id, leaseToken);
    }

    private static boolean hasSqlState(Throwable throwable, String sqlState) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException && sqlState.equals(((SQLException) current).getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void requirePositive(String name, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    private static void requireNonNegative(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requirePollableStatus(String status) {
        if (!"QUEUED".equals(status) && !"GENERATING".equals(status) && !"RENDERING".equals(status)) {
            throw new IllegalArgumentException("status must be QUEUED, GENERATING, or RENDERING");
        }
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
