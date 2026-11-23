package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.Application;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ApplicationDAO — CRUD for the applications table.
 * All SQL uses PreparedStatement. Ownership checks are enforced in ApplicationService.
 */
public class ApplicationDAO {

    // ── INSERT ───────────────────────────────────────────────────────────────
    private static final String SQL_INSERT =
            "INSERT INTO applications (job_id, student_id, cover_letter, resume_id, status) VALUES (?, ?, ?, ?, 'PENDING')";

    // ── FIND by id ────────────────────────────────────────────────────────────
    private static final String SQL_FIND_BY_ID =
            "SELECT a.id, a.job_id, a.student_id, a.resume_id, a.cover_letter, a.status, a.applied_at, a.updated_at, " +
            "j.title AS job_title, j.company_name, j.job_type, j.location " +
            "FROM applications a " +
            "JOIN jobs j ON a.job_id = j.id " +
            "WHERE a.id = ?";

    // ── FIND by student (My Applications screen) ──────────────────────────────
    private static final String SQL_FIND_BY_STUDENT =
            "SELECT a.id, a.job_id, a.student_id, a.resume_id, a.cover_letter, a.status, a.applied_at, a.updated_at, " +
            "j.title AS job_title, j.company_name, j.job_type, j.location " +
            "FROM applications a " +
            "JOIN jobs j ON a.job_id = j.id " +
            "WHERE a.student_id = ? " +
            "ORDER BY a.applied_at DESC";

    // ── FIND by job (recruiter applicant management) — includes student profile ─
    private static final String SQL_FIND_BY_JOB =
            "SELECT a.id, a.job_id, a.student_id, a.resume_id, a.cover_letter, a.status, a.applied_at, a.updated_at, " +
            "j.title AS job_title, j.company_name, j.job_type, j.location, " +
            "u.full_name AS student_name, u.email AS student_email, " +
            "sp.college AS student_college, sp.degree AS student_degree, " +
            "sp.graduation_year AS student_grad_year, sp.skills AS student_skills " +
            "FROM applications a " +
            "JOIN jobs j ON a.job_id = j.id " +
            "JOIN users u ON a.student_id = u.id " +
            "LEFT JOIN student_profiles sp ON sp.user_id = a.student_id " +
            "WHERE a.job_id = ? " +
            "ORDER BY a.applied_at DESC";

    // ── CHECK duplicate ───────────────────────────────────────────────────────
    private static final String SQL_EXISTS =
            "SELECT 1 FROM applications WHERE job_id = ? AND student_id = ? LIMIT 1";

    // ── UPDATE status ─────────────────────────────────────────────────────────
    private static final String SQL_UPDATE_STATUS =
            "UPDATE applications SET status = ? WHERE id = ?";

    // ── DELETE ────────────────────────────────────────────────────────────────
    private static final String SQL_DELETE =
            "DELETE FROM applications WHERE id = ?";

    // ── COUNT by student ──────────────────────────────────────────────────────
    private static final String SQL_COUNT_BY_STUDENT =
            "SELECT COUNT(*) FROM applications WHERE student_id = ?";

    // ── COUNT by job ──────────────────────────────────────────────────────────
    private static final String SQL_COUNT_BY_JOB =
            "SELECT COUNT(*) FROM applications WHERE job_id = ?";

    // ── COUNT by job + status ─────────────────────────────────────────────────
    private static final String SQL_COUNT_BY_JOB_STATUS =
            "SELECT COUNT(*) FROM applications WHERE job_id = ? AND status = ?";

    // ── COUNT all apps for a recruiter's jobs ─────────────────────────────────
    private static final String SQL_COUNT_TOTAL_BY_RECRUITER =
            "SELECT COUNT(*) FROM applications a " +
            "JOIN jobs j ON a.job_id = j.id WHERE j.recruiter_id = ?";

    // ── COUNT shortlisted for a recruiter's jobs ──────────────────────────────
    private static final String SQL_COUNT_SHORTLISTED_BY_RECRUITER =
            "SELECT COUNT(*) FROM applications a " +
            "JOIN jobs j ON a.job_id = j.id WHERE j.recruiter_id = ? AND a.status = 'SHORTLISTED'";

    // ── RECRUITER-SCOPED QUERIES (atomic ownership enforcement) ──────────────
    private static final String SQL_FIND_BY_ID_AND_RECRUITER =
            "SELECT a.id, a.job_id, a.student_id, a.resume_id, a.cover_letter, a.status, a.applied_at, a.updated_at, " +
            "j.title AS job_title, j.company_name, j.job_type, j.location, " +
            "u.full_name AS student_name, u.email AS student_email, " +
            "sp.college AS student_college, sp.degree AS student_degree, " +
            "sp.graduation_year AS student_grad_year, sp.skills AS student_skills " +
            "FROM applications a " +
            "INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ? " +
            "JOIN users u ON a.student_id = u.id " +
            "LEFT JOIN student_profiles sp ON sp.user_id = a.student_id " +
            "WHERE a.id = ?";

