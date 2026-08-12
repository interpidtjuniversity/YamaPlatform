package com.clcy.grade_feedback.dao.pg;

import com.clcy.grade_feedback.entity.GeneratedVideoBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Collections;
import java.util.List;

/**
 * PostgreSQL DAO for generated video class/group/exam bindings.
 */
@Repository
public class GeneratedVideoBindingDao {

    private static final String COLUMNS = "id, video_conversation_id, owner_number, class_id, group_id, "
            + "exam_name, script_name, video_name, video_url, created_at, updated_at";

    private static final RowMapper<GeneratedVideoBinding> ROW_MAPPER = (rs, rowNum) ->
            GeneratedVideoBinding.builder()
                    .id(rs.getLong("id"))
                    .videoConversationId(rs.getLong("video_conversation_id"))
                    .ownerNumber(rs.getString("owner_number"))
                    .classId(rs.getInt("class_id"))
                    .groupId((Integer) rs.getObject("group_id"))
                    .examName(rs.getString("exam_name"))
                    .scriptName(rs.getString("script_name"))
                    .videoName(rs.getString("video_name"))
                    .videoUrl(rs.getString("video_url"))
                    .createdAt(rs.getTimestamp("created_at"))
                    .updatedAt(rs.getTimestamp("updated_at"))
                    .build();

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /**
     * Deletes all bindings belonging to one generated-video conversation.
     */
    public int deleteByVideoConversationId(Long videoConversationId) {
        String sql = "delete from generated_video_bindings where video_conversation_id = ?";
        return jdbcTemplate.update(sql, videoConversationId);
    }

    /**
     * Inserts all supplied bindings, excluding generated id and timestamp columns.
     *
     * @return the number of rows reported as inserted by the JDBC driver
     */
    public int batchInsert(List<GeneratedVideoBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return 0;
        }
        String sql = "insert into generated_video_bindings "
                + "(video_conversation_id, owner_number, class_id, group_id, exam_name, "
                + "script_name, video_name, video_url) values (?, ?, ?, ?, ?, ?, ?, ?)";
        int[][] updateCounts = jdbcTemplate.batchUpdate(sql, bindings, bindings.size(), (ps, binding) -> {
            ps.setLong(1, binding.getVideoConversationId());
            ps.setString(2, binding.getOwnerNumber());
            ps.setInt(3, binding.getClassId());
            if (binding.getGroupId() == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, binding.getGroupId());
            }
            ps.setString(5, binding.getExamName());
            ps.setString(6, binding.getScriptName());
            ps.setString(7, binding.getVideoName());
            ps.setString(8, binding.getVideoUrl());
        });

        int inserted = 0;
        for (int[] batchCounts : updateCounts) {
            for (int count : batchCounts) {
                if (count > 0) {
                    inserted += count;
                } else if (count == java.sql.Statement.SUCCESS_NO_INFO) {
                    inserted++;
                }
            }
        }
        return inserted;
    }

    /**
     * Returns bindings in deterministic class/group/exam order.
     */
    public List<GeneratedVideoBinding> queryByVideoConversationId(Long videoConversationId) {
        if (videoConversationId == null) {
            return Collections.emptyList();
        }
        String sql = "select " + COLUMNS + " from generated_video_bindings "
                + "where video_conversation_id = ? "
                + "order by class_id asc, group_id asc nulls first, exam_name asc nulls first, id asc";
        return jdbcTemplate.query(sql, ROW_MAPPER, videoConversationId);
    }

    /**
     * 查询班级公共视频。必须是该班级的精确公共绑定，不能包含分组或测试专属视频.
     */
    public List<GeneratedVideoBinding> queryClassPublicVideos(int classId) {
        String sql = "select " + COLUMNS + " from generated_video_bindings "
                + "where class_id = ? and group_id is null and exam_name is null "
                + "order by created_at asc, id asc";
        return jdbcTemplate.query(sql, ROW_MAPPER, classId);
    }

    /**
     * 查询当前测试的专属视频，因为每个分组的测试可能不同，就需要针对当前测试去查当前测试的专属视频
     */
    public List<GeneratedVideoBinding> queryExamVisibleVideos(int classId, int groupId, String examName) {
        String sql = "select " + COLUMNS + " from generated_video_bindings "
                + "where class_id = ? and group_id = ? and exam_name = ? "
                + "order by created_at asc, id asc";
        return jdbcTemplate.query(sql, ROW_MAPPER, classId, groupId, examName);
    }
}
