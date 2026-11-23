package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.ResumeAnalysis;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ResumeAnalysisDAO — JDBC CRUD operations for the `resume_analyses` table.
 */
public class ResumeAnalysisDAO {

    private static final String SQL_INSERT =
            "INSERT INTO resume_analyses (resume_id, job_id, student_id, overall_score, "
            + "skills_score, experience_score, education_score, completeness_score, "
            + "certifications_score, matching_skills, missing_skills, analysis_summary, "
            + "quality_feedback, improvement_plan) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_FIND_BY_ID =
            "SELECT ra.*, j.title AS job_title, j.company_name AS company_name, r.file_name AS file_name "
            + "FROM resume_analyses ra "
            + "LEFT JOIN jobs j ON ra.job_id = j.id "
            + "LEFT JOIN resumes r ON ra.resume_id = r.id "
            + "WHERE ra.id=?";

    private static final String SQL_FIND_BY_STUDENT_AND_JOB =
            "SELECT ra.*, j.title AS job_title, j.company_name AS company_name, r.file_name AS file_name "
            + "FROM resume_analyses ra "
            + "LEFT JOIN jobs j ON ra.job_id = j.id "
            + "LEFT JOIN resumes r ON ra.resume_id = r.id "
            + "WHERE ra.student_id=? AND ra.job_id=? ORDER BY ra.created_at DESC LIMIT 1";

    private static final String SQL_FIND_RECENT_BY_STUDENT =
            "SELECT ra.*, j.title AS job_title, j.company_name AS company_name, r.file_name AS file_name "
            + "FROM resume_analyses ra "
            + "LEFT JOIN jobs j ON ra.job_id = j.id "
            + "LEFT JOIN resumes r ON ra.resume_id = r.id "
            + "WHERE ra.student_id=? ORDER BY ra.created_at DESC LIMIT ?";

    private static final String SQL_DELETE =
            "DELETE FROM resume_analyses WHERE id=?";

    private static final String SQL_FIND_BY_ID_FOR_STUDENT =
            "SELECT ra.*, j.title AS job_title, j.company_name AS company_name, r.file_name AS file_name "
            + "FROM resume_analyses ra "
            + "LEFT JOIN jobs j ON ra.job_id = j.id "
            + "LEFT JOIN resumes r ON ra.resume_id = r.id "
            + "WHERE ra.id=? AND ra.student_id=?";

    private static final String SQL_DELETE_FOR_STUDENT =
            "DELETE FROM resume_analyses WHERE id=? AND student_id=?";

    /**
     * Inserts a new analysis record and sets the auto-generated primary key.
     */
    public void insert(ResumeAnalysis a) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            if (a.getResumeId() == null || a.getResumeId() <= 0) {
                stmt.setNull(1, Types.INTEGER);
            } else {
                stmt.setInt(1, a.getResumeId());
            }

            if (a.getJobId() == null || a.getJobId() <= 0) {
                stmt.setNull(2, Types.INTEGER);
            } else {
                stmt.setInt(2, a.getJobId());
            }

            stmt.setInt(3, a.getStudentId());
            stmt.setInt(4, a.getOverallScore());
            stmt.setInt(5, a.getSkillsScore());
            stmt.setInt(6, a.getExperienceScore());
            stmt.setInt(7, a.getEducationScore());
            stmt.setInt(8, a.getCompletenessScore());
            stmt.setInt(9, a.getCertificationsScore());
            setNullableString(stmt, 10, a.getMatchingSkills());
            setNullableString(stmt, 11, a.getMissingSkills());
            setNullableString(stmt, 12, a.getAnalysisSummary());
            setNullableString(stmt, 13, a.getQualityFeedback());
            setNullableString(stmt, 14, a.getImprovementPlan());

            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    a.setId(keys.getInt(1));
                }
            }
        }
    }

    /**
     * Finds an analysis by primary key id.
     *
     * @deprecated Unscoped lookup. Use {@link #findByIdForStudent(int, int)} for student operations.
     */
    @Deprecated
    public Optional<ResumeAnalysis> findById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds an analysis by primary key id scoped to a student (atomic ownership check).
     */
    public Optional<ResumeAnalysis> findByIdForStudent(int id, int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID_FOR_STUDENT)) {

            stmt.setInt(1, id);
            stmt.setInt(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds the most recent analysis for a given student and job.
     */
    public Optional<ResumeAnalysis> findByStudentAndJob(int studentId, int jobId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_STUDENT_AND_JOB)) {

            stmt.setInt(1, studentId);
            stmt.setInt(2, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Retrieves the most recent analyses for a student.
     */
    public List<ResumeAnalysis> findRecentByStudentId(int studentId, int limit) throws SQLException {
        List<ResumeAnalysis> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_RECENT_BY_STUDENT)) {

            stmt.setInt(1, studentId);
            stmt.setInt(2, limit <= 0 ? 10 : limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Deletes an analysis record.
     *
     * @deprecated Unscoped delete. Use {@link #deleteForStudent(int, int)} for student operations.
     */
    @Deprecated
    public void delete(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    /**
     * Atomically deletes an analysis record only if owned by the given student.
     *
     * @param id        the analysis primary key
     * @param studentId the student user id
     * @return true if a row was deleted, false otherwise
     */
    public boolean deleteForStudent(int id, int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_FOR_STUDENT)) {
            stmt.setInt(1, id);
            stmt.setInt(2, studentId);
            return stmt.executeUpdate() > 0;
        }
    }

    // ── Row Mapping ─────────────────────────────────────────────────────────

    private ResumeAnalysis mapRow(ResultSet rs) throws SQLException {
        ResumeAnalysis a = new ResumeAnalysis();
        a.setId                 (rs.getInt("id"));

        int rId = rs.getInt("resume_id");
        a.setResumeId(rs.wasNull() ? null : rId);

        int jId = rs.getInt("job_id");
        a.setJobId(rs.wasNull() ? null : jId);

        a.setStudentId          (rs.getInt("student_id"));
        a.setOverallScore       (rs.getInt("overall_score"));
        a.setSkillsScore        (rs.getInt("skills_score"));
        a.setExperienceScore    (rs.getInt("experience_score"));
        a.setEducationScore     (rs.getInt("education_score"));
        a.setCompletenessScore  (rs.getInt("completeness_score"));
        a.setCertificationsScore(rs.getInt("certifications_score"));
        a.setMatchingSkills     (rs.getString("matching_skills"));
        a.setMissingSkills      (rs.getString("missing_skills"));
        a.setAnalysisSummary    (rs.getString("analysis_summary"));
        a.setQualityFeedback    (rs.getString("quality_feedback"));
        a.setImprovementPlan    (rs.getString("improvement_plan"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) a.setCreatedAt(ts.toLocalDateTime());

        // Joined fields
        try {
            a.setJobTitle   (rs.getString("job_title"));
            a.setCompanyName(rs.getString("company_name"));
            a.setFileName   (rs.getString("file_name"));
        } catch (SQLException ignored) {}

        return a;
    }

    private void setNullableString(PreparedStatement stmt, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) stmt.setNull(idx, Types.VARCHAR);
        else                              stmt.setString(idx, val);
    }
}