    private static final String SQL_FIND_BY_JOB_AND_RECRUITER =
            "SELECT a.id, a.job_id, a.student_id, a.resume_id, a.cover_letter, a.status, a.applied_at, a.updated_at, " +
            "j.title AS job_title, j.company_name, j.job_type, j.location, " +
            "u.full_name AS student_name, u.email AS student_email, " +
            "sp.college AS student_college, sp.degree AS student_degree, " +
            "sp.graduation_year AS student_grad_year, sp.skills AS student_skills " +
            "FROM applications a " +
            "INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ? " +
            "JOIN users u ON a.student_id = u.id " +
            "LEFT JOIN student_profiles sp ON sp.user_id = a.student_id " +
            "WHERE a.job_id = ? " +
            "ORDER BY a.applied_at DESC";

    private static final String SQL_UPDATE_STATUS_FOR_RECRUITER =
            "UPDATE applications a " +
            "INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ? " +
            "SET a.status = ? " +
            "WHERE a.id = ?";

    private static final String SQL_COUNT_BY_JOB_AND_RECRUITER =
            "SELECT COUNT(*) FROM applications a " +
            "INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ? " +
            "WHERE a.job_id = ?";

    private static final String SQL_COUNT_BY_JOBS_FOR_RECRUITER =
            "SELECT a.job_id, COUNT(*) AS app_count " +
            "FROM applications a " +
            "INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ? " +
            "GROUP BY a.job_id";

    // =========================================================================
    // Write
    // =========================================================================

