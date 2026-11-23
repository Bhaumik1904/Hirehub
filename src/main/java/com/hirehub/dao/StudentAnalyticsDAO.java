package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * StudentAnalyticsDAO — Dedicated read-only data access layer for Student Analytics.
 *
 * <p>All queries:
 * <ul>
 *   <li>Are strictly {@code SELECT}-only (no DML/DDL).</li>
 *   <li>Enforce {@code student_id = ?} parameterization with positive ID validation.</li>
 *   <li>Comply fully with MySQL 8 {@code ONLY_FULL_GROUP_BY}.</li>
 *   <li>Support optional date window filtering (inclusive {@code from}, exclusive {@code to}).</li>
 * </ul>
 */
public class StudentAnalyticsDAO {

    private static void requireValidStudentId(int studentId) {
        if (studentId <= 0) {
            throw new IllegalArgumentException("Student ID must be positive, got: " + studentId);
        }
    }

    private static void bindOptionalTimestamp(PreparedStatement ps, int idx, LocalDateTime dt) throws SQLException {
        if (dt == null) {
            ps.setNull(idx, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(idx, Timestamp.valueOf(dt));
        }
    }

    // =========================================================================
    // Application Status Counts & Totals
    // =========================================================================

    /**
     * Counts student applications grouped by status within the optional date window.
     */
    public Map<Application.Status, Integer> countApplicationsByStatus(int studentId, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        requireValidStudentId(studentId);

        String sql = """
                SELECT a.status, COUNT(*) AS cnt
                FROM applications a
                WHERE a.student_id = ?
                  AND (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                GROUP BY a.status
                """;

        Map<Application.Status, Integer> counts = new EnumMap<>(Application.Status.class);
        for (Application.Status s : Application.Status.values()) {
            counts.put(s, 0);
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            bindOptionalTimestamp(stmt, 2, from);
            bindOptionalTimestamp(stmt, 3, from);
            bindOptionalTimestamp(stmt, 4, to);
            bindOptionalTimestamp(stmt, 5, to);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String statusStr = rs.getString("status");
                    try {
                        Application.Status st = Application.Status.valueOf(statusStr.toUpperCase());
                        counts.put(st, rs.getInt("cnt"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return counts;
    }

    /**
     * Counts open active jobs visible to students (ACTIVE, APPROVED, verified recruiter, non-expired).
     * Enforces the same fail-closed visibility rules as {@code JobDAO.searchApprovedActive()}.
     */
    public int countOpenActiveJobs() throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM jobs j
                JOIN users u ON u.id = j.recruiter_id
                JOIN recruiter_profiles rp ON rp.user_id = u.id
                WHERE j.status = 'ACTIVE'
                  AND j.moderation_status = 'APPROVED'
                  AND u.is_active = TRUE
                  AND u.role = 'RECRUITER'
                  AND rp.verification_status = 'VERIFIED'
                  AND (j.deadline IS NULL OR j.deadline >= CURDATE())
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    // =========================================================================
    // Student Profile & Resumes
    // =========================================================================

    /**
     * Retrieves the student profile by user ID.
     */
    public Optional<StudentProfile> getStudentProfile(int studentId) throws SQLException {
        requireValidStudentId(studentId);

        String sql = """
                SELECT id, user_id, phone, college, degree, graduation_year, skills, resume_path, updated_at
                FROM student_profiles
                WHERE user_id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StudentProfile p = new StudentProfile();
                    p.setId(rs.getInt("id"));
                    p.setUserId(rs.getInt("user_id"));
                    p.setPhone(rs.getString("phone"));
                    p.setCollege(rs.getString("college"));
                    p.setDegree(rs.getString("degree"));
                    p.setGraduationYear(rs.getInt("graduation_year"));
                    p.setSkills(rs.getString("skills"));
                    p.setResumePath(rs.getString("resume_path"));

                    Timestamp upd = rs.getTimestamp("updated_at");
                    if (upd != null) {
                        p.setUpdatedAt(upd.toLocalDateTime());
                    }
                    return Optional.of(p);
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Retrieves the student's active resume.
     */
    public Optional<Resume> getActiveResume(int studentId) throws SQLException {
        requireValidStudentId(studentId);

        String sql = """
                SELECT id, student_id, file_name, stored_path, file_size_bytes, file_hash,
                       parsed_text, parsed_json, is_active, uploaded_at, updated_at
                FROM resumes
                WHERE student_id = ? AND is_active = TRUE
                ORDER BY id DESC
                LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Resume r = new Resume();
                    r.setId(rs.getInt("id"));
                    r.setStudentId(rs.getInt("student_id"));
                    r.setFileName(rs.getString("file_name"));
                    r.setStoredPath(rs.getString("stored_path"));
                    r.setFileSizeBytes(rs.getLong("file_size_bytes"));
                    r.setFileHash(rs.getString("file_hash"));
                    r.setParsedText(rs.getString("parsed_text"));
                    r.setParsedJson(rs.getString("parsed_json"));
                    r.setActive(rs.getBoolean("is_active"));

                    Timestamp up = rs.getTimestamp("uploaded_at");
                    if (up != null) r.setUploadedAt(up.toLocalDateTime());
                    Timestamp upd = rs.getTimestamp("updated_at");
                    if (upd != null) r.setUpdatedAt(upd.toLocalDateTime());

                    return Optional.of(r);
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Counts historical resumes uploaded by this student (all records in resumes table).
     */
    public int countHistoricalResumes(int studentId) throws SQLException {
        requireValidStudentId(studentId);

        String sql = "SELECT COUNT(*) FROM resumes WHERE student_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    /**
     * Retrieves the student's most recent persisted resume analysis.
     */
    public Optional<ResumeAnalysis> getLatestResumeAnalysis(int studentId) throws SQLException {
        requireValidStudentId(studentId);

        String sql = """
                SELECT ra.*, j.title AS job_title, j.company_name AS company_name, r.file_name AS file_name
                FROM resume_analyses ra
                LEFT JOIN jobs j ON ra.job_id = j.id
                LEFT JOIN resumes r ON ra.resume_id = r.id
                WHERE ra.student_id = ?
                ORDER BY ra.created_at DESC
                LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResumeAnalysisRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Retrieves recent resume analyses for the student up to limit.
     */
    public List<ResumeAnalysis> getRecentAnalyses(int studentId, int limit) throws SQLException {
        requireValidStudentId(studentId);
        int effectiveLimit = limit <= 0 ? 10 : limit;

        String sql = """
                SELECT ra.*, j.title AS job_title, j.company_name AS company_name, r.file_name AS file_name
                FROM resume_analyses ra
                LEFT JOIN jobs j ON ra.job_id = j.id
                LEFT JOIN resumes r ON ra.resume_id = r.id
                WHERE ra.student_id = ?
                ORDER BY ra.created_at DESC
                LIMIT ?
                """;

        List<ResumeAnalysis> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            stmt.setInt(2, effectiveLimit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResumeAnalysisRow(rs));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // Application Rows & Trends
    // =========================================================================

    /**
     * Retrieves application rows for this student, joined with job details.
     */
    public List<StudentApplicationRow> getApplicationRows(int studentId, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        requireValidStudentId(studentId);

        String sql = """
                SELECT a.id AS application_id,
                       a.job_id,
                       j.title AS job_title,
                       j.company_name,
                       j.job_type,
                       j.location,
                       a.status,
                       a.applied_at,
                       a.updated_at AS latest_update_at,
                       j.status AS job_status,
                       j.deadline AS job_deadline
                FROM applications a
                JOIN jobs j ON a.job_id = j.id
                WHERE a.student_id = ?
                  AND (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                ORDER BY a.applied_at DESC
                """;

        List<StudentApplicationRow> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            bindOptionalTimestamp(stmt, 2, from);
            bindOptionalTimestamp(stmt, 3, from);
            bindOptionalTimestamp(stmt, 4, to);
            bindOptionalTimestamp(stmt, 5, to);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StudentApplicationRow row = new StudentApplicationRow();
                    row.setApplicationId(rs.getInt("application_id"));
                    row.setJobId(rs.getInt("job_id"));
                    row.setJobTitle(rs.getString("job_title"));
                    row.setCompanyName(rs.getString("company_name"));
                    row.setJobType(rs.getString("job_type"));
                    row.setLocation(rs.getString("location"));

                    String statusStr = rs.getString("status");
                    try {
                        row.setStatus(Application.Status.valueOf(statusStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        row.setStatus(Application.Status.PENDING);
                    }

                    Timestamp appliedTs = rs.getTimestamp("applied_at");
                    if (appliedTs != null) row.setAppliedAt(appliedTs.toLocalDateTime());

                    Timestamp updTs = rs.getTimestamp("latest_update_at");
                    if (updTs != null) row.setLatestUpdateAt(updTs.toLocalDateTime());

                    row.setJobStatus(rs.getString("job_status"));

                    java.sql.Date deadlineDate = rs.getDate("job_deadline");
                    if (deadlineDate != null) {
                        LocalDate dl = deadlineDate.toLocalDate();
                        row.setJobDeadline(dl);
                        row.setDeadlineExpired(dl.isBefore(today));
                    } else {
                        row.setJobDeadline(null);
                        row.setDeadlineExpired(false);
                    }

                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * Retrieves application volume trend points grouped by application date.
     */
    public List<StudentTrendPoint> getApplicationTrend(int studentId, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        requireValidStudentId(studentId);

        String sql = """
                SELECT DATE(a.applied_at) AS app_date,
                       COUNT(*) AS app_count,
                       SUM(CASE WHEN a.status = 'SHORTLISTED' THEN 1 ELSE 0 END) AS shortlisted_count,
                       SUM(CASE WHEN a.status = 'SELECTED' THEN 1 ELSE 0 END) AS selected_count
                FROM applications a
                WHERE a.student_id = ?
                  AND (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                GROUP BY DATE(a.applied_at)
                ORDER BY app_date ASC
                """;

        List<StudentTrendPoint> points = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            bindOptionalTimestamp(stmt, 2, from);
            bindOptionalTimestamp(stmt, 3, from);
            bindOptionalTimestamp(stmt, 4, to);
            bindOptionalTimestamp(stmt, 5, to);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date d = rs.getDate("app_date");
                    if (d != null) {
                        StudentTrendPoint p = new StudentTrendPoint(
                                d.toLocalDate(),
                                rs.getInt("app_count"),
                                rs.getInt("shortlisted_count"),
                                rs.getInt("selected_count")
                        );
                        points.add(p);
                    }
                }
            }
        }
        return points;
    }

    // =========================================================================
    // Recommendations & Active Jobs
    // =========================================================================

    /**
     * Retrieves all open active jobs visible to students.
     * Enforces the full fail-closed visibility rules:
     * - Job status = ACTIVE
     * - Moderation status = APPROVED
     * - Recruiter account active (users.is_active = TRUE)
     * - Recruiter role = RECRUITER
     * - Recruiter profile verification = VERIFIED
     * - Deadline NULL or not expired
     */
    public List<Job> findOpenActiveJobs() throws SQLException {
        String sql = """
                SELECT j.id, j.recruiter_id, j.title, j.company_name, j.description, j.required_skills,
                       j.location, j.job_type, j.salary_range, j.eligibility, j.deadline, j.status, j.created_at
                FROM jobs j
                JOIN users u ON u.id = j.recruiter_id
                JOIN recruiter_profiles rp ON rp.user_id = u.id
                WHERE j.status = 'ACTIVE'
                  AND j.moderation_status = 'APPROVED'
                  AND u.is_active = TRUE
                  AND u.role = 'RECRUITER'
                  AND rp.verification_status = 'VERIFIED'
                  AND (j.deadline IS NULL OR j.deadline >= CURDATE())
                ORDER BY j.created_at DESC
                """;

        List<Job> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Job j = new Job();
                j.setId(rs.getInt("id"));
                j.setRecruiterId(rs.getInt("recruiter_id"));
                j.setTitle(rs.getString("title"));
                j.setCompanyName(rs.getString("company_name"));
                j.setDescription(rs.getString("description"));
                j.setRequiredSkills(rs.getString("required_skills"));
                j.setLocation(rs.getString("location"));

                String typeStr = rs.getString("job_type");
                try {
                    j.setJobType(Job.JobType.valueOf(typeStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    j.setJobType(Job.JobType.FULL_TIME);
                }

                j.setSalaryRange(rs.getString("salary_range"));
                j.setEligibility(rs.getString("eligibility"));

                java.sql.Date dl = rs.getDate("deadline");
                if (dl != null) j.setDeadline(dl.toLocalDate());

                String stStr = rs.getString("status");
                try {
                    j.setStatus(Job.Status.valueOf(stStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    j.setStatus(Job.Status.ACTIVE);
                }

                Timestamp cat = rs.getTimestamp("created_at");
                if (cat != null) j.setCreatedAt(cat.toLocalDateTime());

                list.add(j);
            }
        }
        return list;
    }

    /**
     * Retrieves the set of job IDs that the student has already applied to.
     */
    public Set<Integer> getAppliedJobIds(int studentId) throws SQLException {
        requireValidStudentId(studentId);

        String sql = "SELECT job_id FROM applications WHERE student_id = ?";
        Set<Integer> set = new HashSet<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getInt("job_id"));
                }
            }
        }
        return set;
    }

    // =========================================================================
    // Activity Timeline
    // =========================================================================

    /**
     * Retrieves recent activity events for a student (applications submitted, status updates,
     * resume uploads, and resume analyses), sorted by timestamp descending.
     */
    public List<StudentActivityEvent> getRecentActivity(int studentId, int limit) throws SQLException {
        requireValidStudentId(studentId);
        int maxItems = limit <= 0 ? 10 : limit;

        List<StudentActivityEvent> events = new ArrayList<>();

        // 1. Applications submitted & updated
        String appSql = """
                SELECT a.id, a.status, a.applied_at, a.updated_at, j.title, j.company_name
                FROM applications a
                JOIN jobs j ON a.job_id = j.id
                WHERE a.student_id = ?
                ORDER BY a.applied_at DESC
                LIMIT ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(appSql)) {

            stmt.setInt(1, studentId);
            stmt.setInt(2, maxItems);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String title = rs.getString("title");
                    String company = rs.getString("company_name");
                    String statusStr = rs.getString("status");
                    Timestamp appliedTs = rs.getTimestamp("applied_at");
                    Timestamp updatedTs = rs.getTimestamp("updated_at");

                    if (appliedTs != null) {
                        events.add(new StudentActivityEvent(
                                StudentActivityEvent.EventType.APPLICATION_SUBMITTED,
                                appliedTs.toLocalDateTime(),
                                "Applied to " + title,
                                "Application submitted to " + company,
                                "Applied",
                                "badge-pending"
                        ));
                    }

                    if (updatedTs != null && !appliedTs.equals(updatedTs) && !"PENDING".equalsIgnoreCase(statusStr)) {
                        String badgeClass = switch (statusStr.toUpperCase()) {
                            case "SHORTLISTED" -> "badge-active";
                            case "SELECTED" -> "badge-selected";
                            case "REJECTED" -> "badge-rejected";
                            default -> "badge-pending";
                        };
                        events.add(new StudentActivityEvent(
                                StudentActivityEvent.EventType.STATUS_CHANGED,
                                updatedTs.toLocalDateTime(),
                                "Status Updated: " + statusStr,
                                title + " at " + company + " status marked as " + statusStr,
                                statusStr,
                                badgeClass
                        ));
                    }
                }
            }
        }

        // 2. Resume uploads
        String resumeSql = """
                SELECT file_name, uploaded_at, is_active
                FROM resumes
                WHERE student_id = ?
                ORDER BY uploaded_at DESC
                LIMIT ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(resumeSql)) {

            stmt.setInt(1, studentId);
            stmt.setInt(2, maxItems);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String fileName = rs.getString("file_name");
                    Timestamp upTs = rs.getTimestamp("uploaded_at");
                    boolean isActive = rs.getBoolean("is_active");

                    if (upTs != null) {
                        events.add(new StudentActivityEvent(
                                StudentActivityEvent.EventType.RESUME_UPLOADED,
                                upTs.toLocalDateTime(),
                                "Uploaded Resume: " + fileName,
                                (isActive ? "Active resume" : "Historical resume") + " processed and parsed",
                                isActive ? "Active" : "Archived",
                                isActive ? "badge-active" : "badge-inactive"
                        ));
                    }
                }
            }
        }

        // 3. Resume analyses
        String analysisSql = """
                SELECT ra.overall_score, ra.created_at, j.title, j.company_name
                FROM resume_analyses ra
                LEFT JOIN jobs j ON ra.job_id = j.id
                WHERE ra.student_id = ?
                ORDER BY ra.created_at DESC
                LIMIT ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(analysisSql)) {

            stmt.setInt(1, studentId);
            stmt.setInt(2, maxItems);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int score = rs.getInt("overall_score");
                    Timestamp crTs = rs.getTimestamp("created_at");
                    String jobTitle = rs.getString("title");
                    String company = rs.getString("company_name");

                    if (crTs != null) {
                        String target = (jobTitle != null && !jobTitle.isBlank())
                                ? jobTitle + " at " + company
                                : "General Resume Review";
                        events.add(new StudentActivityEvent(
                                StudentActivityEvent.EventType.RESUME_ANALYZED,
                                crTs.toLocalDateTime(),
                                "Resume Analysis Completed (" + score + "%)",
                                "Evaluated against: " + target,
                                score + "%",
                                "badge-active"
                        ));
                    }
                }
            }
        }

        // Sort combined events descending by timestamp
        events.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        if (events.size() > maxItems) {
            return new ArrayList<>(events.subList(0, maxItems));
        }
        return events;
    }

    // =========================================================================
    // Row Mapping Helpers
    // =========================================================================

    private ResumeAnalysis mapResumeAnalysisRow(ResultSet rs) throws SQLException {
        ResumeAnalysis a = new ResumeAnalysis();
        a.setId(rs.getInt("id"));

        int rId = rs.getInt("resume_id");
        a.setResumeId(rs.wasNull() ? null : rId);

        int jId = rs.getInt("job_id");
        a.setJobId(rs.wasNull() ? null : jId);

        a.setStudentId(rs.getInt("student_id"));
        a.setOverallScore(rs.getInt("overall_score"));
        a.setSkillsScore(rs.getInt("skills_score"));
        a.setExperienceScore(rs.getInt("experience_score"));
        a.setEducationScore(rs.getInt("education_score"));
        a.setCompletenessScore(rs.getInt("completeness_score"));
        a.setCertificationsScore(rs.getInt("certifications_score"));
        a.setMatchingSkills(rs.getString("matching_skills"));
        a.setMissingSkills(rs.getString("missing_skills"));
        a.setAnalysisSummary(rs.getString("analysis_summary"));
        a.setQualityFeedback(rs.getString("quality_feedback"));
        a.setImprovementPlan(rs.getString("improvement_plan"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) a.setCreatedAt(ts.toLocalDateTime());

        try {
            a.setJobTitle(rs.getString("job_title"));
            a.setCompanyName(rs.getString("company_name"));
            a.setFileName(rs.getString("file_name"));
        } catch (SQLException ignored) {}

        return a;
    }
}

