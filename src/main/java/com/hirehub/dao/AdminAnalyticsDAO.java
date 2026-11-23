package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO for Admin Analytics Dashboard metrics.
 *
 * <p>All methods are read-only (SELECT only). No INSERT, UPDATE, or DELETE.
 * Connections are obtained from {@link DatabaseConnection#getConnection()} in
 * try-with-resources blocks. All queries are {@code ONLY_FULL_GROUP_BY} compatible.</p>
 *
 * <p>Date filter parameter semantics:
 * <ul>
 *   <li>{@code from}: inclusive lower bound (null = no lower bound)</li>
 *   <li>{@code to}:   exclusive upper bound (null = no upper bound)</li>
 * </ul>
 * Each method documents the exact column(s) scoped by the date filter.</p>
 */
public class AdminAnalyticsDAO {

    /**
     * Binds an optional TIMESTAMP parameter (NULL = unbounded).
     * Typically bound twice per range boundary: once for {@code ? IS NULL}, once for the comparison.
     */
    private void bindOptionalTimestamp(PreparedStatement ps, int idx, LocalDateTime dt)
            throws SQLException {
        if (dt == null) {
            ps.setNull(idx, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(idx, Timestamp.valueOf(dt));
        }
    }

    // =========================================================================
    // Q1 — Overview KPI
    // Date filter: users.created_at
    // =========================================================================

    /**
     * Fetches user overview KPIs (role counts, profile completion, and resume coverage).
     * Date filter scopes {@code users.created_at}; profile/resume metrics describe
     * those registered users.
     *
     * @param from inclusive start (null = all time)
     * @param to   exclusive end   (null = all time)
     */
    public AdminOverviewKPI fetchOverviewKPI(LocalDateTime from, LocalDateTime to)
            throws SQLException {

        String sql = """
                SELECT
                    COUNT(DISTINCT CASE WHEN u.role = 'STUDENT'   THEN u.id END) AS total_students,
                    COUNT(DISTINCT CASE WHEN u.role = 'RECRUITER' THEN u.id END) AS total_recruiters,
                    COUNT(DISTINCT CASE WHEN u.role = 'ADMIN'     THEN u.id END) AS total_admins,
                    COUNT(DISTINCT u.id) AS total_users,
                    COUNT(DISTINCT CASE
                        WHEN u.role = 'STUDENT'
                         AND sp.college IS NOT NULL AND TRIM(sp.college) <> ''
                         AND sp.degree  IS NOT NULL AND TRIM(sp.degree)  <> ''
                         AND sp.graduation_year > 2000
                        THEN u.id END) AS students_completed_profile,
                    COUNT(DISTINCT CASE
                        WHEN u.role = 'STUDENT' AND r.id IS NOT NULL
                        THEN u.id END) AS students_active_pdf_resume,
                    COUNT(DISTINCT CASE
                        WHEN u.role = 'STUDENT'
                         AND (r.id IS NOT NULL OR (sp.resume_path IS NOT NULL AND TRIM(sp.resume_path) <> ''))
                        THEN u.id END) AS students_with_any_resume,
                    COUNT(DISTINCT CASE
                        WHEN u.role = 'STUDENT' AND r_any.student_id IS NOT NULL
                        THEN u.id END) AS students_ever_uploaded_resume,
                    COUNT(DISTINCT CASE
                        WHEN u.role = 'RECRUITER'
                         AND rp.company_name IS NOT NULL AND TRIM(rp.company_name) <> ''
                        THEN u.id END) AS recruiters_completed_profile
                FROM users u
                LEFT JOIN student_profiles sp ON sp.user_id = u.id AND u.role = 'STUDENT'
                LEFT JOIN resumes r ON r.student_id = u.id AND r.is_active = TRUE
                LEFT JOIN (SELECT DISTINCT student_id FROM resumes) r_any ON r_any.student_id = u.id
                LEFT JOIN recruiter_profiles rp ON rp.user_id = u.id AND u.role = 'RECRUITER'
                WHERE (? IS NULL OR u.created_at >= ?)
                  AND (? IS NULL OR u.created_at < ?)
                """;

        AdminOverviewKPI kpi = new AdminOverviewKPI();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setTotalStudents(rs.getInt("total_students"));
                    kpi.setTotalRecruiters(rs.getInt("total_recruiters"));
                    kpi.setTotalAdmins(rs.getInt("total_admins"));
                    kpi.setTotalUsers(rs.getInt("total_users"));
                    kpi.setStudentsCompletedProfile(rs.getInt("students_completed_profile"));
                    kpi.setStudentsWithActivePdfResume(rs.getInt("students_active_pdf_resume"));
                    kpi.setStudentsWithAnyResume(rs.getInt("students_with_any_resume"));
                    kpi.setStudentsEverUploadedResume(rs.getInt("students_ever_uploaded_resume"));
                    kpi.setRecruitersCompletedProfile(rs.getInt("recruiters_completed_profile"));
                }
            }
        }

        return kpi;
    }

    // =========================================================================
    // Q2 — Job KPI
    // Date filter: jobs.created_at
    // =========================================================================

    /**
     * Fetches job posting KPIs, separating listed ACTIVE jobs from currently open
     * (non-expired deadline or NULL deadline) vs expired-active jobs.
     *
     * @param from inclusive start (null = all time)
     * @param to   exclusive end   (null = all time)
     */
    public AdminJobKPI fetchJobKPI(LocalDateTime from, LocalDateTime to)
            throws SQLException {

        String sql = """
                SELECT
                    COUNT(*) AS total_jobs,
                    COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_status_jobs,
                    COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND (deadline IS NULL OR deadline >= CURDATE()) THEN 1 ELSE 0 END), 0) AS open_accepting_jobs,
                    COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND deadline < CURDATE() THEN 1 ELSE 0 END), 0) AS expired_active_jobs,
                    COALESCE(SUM(CASE WHEN status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_jobs,
                    COALESCE(SUM(CASE WHEN status = 'DRAFT'  THEN 1 ELSE 0 END), 0) AS draft_jobs,
                    COUNT(DISTINCT recruiter_id) AS active_posting_recruiters
                FROM jobs
                WHERE (? IS NULL OR created_at >= ?)
                  AND (? IS NULL OR created_at < ?)
                """;

        AdminJobKPI kpi = new AdminJobKPI();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setTotalJobs(rs.getInt("total_jobs"));
                    kpi.setActiveStatusJobs(rs.getInt("active_status_jobs"));
                    kpi.setOpenAcceptingJobs(rs.getInt("open_accepting_jobs"));
                    kpi.setExpiredActiveJobs(rs.getInt("expired_active_jobs"));
                    kpi.setClosedJobs(rs.getInt("closed_jobs"));
                    kpi.setDraftJobs(rs.getInt("draft_jobs"));
                    kpi.setActivePostingRecruiters(rs.getInt("active_posting_recruiters"));
                }
            }
        }
        return kpi;
    }

    // =========================================================================
    // Q3 — Application KPI
    // Date filter: applications.applied_at
    // =========================================================================

    /**
     * Fetches application funnel KPIs and distinct student counts.
     *
     * @param from inclusive start (null = all time)
     * @param to   exclusive end   (null = all time)
     */
    public AdminApplicationKPI fetchApplicationKPI(LocalDateTime from, LocalDateTime to)
            throws SQLException {

        String sql = """
                SELECT
                    COUNT(*) AS total_applications,
                    COALESCE(SUM(CASE WHEN status = 'PENDING'     THEN 1 ELSE 0 END), 0) AS pending,
                    COALESCE(SUM(CASE WHEN status = 'SHORTLISTED' THEN 1 ELSE 0 END), 0) AS shortlisted,
                    COALESCE(SUM(CASE WHEN status = 'SELECTED'    THEN 1 ELSE 0 END), 0) AS selected,
                    COALESCE(SUM(CASE WHEN status = 'REJECTED'    THEN 1 ELSE 0 END), 0) AS rejected,
                    COUNT(DISTINCT student_id) AS unique_applying_students,
                    COUNT(DISTINCT CASE WHEN status = 'SHORTLISTED' THEN student_id END) AS unique_shortlisted_students,
                    COUNT(DISTINCT CASE WHEN status = 'SELECTED'    THEN student_id END) AS unique_selected_students
                FROM applications
                WHERE (? IS NULL OR applied_at >= ?)
                  AND (? IS NULL OR applied_at < ?)
                """;

        AdminApplicationKPI kpi = new AdminApplicationKPI();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setTotalApplications(rs.getInt("total_applications"));
                    kpi.setPendingApplications(rs.getInt("pending"));
                    kpi.setShortlistedApplications(rs.getInt("shortlisted"));
                    kpi.setSelectedApplications(rs.getInt("selected"));
                    kpi.setRejectedApplications(rs.getInt("rejected"));
                    kpi.setUniqueApplyingStudents(rs.getInt("unique_applying_students"));
                    kpi.setUniqueShortlistedStudents(rs.getInt("unique_shortlisted_students"));
                    kpi.setUniqueSelectedStudents(rs.getInt("unique_selected_students"));
                }
            }
        }
        return kpi;
    }

    // =========================================================================
    // Q4 — Status Distribution
    // Date filter: applications.applied_at
    // =========================================================================

    /**
     * Fetches per-status application counts for statuses present in the DB.
     * The service layer zero-fills missing canonical statuses.
     *
     * @param from inclusive start (null = all time)
     * @param to   exclusive end   (null = all time)
     */
    public List<AdminStatusDistribution> fetchStatusDistribution(LocalDateTime from, LocalDateTime to)
            throws SQLException {

        String sql = """
                SELECT
                    status,
                    COUNT(*) AS application_count,
                    COUNT(DISTINCT student_id) AS unique_students
                FROM applications
                WHERE (? IS NULL OR applied_at >= ?)
                  AND (? IS NULL OR applied_at < ?)
                GROUP BY status
                ORDER BY FIELD(status, 'PENDING', 'SHORTLISTED', 'SELECTED', 'REJECTED')
                """;

        List<AdminStatusDistribution> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminStatusDistribution row = new AdminStatusDistribution(
                            rs.getString("status"),
                            rs.getInt("application_count"),
                            rs.getInt("unique_students")
                    );
                    list.add(row);
                }
            }
        }
        return list;
    }

    // =========================================================================
    // Q5 — Job Type Stats
    // Date filter: jobs.created_at
    // =========================================================================

    /**
     * Fetches per-job-type job counts.
     * The service layer zero-fills missing canonical job types.
     *
     * @param from inclusive start (null = all time)
     * @param to   exclusive end   (null = all time)
     */
    public List<AdminJobTypeStat> fetchJobTypeStats(LocalDateTime from, LocalDateTime to)
            throws SQLException {

        String sql = """
                SELECT
                    job_type,
                    COUNT(*) AS total_jobs,
                    COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_jobs,
                    COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND (deadline IS NULL OR deadline >= CURDATE()) THEN 1 ELSE 0 END), 0) AS open_jobs,
                    COALESCE(SUM(CASE WHEN status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_jobs,
                    COALESCE(SUM(CASE WHEN status = 'DRAFT'  THEN 1 ELSE 0 END), 0) AS draft_jobs
                FROM jobs
                WHERE (? IS NULL OR created_at >= ?)
                  AND (? IS NULL OR created_at < ?)
                GROUP BY job_type
                ORDER BY total_jobs DESC
                """;

        List<AdminJobTypeStat> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminJobTypeStat row = new AdminJobTypeStat(
                            rs.getString("job_type"),
                            rs.getInt("total_jobs"),
                            rs.getInt("active_jobs"),
                            rs.getInt("open_jobs"),
                            rs.getInt("closed_jobs"),
                            rs.getInt("draft_jobs")
                    );
                    list.add(row);
                }
            }
        }
        return list;
    }

    // =========================================================================
    // Q6 — Top Jobs
    // Date filter: jobs.created_at
    // Application sub-counts: ALL applications received by those jobs
    // =========================================================================

    /**
     * Fetches the top N jobs ranked by total application volume.
     *
     * <p>Date filter applies to {@code jobs.created_at}; application counts include all
     * applications submitted to those jobs regardless of apply date.</p>
     *
     * @param from  inclusive start (null = all time)
     * @param to    exclusive end   (null = all time)
     * @param limit maximum rows to return (must be &gt; 0)
     */
    public List<AdminTopJob> fetchTopJobs(LocalDateTime from, LocalDateTime to, int limit)
            throws SQLException {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, got: " + limit);
        }

        String sql = """
                SELECT
                    j.id AS job_id,
                    j.title,
                    j.company_name,
                    j.job_type,
                    j.status,
                    j.deadline,
                    j.created_at,
                    COUNT(a.id) AS total_applications,
                    COALESCE(SUM(CASE WHEN a.status = 'PENDING'     THEN 1 ELSE 0 END), 0) AS pending_count,
                    COALESCE(SUM(CASE WHEN a.status = 'SHORTLISTED' THEN 1 ELSE 0 END), 0) AS shortlisted_count,
                    COALESCE(SUM(CASE WHEN a.status = 'SELECTED'    THEN 1 ELSE 0 END), 0) AS selected_count,
                    COALESCE(SUM(CASE WHEN a.status = 'REJECTED'    THEN 1 ELSE 0 END), 0) AS rejected_count
                FROM jobs j
                LEFT JOIN applications a ON a.job_id = j.id
                WHERE (? IS NULL OR j.created_at >= ?)
                  AND (? IS NULL OR j.created_at < ?)
                GROUP BY j.id, j.title, j.company_name, j.job_type, j.status, j.deadline, j.created_at
                ORDER BY total_applications DESC, j.created_at DESC
                LIMIT ?
                """;

        List<AdminTopJob> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);
            ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminTopJob job = new AdminTopJob();
                    job.setJobId(rs.getInt("job_id"));
                    job.setTitle(rs.getString("title"));
                    job.setCompanyName(rs.getString("company_name"));
                    job.setJobType(rs.getString("job_type"));
                    job.setStatus(rs.getString("status"));

                    java.sql.Date deadline = rs.getDate("deadline");
                    if (deadline != null) job.setDeadline(deadline.toLocalDate());

                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) job.setCreatedAt(createdAt.toLocalDateTime());

                    job.setTotalApplications(rs.getInt("total_applications"));
                    job.setPendingCount(rs.getInt("pending_count"));
                    job.setShortlistedCount(rs.getInt("shortlisted_count"));
                    job.setSelectedCount(rs.getInt("selected_count"));
                    job.setRejectedCount(rs.getInt("rejected_count"));

                    list.add(job);
                }
            }
        }
        return list;
    }

    // =========================================================================
    // Q7 — Student Participation (FIXED: pre-aggregated subqueries + weighted AVG)
    // Date filters:
    //   users.created_at           → student cohort
    //   applications.applied_at    → applications within [from, to)
    //   resume_analyses.created_at → AI resume analyses within [from, to)
    // =========================================================================

    /**
     * Fetches student participation and AI Resume Analyzer adoption metrics.
     *
     * <p>Uses independent pre-aggregated subqueries for {@code applications} and
     * {@code resume_analyses} before joining to {@code users}, preventing
     * N x M row multiplication when a student has multiple applications and multiple analyses.</p>
     *
     * <p>Computes {@code avgResumeScore} as a true weighted arithmetic mean across
     * all individual analysis records ({@code SUM(score_sum) / SUM(total_analyses)}),
     * rounded to 1 decimal place, rather than an unweighted average of per-student averages.</p>
     *
     * @param from inclusive start (null = all time)
     * @param to   exclusive end   (null = all time)
     */
    public AdminStudentParticipation fetchStudentParticipation(LocalDateTime from, LocalDateTime to)
            throws SQLException {

        String sql = """
                SELECT
                    COUNT(DISTINCT u.id) AS total_students,
                    COUNT(DISTINCT CASE WHEN app_agg.app_count > 0 THEN u.id END) AS students_who_applied,
                    COUNT(DISTINCT CASE WHEN ra_agg.total_analyses > 0 THEN u.id END) AS students_with_ai_analysis,
                    COALESCE(SUM(ra_agg.total_analyses), 0) AS total_resume_analyses,
                    ROUND(SUM(ra_agg.score_sum) / NULLIF(SUM(ra_agg.total_analyses), 0), 1) AS avg_resume_score,
                    COALESCE(SUM(ra_agg.job_specific), 0) AS job_specific_analyses,
                    COALESCE(SUM(ra_agg.general_count), 0) AS general_analyses
                FROM users u
                LEFT JOIN (
                    SELECT student_id, COUNT(id) AS app_count
                    FROM applications
                    WHERE (? IS NULL OR applied_at >= ?)
                      AND (? IS NULL OR applied_at < ?)
                    GROUP BY student_id
                ) app_agg ON app_agg.student_id = u.id
                LEFT JOIN (
                    SELECT
                        student_id,
                        COUNT(id)                                                AS total_analyses,
                        COALESCE(SUM(overall_score), 0)                          AS score_sum,
                        SUM(CASE WHEN job_id IS NOT NULL THEN 1 ELSE 0 END)      AS job_specific,
                        SUM(CASE WHEN job_id IS NULL     THEN 1 ELSE 0 END)      AS general_count
                    FROM resume_analyses
                    WHERE (? IS NULL OR created_at >= ?)
                      AND (? IS NULL OR created_at < ?)
                    GROUP BY student_id
                ) ra_agg ON ra_agg.student_id = u.id
                WHERE u.role = 'STUDENT'
                  AND (? IS NULL OR u.created_at >= ?)
                  AND (? IS NULL OR u.created_at < ?)
                """;

        AdminStudentParticipation result = new AdminStudentParticipation();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int p = 1;
            // app_agg filter on applied_at (params 1-4)
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, to);
            bindOptionalTimestamp(ps, p++, to);
            // ra_agg filter on created_at (params 5-8)
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, to);
            bindOptionalTimestamp(ps, p++, to);
            // student cohort filter on users.created_at (params 9-12)
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, to);
            bindOptionalTimestamp(ps, p++, to);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.setTotalStudents(rs.getInt("total_students"));
                    result.setStudentsWhoApplied(rs.getInt("students_who_applied"));
                    result.setStudentsWithAiAnalysis(rs.getInt("students_with_ai_analysis"));
                    result.setTotalResumeAnalyses(rs.getInt("total_resume_analyses"));

                    double avgScore = rs.getDouble("avg_resume_score");
                    if (rs.wasNull() || !Double.isFinite(avgScore)) {
                        result.setAvgResumeScore(null);
                    } else {
                        result.setAvgResumeScore(avgScore);
                    }

                    result.setJobSpecificAnalyses(rs.getInt("job_specific_analyses"));
                    result.setGeneralAnalyses(rs.getInt("general_analyses"));
                }
            }
        }
        return result;
    }

    // =========================================================================
    // Q8 — Recruiter Engagement (FIXED: two distinct date scopes, user_id join,
    //      company fallback, and true review timestamp updated_at > applied_at)
    // Date scope A: jobs.created_at   → job posting metrics
    // Date scope B: applications for Scope-A jobs (no separate apply-date filter)
    // =========================================================================

    /**
     * Fetches per-recruiter engagement metrics anchored on {@code users WHERE role = 'RECRUITER'}.
     *
     * <p>Date-filter semantics (two distinct scopes):
     * <ul>
     *   <li><strong>Scope A — {@code jobs.created_at}:</strong> restricts which jobs count
     *       toward {@code totalJobsPosted}, {@code activeJobs}, {@code openJobs}, and {@code lastJobPostedAt}.</li>
     *   <li><strong>Scope B — all applications for Scope-A jobs:</strong> no additional
     *       {@code applied_at} filter is applied, measuring conversion outcome across the jobs
     *       posted in the period.</li>
     * </ul>
     * {@code lastAppReviewAt} uses {@code MAX(CASE WHEN a.updated_at > a.applied_at THEN a.updated_at END)}
     * so unreviewed applications do not appear as recruiter review activity.</p>
     *
     * @param from inclusive start (null = all time)
     * @param to   exclusive end   (null = all time)
     */
    public List<AdminRecruiterEngagement> fetchRecruiterEngagement(LocalDateTime from, LocalDateTime to)
            throws SQLException {

        String sql = """
                SELECT
                    u.id AS recruiter_id,
                    u.full_name AS recruiter_name,
                    u.email AS recruiter_email,
                    COALESCE(NULLIF(TRIM(rp.company_name), ''), MAX(j.company_name), 'Profile Incomplete') AS company_name,
                    NULLIF(TRIM(rp.industry), '') AS industry,
                    COUNT(DISTINCT j.id) AS total_jobs_posted,
                    COUNT(DISTINCT CASE WHEN j.status = 'ACTIVE' THEN j.id END) AS active_jobs,
                    COUNT(DISTINCT CASE WHEN j.status = 'ACTIVE' AND (j.deadline IS NULL OR j.deadline >= CURDATE()) THEN j.id END) AS open_jobs,
                    MAX(j.created_at) AS last_job_posted_at,
                    COUNT(a.id) AS applications_received,
                    COALESCE(SUM(CASE WHEN a.status = 'SHORTLISTED' THEN 1 ELSE 0 END), 0) AS shortlisted_count,
                    COALESCE(SUM(CASE WHEN a.status = 'SELECTED'    THEN 1 ELSE 0 END), 0) AS selected_count,
                    rp.updated_at AS profile_updated_at,
                    MAX(CASE WHEN a.updated_at > a.applied_at THEN a.updated_at END) AS last_app_review_at
                FROM users u
                LEFT JOIN recruiter_profiles rp ON rp.user_id = u.id
                LEFT JOIN jobs j ON j.recruiter_id = u.id
                    AND (? IS NULL OR j.created_at >= ?)
                    AND (? IS NULL OR j.created_at < ?)
                LEFT JOIN applications a ON a.job_id = j.id
                WHERE u.role = 'RECRUITER'
                GROUP BY u.id, u.full_name, u.email, rp.company_name, rp.industry, rp.updated_at
                ORDER BY total_jobs_posted DESC, applications_received DESC, u.created_at DESC
                """;

        List<AdminRecruiterEngagement> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminRecruiterEngagement row = new AdminRecruiterEngagement();
                    row.setRecruiterId(rs.getInt("recruiter_id"));
                    row.setRecruiterName(rs.getString("recruiter_name"));
                    row.setRecruiterEmail(rs.getString("recruiter_email"));
                    row.setCompanyName(rs.getString("company_name"));
                    row.setIndustry(rs.getString("industry"));
                    row.setTotalJobsPosted(rs.getInt("total_jobs_posted"));
                    row.setActiveJobs(rs.getInt("active_jobs"));
                    row.setOpenJobs(rs.getInt("open_jobs"));
                    row.setApplicationsReceived(rs.getInt("applications_received"));
                    row.setShortlistedCount(rs.getInt("shortlisted_count"));
                    row.setSelectedCount(rs.getInt("selected_count"));

                    Timestamp lastJobTs = rs.getTimestamp("last_job_posted_at");
                    if (lastJobTs != null) row.setLastJobPostedAt(lastJobTs.toLocalDateTime());

                    Timestamp profileTs = rs.getTimestamp("profile_updated_at");
                    if (profileTs != null) row.setProfileUpdatedAt(profileTs.toLocalDateTime());

                    Timestamp lastAppTs = rs.getTimestamp("last_app_review_at");
                    if (lastAppTs != null) row.setLastAppReviewAt(lastAppTs.toLocalDateTime());

                    list.add(row);
                }
            }
        }
        return list;
    }

    // =========================================================================
    // Q9 — Recent Activity Feed
    // Merges registration, job posting, application, status change, resume upload,
    // and AI resume analysis events with predicate pushdown per branch
    // =========================================================================

    /**
     * Fetches the most recent platform activity events across 6 event streams:
     * {@code USER_REGISTERED}, {@code JOB_POSTED}, {@code APPLICATION_SUBMITTED},
     * {@code APPLICATION_STATUS_UPDATED}, {@code RESUME_UPLOADED}, and {@code RESUME_ANALYZED}.
     *
     * @param from  inclusive start (null = all time)
     * @param to    exclusive end   (null = all time)
     * @param limit maximum events to return (most recent first, must be &gt; 0)
     */
    public List<AdminActivityEvent> fetchRecentActivity(LocalDateTime from, LocalDateTime to, int limit)
            throws SQLException {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, got: " + limit);
        }

        String sql = """
                (SELECT
                    'USER_REGISTERED' AS event_type,
                    u.full_name AS actor_name,
                    CONCAT('Registered as ', u.role) AS summary,
                    u.created_at AS occurred_at
                FROM users u
                WHERE (? IS NULL OR u.created_at >= ?)
                  AND (? IS NULL OR u.created_at < ?)
                ORDER BY u.created_at DESC
                LIMIT ?
                )
                UNION ALL
                (SELECT
                    'JOB_POSTED' AS event_type,
                    u.full_name AS actor_name,
                    CONCAT('Posted ', j.job_type, ' job "', j.title, '" at ', j.company_name, ' (', j.status, ')') AS summary,
                    j.created_at AS occurred_at
                FROM jobs j
                JOIN users u ON u.id = j.recruiter_id
                WHERE (? IS NULL OR j.created_at >= ?)
                  AND (? IS NULL OR j.created_at < ?)
                ORDER BY j.created_at DESC
                LIMIT ?
                )
                UNION ALL
                (SELECT
                    'APPLICATION_SUBMITTED' AS event_type,
                    u.full_name AS actor_name,
                    CONCAT('Applied to "', j.title, '" at ', j.company_name) AS summary,
                    a.applied_at AS occurred_at
                FROM applications a
                JOIN users u ON u.id = a.student_id
                JOIN jobs j ON j.id = a.job_id
                WHERE (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                ORDER BY a.applied_at DESC
                LIMIT ?
                )
                UNION ALL
                (SELECT
                    'APPLICATION_STATUS_UPDATED' AS event_type,
                    r.full_name AS actor_name,
                    CONCAT('Updated ', s.full_name, '''s application for "', j.title, '" to ', a.status) AS summary,
                    a.updated_at AS occurred_at
                FROM applications a
                JOIN users s ON s.id = a.student_id
                JOIN jobs j ON j.id = a.job_id
                JOIN users r ON r.id = j.recruiter_id
                WHERE a.updated_at > a.applied_at
                  AND (? IS NULL OR a.updated_at >= ?)
                  AND (? IS NULL OR a.updated_at < ?)
                ORDER BY a.updated_at DESC
                LIMIT ?
                )
                UNION ALL
                (SELECT
                    'RESUME_UPLOADED' AS event_type,
                    u.full_name AS actor_name,
                    CONCAT('Uploaded resume "', res.file_name, '"') AS summary,
                    res.uploaded_at AS occurred_at
                FROM resumes res
                JOIN users u ON u.id = res.student_id
                WHERE (? IS NULL OR res.uploaded_at >= ?)
                  AND (? IS NULL OR res.uploaded_at < ?)
                ORDER BY res.uploaded_at DESC
                LIMIT ?
                )
                UNION ALL
                (SELECT
                    'RESUME_ANALYZED' AS event_type,
                    u.full_name AS actor_name,
                    CONCAT(
                        IF(ra.job_id IS NULL, 'General resume audit', CONCAT('Analyzed resume for "', j.title, '"')),
                        ' (Score: ', ra.overall_score, '/100)'
                    ) AS summary,
                    ra.created_at AS occurred_at
                FROM resume_analyses ra
                JOIN users u ON u.id = ra.student_id
                LEFT JOIN jobs j ON j.id = ra.job_id
                WHERE (? IS NULL OR ra.created_at >= ?)
                  AND (? IS NULL OR ra.created_at < ?)
                ORDER BY ra.created_at DESC
                LIMIT ?
                )
                ORDER BY occurred_at DESC
                LIMIT ?
                """;

        List<AdminActivityEvent> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int p = 1;
            // 6 branches, each has 4 timestamps + 1 limit
            for (int branch = 0; branch < 6; branch++) {
                bindOptionalTimestamp(ps, p++, from);
                bindOptionalTimestamp(ps, p++, from);
                bindOptionalTimestamp(ps, p++, to);
                bindOptionalTimestamp(ps, p++, to);
                ps.setInt(p++, limit);
            }
            // Final outer LIMIT
            ps.setInt(p, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("occurred_at");
                    LocalDateTime occurredAt = (ts != null) ? ts.toLocalDateTime() : null;
                    list.add(new AdminActivityEvent(
                            rs.getString("event_type"),
                            rs.getString("actor_name"),
                            rs.getString("summary"),
                            occurredAt
                    ));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // Q10 — Application Trend (daily)
    // Date filter: applications.applied_at
    // =========================================================================

    /**
     * Fetches daily application trend points grouped by calendar date ({@code DATE(applied_at)}).
     * Days with zero activity are omitted.
     *
     * @param from inclusive start (null = all time)
     * @param to   exclusive end   (null = all time)
     */
    public List<AdminTrendPoint> fetchApplicationTrend(LocalDateTime from, LocalDateTime to)
            throws SQLException {

        String sql = """
                SELECT
                    DATE(applied_at) AS activity_date,
                    COUNT(*) AS applications_count,
                    COALESCE(SUM(CASE WHEN status = 'SHORTLISTED' THEN 1 ELSE 0 END), 0) AS shortlisted_count,
                    COALESCE(SUM(CASE WHEN status = 'SELECTED'    THEN 1 ELSE 0 END), 0) AS selected_count
                FROM applications
                WHERE (? IS NULL OR applied_at >= ?)
                  AND (? IS NULL OR applied_at < ?)
                GROUP BY DATE(applied_at)
                ORDER BY activity_date ASC
                """;

        List<AdminTrendPoint> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date d = rs.getDate("activity_date");
                    LocalDate date = (d != null) ? d.toLocalDate() : null;
                    list.add(new AdminTrendPoint(
                            date,
                            rs.getInt("applications_count"),
                            rs.getInt("shortlisted_count"),
                            rs.getInt("selected_count")
                    ));
                }
            }
        }
        return list;
    }
}