    /** Inserts a new application with an optional resume snapshot. Returns the generated id. */
    public int insert(int jobId, int studentId, String coverLetter, Integer resumeId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, jobId);
            stmt.setInt(2, studentId);
            if (coverLetter == null || coverLetter.isBlank()) stmt.setNull(3, Types.CLOB);
            else stmt.setString(3, coverLetter.trim());
            if (resumeId == null || resumeId <= 0) stmt.setNull(4, Types.INTEGER);
            else stmt.setInt(4, resumeId);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Application insert returned no generated key.");
            }
        }
    }

    /** Inserts a new application without a resume snapshot (resume_id = NULL). Returns the generated id. */
    public int insert(int jobId, int studentId, String coverLetter) throws SQLException {
        return insert(jobId, studentId, coverLetter, null);
    }

    /** Updates only the status field. Prefer {@link #updateStatusForRecruiter(int, int, Application.Status)}. */
    public void updateStatus(int applicationId, Application.Status newStatus) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_STATUS)) {
            stmt.setString(1, newStatus.name());
            stmt.setInt(2, applicationId);
            stmt.executeUpdate();
        }
    }

    /**
     * Atomically updates application status only when the application's job is owned by {@code recruiterId}.
     *
     * @return number of rows modified (1 when updated, 0 when missing or owned by another recruiter)
     */
    public int updateStatusForRecruiter(int applicationId, int recruiterId,
                                        Application.Status newStatus) throws SQLException {
        if (applicationId <= 0 || recruiterId <= 0 || newStatus == null) {
            return 0;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_STATUS_FOR_RECRUITER)) {
            stmt.setInt(1, recruiterId);
            stmt.setString(2, newStatus.name());
            stmt.setInt(3, applicationId);
            return stmt.executeUpdate();
        }
    }

    // =========================================================================
    // Read
    // =========================================================================

    public Optional<Application> findById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs, false));
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a single application including applicant details only if its job belongs to {@code recruiterId}.
     */
    public Optional<Application> findByIdAndRecruiter(int applicationId, int recruiterId) throws SQLException {
        if (applicationId <= 0 || recruiterId <= 0) {
            return Optional.empty();
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_ID_AND_RECRUITER)) {
            stmt.setInt(1, recruiterId);
            stmt.setInt(2, applicationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs, true));
                return Optional.empty();
            }
        }
    }

    /** Returns all applications for a student, joined with job info. */
    public List<Application> findByStudentId(int studentId) throws SQLException {
        List<Application> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_STUDENT)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs, false));
            }
        }
        return result;
    }

    /** Returns all applications for a job, including student profile data. */
    public List<Application> findByJobId(int jobId) throws SQLException {
        List<Application> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_JOB)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs, true));
            }
        }
        return result;
    }

    /**
     * Returns all applications for {@code jobId} including student profile data,
     * enforced atomically via {@code INNER JOIN jobs j ON j.id = a.job_id AND j.recruiter_id = ?}.
     */
    public List<Application> findByJobIdAndRecruiter(int jobId, int recruiterId) throws SQLException {
        if (jobId <= 0 || recruiterId <= 0) {
            return new ArrayList<>();
        }
        List<Application> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_FIND_BY_JOB_AND_RECRUITER)) {
            stmt.setInt(1, recruiterId);
            stmt.setInt(2, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs, true));
            }
        }
        return result;
    }

    /** Returns true if the student has already applied to this job. */
    public boolean exists(int jobId, int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_EXISTS)) {
            stmt.setInt(1, jobId);
            stmt.setInt(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Count of all applications by a student. */
    public int countByStudent(int studentId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_COUNT_BY_STUDENT)) {
            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Count of all applications for a specific job. */
    public int countByJob(int jobId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_COUNT_BY_JOB)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Count of all applications for {@code jobId} scoped to {@code recruiterId}.
     * Returns 0 when the job does not belong to the recruiter.
     */
    public int countByJobAndRecruiter(int jobId, int recruiterId) throws SQLException {
        if (jobId <= 0 || recruiterId <= 0) {
            return 0;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_COUNT_BY_JOB_AND_RECRUITER)) {
            stmt.setInt(1, recruiterId);
            stmt.setInt(2, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Bulk application counts keyed by {@code job_id} across all jobs owned by {@code recruiterId},
     * replacing per-job N+1 count queries on the recruiter dashboard.
     */
    public Map<Integer, Integer> countByJobsForRecruiter(int recruiterId) throws SQLException {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        if (recruiterId <= 0) {
            return counts;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_COUNT_BY_JOBS_FOR_RECRUITER)) {
            stmt.setInt(1, recruiterId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getInt("job_id"), rs.getInt("app_count"));
                }
            }
        }
        return counts;
    }

    /** Count of applications with a given status for a specific job. */
    public int countByJobAndStatus(int jobId, Application.Status status) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_COUNT_BY_JOB_STATUS)) {
            stmt.setInt(1, jobId);
            stmt.setString(2, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Total application count across all jobs belonging to a recruiter. */
    public int countTotalByRecruiter(int recruiterId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_COUNT_TOTAL_BY_RECRUITER)) {
            stmt.setInt(1, recruiterId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Count of SHORTLISTED applications across all jobs belonging to a recruiter. */
    public int countShortlistedByRecruiter(int recruiterId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_COUNT_SHORTLISTED_BY_RECRUITER)) {
            stmt.setInt(1, recruiterId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ── RESUME REFERENCED CHECK ──────────────────────────────────────────────
    private static final String SQL_EXISTS_BY_RESUME_ID =
            "SELECT 1 FROM applications WHERE resume_id = ? LIMIT 1";

    /**
     * Checks if a resume is currently referenced by any submitted application.
     * Used to prevent deleting resumes that form historical application records.
     */
    public boolean isResumeReferenced(int resumeId) throws SQLException {
        if (resumeId <= 0) return false;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_EXISTS_BY_RESUME_ID)) {
            stmt.setInt(1, resumeId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private Application mapRow(ResultSet rs, boolean includeStudentInfo) throws SQLException {
        Application a = new Application();
        a.setId(rs.getInt("id"));
        a.setJobId(rs.getInt("job_id"));
        a.setStudentId(rs.getInt("student_id"));

        int resumeIdVal = rs.getInt("resume_id");
        if (!rs.wasNull()) {
            a.setResumeId(resumeIdVal);
        } else {
            a.setResumeId(null);
        }

        a.setCoverLetter(rs.getString("cover_letter"));
        a.setStatus(Application.Status.fromString(rs.getString("status")));

        Timestamp appliedAt = rs.getTimestamp("applied_at");
        if (appliedAt != null) a.setAppliedAt(appliedAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) a.setUpdatedAt(updatedAt.toLocalDateTime());

        a.setJobTitle(rs.getString("job_title"));
        a.setCompanyName(rs.getString("company_name"));
        a.setJobType(rs.getString("job_type"));
        a.setLocation(rs.getString("location"));

        if (includeStudentInfo) {
            a.setStudentName(rs.getString("student_name"));
            a.setStudentEmail(rs.getString("student_email"));
            a.setStudentCollege(rs.getString("student_college"));
            a.setStudentDegree(rs.getString("student_degree"));
            a.setStudentGradYear(rs.getInt("student_grad_year"));
            a.setStudentSkills(rs.getString("student_skills"));
        }
        return a;
    }
}
