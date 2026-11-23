package com.hirehub.dao;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO for recruiter-scoped analytics metrics.
 *
 * <p>All queries are strictly {@code SELECT}-only (no writes or mutations), use
 * parameterized {@link PreparedStatement} bindings, enforce positive {@code recruiterId}
 * validation and {@code j.recruiter_id = ?} SQL isolation predicates, and are
 * compatible with MySQL 8 {@code ONLY_FULL_GROUP_BY}.</p>
 *
 * <p>Date filter semantics:
 * <ul>
 *   <li>{@code from}: inclusive lower bound ({@code null} = unbounded)</li>
 *   <li>{@code to}: exclusive upper bound ({@code null} = unbounded)</li>
 * </ul>
 */
public class RecruiterAnalyticsDAO {

    private static void requireValidRecruiterId(int recruiterId) {
        if (recruiterId <= 0) {
            throw new IllegalArgumentException("Recruiter ID must be positive, got: " + recruiterId);
        }
    }

    private static void requirePositiveLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, got: " + limit);
        }
    }

    private static void bindOptionalTimestamp(PreparedStatement ps, int idx, LocalDateTime dt)
            throws SQLException {
        if (dt == null) {
            ps.setNull(idx, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(idx, Timestamp.valueOf(dt));
        }
    }

    // =========================================================================
    // RQ1 — Overview KPI
    // Lifetime job portfolio metrics + period-scoped job/application activity
    // =========================================================================

    /**
     * Fetches recruiter overview KPIs including profile completeness, full job
     * portfolio status breakdown, lifetime totals, and period-scoped counts.
     *
     * @param recruiterId positive recruiter user ID
     * @param from        inclusive lower bound (null = all time)
     * @param to          exclusive upper bound (null = all time)
     */
    public RecruiterOverviewKPI fetchOverviewKPI(int recruiterId, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        requireValidRecruiterId(recruiterId);

        String sql = """
                SELECT
                    u.full_name AS recruiter_name,
                    NULLIF(TRIM(rp.company_name), '') AS rp_company_name,
                    NULLIF(TRIM(rp.industry), '') AS rp_industry,
                    NULLIF(TRIM(rp.company_website), '') AS rp_website,
                    NULLIF(TRIM(rp.company_description), '') AS rp_description,
                    job_agg.fallback_company,
                    job_agg.total_jobs,
                    job_agg.active_status_jobs,
                    job_agg.open_accepting_jobs,
                    job_agg.expired_active_jobs,
                    job_agg.closing_7d_jobs,
                    job_agg.draft_jobs,
                    job_agg.closed_jobs,
                    job_agg.zero_applicant_open_jobs,
                    job_agg.jobs_created_in_period,
                    job_agg.lifetime_total_applications,
                    job_agg.lifetime_pending_applications,
                    app_agg.period_total_applications,
                    app_agg.period_pending_applications,
                    app_agg.period_shortlisted_applications,
                    app_agg.period_selected_applications,
                    app_agg.period_rejected_applications
                FROM users u
                LEFT JOIN recruiter_profiles rp ON rp.user_id = u.id
                CROSS JOIN (
                    SELECT
                        MAX(NULLIF(TRIM(j.company_name), '')) AS fallback_company,
                        COUNT(*) AS total_jobs,
                        COALESCE(SUM(CASE WHEN j.status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_status_jobs,
                        COALESCE(SUM(CASE WHEN j.status = 'ACTIVE' AND (j.deadline IS NULL OR j.deadline >= CURDATE()) THEN 1 ELSE 0 END), 0) AS open_accepting_jobs,
                        COALESCE(SUM(CASE WHEN j.status = 'ACTIVE' AND j.deadline < CURDATE() THEN 1 ELSE 0 END), 0) AS expired_active_jobs,
                        COALESCE(SUM(CASE WHEN j.status = 'ACTIVE' AND j.deadline BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY) THEN 1 ELSE 0 END), 0) AS closing_7d_jobs,
                        COALESCE(SUM(CASE WHEN j.status = 'DRAFT'  THEN 1 ELSE 0 END), 0) AS draft_jobs,
                        COALESCE(SUM(CASE WHEN j.status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_jobs,
                        COALESCE(SUM(CASE WHEN j.status = 'ACTIVE' AND (j.deadline IS NULL OR j.deadline >= CURDATE()) AND COALESCE(ja.app_cnt, 0) = 0 THEN 1 ELSE 0 END), 0) AS zero_applicant_open_jobs,
                        COALESCE(SUM(CASE WHEN (? IS NULL OR j.created_at >= ?) AND (? IS NULL OR j.created_at < ?) THEN 1 ELSE 0 END), 0) AS jobs_created_in_period,
                        COALESCE(SUM(COALESCE(ja.app_cnt, 0)), 0) AS lifetime_total_applications,
                        COALESCE(SUM(COALESCE(ja.pending_cnt, 0)), 0) AS lifetime_pending_applications
                    FROM jobs j
                    LEFT JOIN (
                        SELECT
                            a.job_id,
                            COUNT(*) AS app_cnt,
                            SUM(CASE WHEN a.status = 'PENDING' THEN 1 ELSE 0 END) AS pending_cnt
                        FROM applications a
                        JOIN jobs j_inner ON j_inner.id = a.job_id
                        WHERE j_inner.recruiter_id = ?
                        GROUP BY a.job_id
                    ) ja ON ja.job_id = j.id
                    WHERE j.recruiter_id = ?
                ) job_agg
                CROSS JOIN (
                    SELECT
                        COUNT(a.id) AS period_total_applications,
                        COALESCE(SUM(CASE WHEN a.status = 'PENDING'     THEN 1 ELSE 0 END), 0) AS period_pending_applications,
                        COALESCE(SUM(CASE WHEN a.status = 'SHORTLISTED' THEN 1 ELSE 0 END), 0) AS period_shortlisted_applications,
                        COALESCE(SUM(CASE WHEN a.status = 'SELECTED'    THEN 1 ELSE 0 END), 0) AS period_selected_applications,
                        COALESCE(SUM(CASE WHEN a.status = 'REJECTED'    THEN 1 ELSE 0 END), 0) AS period_rejected_applications
                    FROM applications a
                    JOIN jobs j ON j.id = a.job_id
                    WHERE j.recruiter_id = ?
                      AND (? IS NULL OR a.applied_at >= ?)
                      AND (? IS NULL OR a.applied_at < ?)
                ) app_agg
                WHERE u.id = ?
                  AND u.role = 'RECRUITER'
                """;

        RecruiterOverviewKPI kpi = new RecruiterOverviewKPI();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int p = 1;
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, to);
            bindOptionalTimestamp(ps, p++, to);
            ps.setInt(p++, recruiterId); // ja inner
            ps.setInt(p++, recruiterId); // job_agg WHERE
            ps.setInt(p++, recruiterId); // app_agg WHERE
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, from);
            bindOptionalTimestamp(ps, p++, to);
            bindOptionalTimestamp(ps, p++, to);
            ps.setInt(p, recruiterId);   // outer users WHERE

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setRecruiterAccountPresent(true);
                    kpi.setRecruiterName(rs.getString("recruiter_name"));

                    String rpCompany = rs.getString("rp_company_name");
                    String fallbackCompany = rs.getString("fallback_company");
                    String rpIndustry = rs.getString("rp_industry");
                    boolean hasWebsite = rs.getString("rp_website") != null;
                    boolean hasDesc = rs.getString("rp_description") != null;

                    kpi.setProfileCompleted(rpCompany != null);
                    kpi.setCompanyName(rpCompany != null ? rpCompany : (fallbackCompany != null ? fallbackCompany : ""));
                    kpi.setIndustry(rpIndustry);
                    kpi.setCompanyWebsitePresent(hasWebsite);
                    kpi.setCompanyDescriptionPresent(hasDesc);

                    int filledFields = (rpCompany != null ? 1 : 0)
                            + (rpIndustry != null ? 1 : 0)
                            + (hasWebsite ? 1 : 0)
                            + (hasDesc ? 1 : 0);
                    kpi.setProfileCompletenessRate((filledFields * 100.0) / 4.0);

                    kpi.setTotalJobs(rs.getInt("total_jobs"));
                    kpi.setActiveStatusJobs(rs.getInt("active_status_jobs"));
                    kpi.setOpenAcceptingJobs(rs.getInt("open_accepting_jobs"));
                    kpi.setExpiredActiveJobs(rs.getInt("expired_active_jobs"));
                    kpi.setClosingWithin7DaysJobs(rs.getInt("closing_7d_jobs"));
                    kpi.setDraftJobs(rs.getInt("draft_jobs"));
                    kpi.setClosedJobs(rs.getInt("closed_jobs"));
                    kpi.setZeroApplicantOpenJobs(rs.getInt("zero_applicant_open_jobs"));
                    kpi.setJobsCreatedInPeriod(rs.getInt("jobs_created_in_period"));

                    kpi.setLifetimeTotalApplications(rs.getInt("lifetime_total_applications"));
                    kpi.setLifetimePendingApplications(rs.getInt("lifetime_pending_applications"));

                    kpi.setPeriodTotalApplications(rs.getInt("period_total_applications"));
                    kpi.setPeriodPendingApplications(rs.getInt("period_pending_applications"));
                    kpi.setPeriodShortlistedApplications(rs.getInt("period_shortlisted_applications"));
                    kpi.setPeriodSelectedApplications(rs.getInt("period_selected_applications"));
                    kpi.setPeriodRejectedApplications(rs.getInt("period_rejected_applications"));
                }
            }
        }
        return kpi;
    }

    // =========================================================================
    // RQ2 — Application Pipeline
    // Scoped to j.recruiter_id = ? and applications.applied_at in [from, to)
    // =========================================================================

    /**
     * Fetches application pipeline funnel counts and unique candidate counts
     * for jobs owned by {@code recruiterId}.
     */
    public RecruiterPipelineKPI fetchPipelineKPI(int recruiterId, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        requireValidRecruiterId(recruiterId);

        String sql = """
                SELECT
                    COUNT(a.id) AS total_applications,
                    COALESCE(SUM(CASE WHEN a.status = 'PENDING'     THEN 1 ELSE 0 END), 0) AS pending_count,
                    COALESCE(SUM(CASE WHEN a.status = 'SHORTLISTED' THEN 1 ELSE 0 END), 0) AS shortlisted_count,
                    COALESCE(SUM(CASE WHEN a.status = 'SELECTED'    THEN 1 ELSE 0 END), 0) AS selected_count,
                    COALESCE(SUM(CASE WHEN a.status = 'REJECTED'    THEN 1 ELSE 0 END), 0) AS rejected_count,
                    COUNT(DISTINCT a.student_id) AS unique_applicants,
                    COUNT(DISTINCT CASE WHEN a.status = 'PENDING'     THEN a.student_id END) AS unique_pending,
                    COUNT(DISTINCT CASE WHEN a.status = 'SHORTLISTED' THEN a.student_id END) AS unique_shortlisted,
                    COUNT(DISTINCT CASE WHEN a.status = 'SELECTED'    THEN a.student_id END) AS unique_selected,
                    COUNT(DISTINCT CASE WHEN a.status = 'REJECTED'    THEN a.student_id END) AS unique_rejected,
                    COUNT(DISTINCT a.job_id) AS jobs_with_applications
                FROM applications a
                JOIN jobs j ON j.id = a.job_id
                WHERE j.recruiter_id = ?
                  AND (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                """;

        RecruiterPipelineKPI kpi = new RecruiterPipelineKPI();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recruiterId);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, from);
            bindOptionalTimestamp(ps, 4, to);
            bindOptionalTimestamp(ps, 5, to);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    kpi.setTotalApplications(rs.getInt("total_applications"));
                    kpi.setPendingApplications(rs.getInt("pending_count"));
                    kpi.setShortlistedApplications(rs.getInt("shortlisted_count"));
                    kpi.setSelectedApplications(rs.getInt("selected_count"));
                    kpi.setRejectedApplications(rs.getInt("rejected_count"));
                    kpi.setUniqueApplicants(rs.getInt("unique_applicants"));
                    kpi.setUniquePendingApplicants(rs.getInt("unique_pending"));
                    kpi.setUniqueShortlistedApplicants(rs.getInt("unique_shortlisted"));
                    kpi.setUniqueSelectedApplicants(rs.getInt("unique_selected"));
                    kpi.setUniqueRejectedApplicants(rs.getInt("unique_rejected"));
                    kpi.setJobsWithApplications(rs.getInt("jobs_with_applications"));
                }
            }
        }
        return kpi;
    }

    // =========================================================================
    // RQ2/RQ3 — Status Distribution
    // Grouped by a.status; missing canonical statuses are zero-filled by service
    // =========================================================================

    /**
     * Fetches per-status application and unique applicant counts for {@code recruiterId}.
     */
    public List<RecruiterStatusDistribution> fetchStatusDistribution(int recruiterId,
                                                                     LocalDateTime from,
                                                                     LocalDateTime to)
            throws SQLException {
        requireValidRecruiterId(recruiterId);

        String sql = """
                SELECT
                    a.status,
                    COUNT(*) AS application_count,
                    COUNT(DISTINCT a.student_id) AS unique_applicants
                FROM applications a
                JOIN jobs j ON j.id = a.job_id
                WHERE j.recruiter_id = ?
                  AND (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                GROUP BY a.status
                ORDER BY FIELD(a.status, 'PENDING', 'SHORTLISTED', 'SELECTED', 'REJECTED')
                """;

        List<RecruiterStatusDistribution> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recruiterId);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, from);
            bindOptionalTimestamp(ps, 4, to);
            bindOptionalTimestamp(ps, 5, to);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RecruiterStatusDistribution(
                            rs.getString("status"),
                            rs.getInt("application_count"),
                            rs.getInt("unique_applicants")
                    ));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // RQ3 — Job Performance
    // Single grouped query covering every job owned by the recruiter
    // =========================================================================

    /**
     * Fetches per-job application performance metrics for all jobs owned by
     * {@code recruiterId}, including zero-application jobs.
     */
    public List<RecruiterJobPerformance> fetchJobPerformance(int recruiterId,
                                                             LocalDateTime from,
                                                             LocalDateTime to)
            throws SQLException {
        requireValidRecruiterId(recruiterId);

        String sql = """
                SELECT
                    j.id AS job_id,
                    j.title,
                    j.company_name,
                    j.job_type,
                    j.location,
                    j.deadline,
                    j.status,
                    j.created_at,
                    CASE
                        WHEN j.status = 'ACTIVE' AND j.deadline IS NOT NULL AND j.deadline < CURDATE()
                        THEN 1 ELSE 0
                    END AS is_expired_active,
                    COUNT(sa.id) AS lifetime_applications,
                    COALESCE(SUM(CASE WHEN sa.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS lifetime_pending_count,
                    MAX(sa.applied_at) AS last_applied_at,
                    COALESCE(SUM(CASE WHEN sa.in_period = 1 THEN 1 ELSE 0 END), 0) AS period_applications,
                    COALESCE(SUM(CASE WHEN sa.in_period = 1 AND sa.status = 'PENDING'     THEN 1 ELSE 0 END), 0) AS period_pending,
                    COALESCE(SUM(CASE WHEN sa.in_period = 1 AND sa.status = 'SHORTLISTED' THEN 1 ELSE 0 END), 0) AS period_shortlisted,
                    COALESCE(SUM(CASE WHEN sa.in_period = 1 AND sa.status = 'SELECTED'    THEN 1 ELSE 0 END), 0) AS period_selected,
                    COALESCE(SUM(CASE WHEN sa.in_period = 1 AND sa.status = 'REJECTED'    THEN 1 ELSE 0 END), 0) AS period_rejected
                FROM jobs j
                LEFT JOIN (
                    SELECT
                        a.id,
                        a.job_id,
                        a.status,
                        a.applied_at,
                        CASE
                            WHEN (? IS NULL OR a.applied_at >= ?)
                             AND (? IS NULL OR a.applied_at < ?)
                            THEN 1 ELSE 0
                        END AS in_period
                    FROM applications a
                    JOIN jobs j2 ON j2.id = a.job_id
                    WHERE j2.recruiter_id = ?
                ) sa ON sa.job_id = j.id
                WHERE j.recruiter_id = ?
                GROUP BY j.id, j.title, j.company_name, j.job_type, j.location, j.deadline, j.status, j.created_at
                ORDER BY period_applications DESC, lifetime_applications DESC, j.created_at DESC, j.id DESC
                """;

        List<RecruiterJobPerformance> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOptionalTimestamp(ps, 1, from);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, to);
            bindOptionalTimestamp(ps, 4, to);
            ps.setInt(5, recruiterId);
            ps.setInt(6, recruiterId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecruiterJobPerformance row = new RecruiterJobPerformance();
                    row.setJobId(rs.getInt("job_id"));
                    row.setTitle(rs.getString("title"));
                    row.setCompanyName(rs.getString("company_name"));
                    row.setJobType(rs.getString("job_type"));
                    row.setLocation(rs.getString("location"));

                    Date deadline = rs.getDate("deadline");
                    if (deadline != null) row.setDeadline(deadline.toLocalDate());

                    row.setStatus(rs.getString("status"));
                    row.setExpiredActive(rs.getInt("is_expired_active") == 1);

                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) row.setCreatedAt(createdAt.toLocalDateTime());

                    row.setLifetimeApplications(rs.getInt("lifetime_applications"));
                    row.setLifetimePendingCount(rs.getInt("lifetime_pending_count"));

                    Timestamp lastApplied = rs.getTimestamp("last_applied_at");
                    if (lastApplied != null) row.setLastAppliedAt(lastApplied.toLocalDateTime());

                    row.setPeriodApplications(rs.getInt("period_applications"));
                    row.setPendingCount(rs.getInt("period_pending"));
                    row.setShortlistedCount(rs.getInt("period_shortlisted"));
                    row.setSelectedCount(rs.getInt("period_selected"));
                    row.setRejectedCount(rs.getInt("period_rejected"));

                    list.add(row);
                }
            }
        }
        return list;
    }

    // =========================================================================
    // RQ5 — Job Type Distribution
    // Grouped by j.job_type; pre-aggregates period apps per job_id to prevent fan-out
    // =========================================================================

    /**
     * Fetches job portfolio and period application totals grouped by {@code job_type}.
     * Missing canonical job types are zero-filled by the service layer.
     */
    public List<RecruiterJobTypeStat> fetchJobTypeStats(int recruiterId,
                                                        LocalDateTime from,
                                                        LocalDateTime to)
            throws SQLException {
        requireValidRecruiterId(recruiterId);

        String sql = """
                SELECT
                    j.job_type,
                    COUNT(j.id) AS total_jobs,
                    COALESCE(SUM(CASE WHEN j.status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active_status_jobs,
                    COALESCE(SUM(CASE WHEN j.status = 'ACTIVE' AND (j.deadline IS NULL OR j.deadline >= CURDATE()) THEN 1 ELSE 0 END), 0) AS open_accepting_jobs,
                    COALESCE(SUM(CASE WHEN j.status = 'ACTIVE' AND j.deadline < CURDATE() THEN 1 ELSE 0 END), 0) AS expired_active_jobs,
                    COALESCE(SUM(CASE WHEN j.status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_jobs,
                    COALESCE(SUM(CASE WHEN j.status = 'DRAFT'  THEN 1 ELSE 0 END), 0) AS draft_jobs,
                    COALESCE(SUM(COALESCE(ja.period_apps, 0)), 0) AS period_applications,
                    COALESCE(SUM(COALESCE(ja.shortlisted_apps, 0)), 0) AS shortlisted_applications,
                    COALESCE(SUM(COALESCE(ja.selected_apps, 0)), 0) AS selected_applications,
                    COALESCE(SUM(COALESCE(ja.rejected_apps, 0)), 0) AS rejected_applications
                FROM jobs j
                LEFT JOIN (
                    SELECT
                        a.job_id,
                        COUNT(a.id) AS period_apps,
                        SUM(CASE WHEN a.status = 'SHORTLISTED' THEN 1 ELSE 0 END) AS shortlisted_apps,
                        SUM(CASE WHEN a.status = 'SELECTED'    THEN 1 ELSE 0 END) AS selected_apps,
                        SUM(CASE WHEN a.status = 'REJECTED'    THEN 1 ELSE 0 END) AS rejected_apps
                    FROM applications a
                    JOIN jobs j2 ON j2.id = a.job_id
                    WHERE j2.recruiter_id = ?
                      AND (? IS NULL OR a.applied_at >= ?)
                      AND (? IS NULL OR a.applied_at < ?)
                    GROUP BY a.job_id
                ) ja ON ja.job_id = j.id
                WHERE j.recruiter_id = ?
                GROUP BY j.job_type
                ORDER BY FIELD(j.job_type, 'INTERNSHIP', 'FULL_TIME', 'PART_TIME')
                """;

        List<RecruiterJobTypeStat> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recruiterId);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, from);
            bindOptionalTimestamp(ps, 4, to);
            bindOptionalTimestamp(ps, 5, to);
            ps.setInt(6, recruiterId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RecruiterJobTypeStat(
                            rs.getString("job_type"),
                            rs.getInt("total_jobs"),
                            rs.getInt("active_status_jobs"),
                            rs.getInt("open_accepting_jobs"),
                            rs.getInt("expired_active_jobs"),
                            rs.getInt("closed_jobs"),
                            rs.getInt("draft_jobs"),
                            rs.getInt("period_applications"),
                            rs.getInt("shortlisted_applications"),
                            rs.getInt("selected_applications"),
                            rs.getInt("rejected_applications")
                    ));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // RQ4 — Application Trends (DAILY, WEEKLY, MONTHLY buckets)
    // =========================================================================

    /**
     * Fetches raw application counts grouped by calendar bucket according to
     * {@code granularity}. Missing zero-count buckets are filled chronologically
     * by the service layer.
     */
    public List<RecruiterTrendPoint> fetchApplicationTrend(int recruiterId,
                                                           LocalDateTime from,
                                                           LocalDateTime to,
                                                           RecruiterTrendPoint.TrendGranularity granularity)
            throws SQLException {
        requireValidRecruiterId(recruiterId);
        if (granularity == null) {
            throw new IllegalArgumentException("granularity must not be null");
        }

        String bucketExpr = switch (granularity) {
            case DAILY   -> "DATE(a.applied_at)";
            case WEEKLY  -> "DATE_SUB(DATE(a.applied_at), INTERVAL WEEKDAY(a.applied_at) DAY)";
            case MONTHLY -> "CAST(DATE_FORMAT(a.applied_at, '%Y-%m-01') AS DATE)";
        };

        String sql = "SELECT "
                + bucketExpr + " AS period_start, "
                + "COUNT(*) AS applications_count, "
                + "COALESCE(SUM(CASE WHEN a.status = 'SHORTLISTED' THEN 1 ELSE 0 END), 0) AS shortlisted_count, "
                + "COALESCE(SUM(CASE WHEN a.status = 'SELECTED'    THEN 1 ELSE 0 END), 0) AS selected_count, "
                + "COALESCE(SUM(CASE WHEN a.status = 'REJECTED'    THEN 1 ELSE 0 END), 0) AS rejected_count "
                + "FROM applications a "
                + "JOIN jobs j ON j.id = a.job_id "
                + "WHERE j.recruiter_id = ? "
                + "  AND (? IS NULL OR a.applied_at >= ?) "
                + "  AND (? IS NULL OR a.applied_at < ?) "
                + "GROUP BY " + bucketExpr + " "
                + "ORDER BY period_start ASC";

        List<RecruiterTrendPoint> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recruiterId);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, from);
            bindOptionalTimestamp(ps, 4, to);
            bindOptionalTimestamp(ps, 5, to);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("period_start");
                    LocalDate start = (d != null) ? d.toLocalDate() : null;
                    list.add(new RecruiterTrendPoint(
                            granularity,
                            start,
                            null,
                            null,
                            rs.getInt("applications_count"),
                            rs.getInt("shortlisted_count"),
                            rs.getInt("selected_count"),
                            rs.getInt("rejected_count")
                    ));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // RQ6 — Recent Hiring Activity Feed
    // Strictly derived from real persisted timestamps and scoped to recruiterId
    // =========================================================================

    /**
     * Fetches the most recent hiring activity events across this recruiter's jobs:
     * {@code JOB_POSTED}, {@code APPLICATION_RECEIVED}, and {@code APPLICATION_REVIEWED}.
     */
    public List<RecruiterActivityEvent> fetchRecentActivity(int recruiterId,
                                                            LocalDateTime from,
                                                            LocalDateTime to,
                                                            int limit)
            throws SQLException {
        requireValidRecruiterId(recruiterId);
        requirePositiveLimit(limit);

        String sql = """
                (SELECT
                    'JOB_POSTED' AS event_type,
                    j.id AS job_id,
                    j.company_name AS subject_name,
                    j.title AS job_title,
                    CONCAT('Posted ', j.job_type, ' role "', j.title, '" (', j.status, ')') AS summary,
                    j.created_at AS occurred_at
                FROM jobs j
                WHERE j.recruiter_id = ?
                  AND (? IS NULL OR j.created_at >= ?)
                  AND (? IS NULL OR j.created_at < ?)
                ORDER BY j.created_at DESC
                LIMIT ?)
                UNION ALL
                (SELECT
                    'APPLICATION_RECEIVED' AS event_type,
                    j.id AS job_id,
                    u.full_name AS subject_name,
                    j.title AS job_title,
                    CONCAT(u.full_name, ' applied for "', j.title, '"') AS summary,
                    a.applied_at AS occurred_at
                FROM applications a
                JOIN jobs j ON j.id = a.job_id
                JOIN users u ON u.id = a.student_id
                WHERE j.recruiter_id = ?
                  AND (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                ORDER BY a.applied_at DESC
                LIMIT ?)
                UNION ALL
                (SELECT
                    'APPLICATION_REVIEWED' AS event_type,
                    j.id AS job_id,
                    u.full_name AS subject_name,
                    j.title AS job_title,
                    CONCAT('Updated ', u.full_name, ' for "', j.title, '" to ', a.status) AS summary,
                    a.updated_at AS occurred_at
                FROM applications a
                JOIN jobs j ON j.id = a.job_id
                JOIN users u ON u.id = a.student_id
                WHERE j.recruiter_id = ?
                  AND a.updated_at > a.applied_at
                  AND (? IS NULL OR a.updated_at >= ?)
                  AND (? IS NULL OR a.updated_at < ?)
                ORDER BY a.updated_at DESC
                LIMIT ?)
                ORDER BY occurred_at DESC
                LIMIT ?
                """;

        List<RecruiterActivityEvent> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int p = 1;
            for (int branch = 0; branch < 3; branch++) {
                ps.setInt(p++, recruiterId);
                bindOptionalTimestamp(ps, p++, from);
                bindOptionalTimestamp(ps, p++, from);
                bindOptionalTimestamp(ps, p++, to);
                bindOptionalTimestamp(ps, p++, to);
                ps.setInt(p++, limit);
            }
            ps.setInt(p, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("occurred_at");
                    list.add(new RecruiterActivityEvent(
                            rs.getString("event_type"),
                            rs.getInt("job_id"),
                            rs.getString("subject_name"),
                            rs.getString("job_title"),
                            rs.getString("summary"),
                            ts != null ? ts.toLocalDateTime() : null
                    ));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // RQ7 — Pending Applicants Review Queue
    // Recent PENDING applications without private contact or file path exposure
    // =========================================================================

    /**
     * Fetches recent {@code PENDING} applications for {@code recruiterId}'s jobs.
     */
    public List<RecruiterPendingApplicant> fetchPendingApplicants(int recruiterId,
                                                                  LocalDateTime from,
                                                                  LocalDateTime to,
                                                                  int limit)
            throws SQLException {
        requireValidRecruiterId(recruiterId);
        requirePositiveLimit(limit);

        String sql = """
                SELECT
                    a.id AS application_id,
                    j.id AS job_id,
                    j.title AS job_title,
                    j.job_type,
                    u.full_name AS applicant_name,
                    NULLIF(TRIM(sp.college), '') AS college,
                    NULLIF(TRIM(sp.degree), '') AS degree,
                    a.applied_at,
                    GREATEST(0, TIMESTAMPDIFF(DAY, a.applied_at, NOW())) AS days_pending
                FROM applications a
                JOIN jobs j ON j.id = a.job_id
                JOIN users u ON u.id = a.student_id
                LEFT JOIN student_profiles sp ON sp.user_id = u.id
                WHERE j.recruiter_id = ?
                  AND a.status = 'PENDING'
                  AND (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                ORDER BY a.applied_at DESC, a.id DESC
                LIMIT ?
                """;

        List<RecruiterPendingApplicant> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recruiterId);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, from);
            bindOptionalTimestamp(ps, 4, to);
            bindOptionalTimestamp(ps, 5, to);
            ps.setInt(6, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("applied_at");
                    list.add(new RecruiterPendingApplicant(
                            rs.getInt("application_id"),
                            rs.getInt("job_id"),
                            rs.getString("job_title"),
                            rs.getString("job_type"),
                            rs.getString("applicant_name"),
                            rs.getString("college"),
                            rs.getString("degree"),
                            ts != null ? ts.toLocalDateTime() : null,
                            rs.getLong("days_pending")
                    ));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // RQ8 — Applicant Readiness
    // Pre-aggregated resumes & latest job-specific resume_analyses (MAX(id))
    // =========================================================================

    /**
     * Fetches applicant pool readiness signals (profile completion, resume availability,
     * and job-specific AI match coverage/average using the latest analysis per
     * {@code (student_id, job_id)} where {@code job_id IS NOT NULL}).
     */
    public RecruiterApplicantReadiness fetchApplicantReadiness(int recruiterId,
                                                             LocalDateTime from,
                                                             LocalDateTime to)
            throws SQLException {
        requireValidRecruiterId(recruiterId);

        String sql = """
                SELECT
                    COUNT(a.id) AS total_applications,
                    COUNT(DISTINCT a.student_id) AS unique_applicants,
                    COUNT(DISTINCT CASE
                        WHEN sp.college IS NOT NULL AND TRIM(sp.college) <> ''
                         AND sp.degree  IS NOT NULL AND TRIM(sp.degree)  <> ''
                         AND sp.graduation_year > 2000
                        THEN a.student_id END) AS applicants_complete_profile,
                    COUNT(DISTINCT CASE
                        WHEN COALESCE(res_agg.has_active_resume, 0) = 1
                        THEN a.student_id END) AS applicants_active_resume,
                    COUNT(DISTINCT CASE
                        WHEN COALESCE(res_agg.has_any_resume, 0) = 1
                          OR (sp.resume_path IS NOT NULL AND TRIM(sp.resume_path) <> '')
                        THEN a.student_id END) AS applicants_any_resume,
                    COUNT(DISTINCT CASE
                        WHEN latest_ra.overall_score IS NOT NULL
                        THEN a.id END) AS apps_with_job_ai_analysis,
                    COUNT(DISTINCT CASE
                        WHEN latest_ra.overall_score IS NOT NULL
                        THEN a.student_id END) AS unique_applicants_with_job_ai_score,
                    ROUND(AVG(CASE
                        WHEN latest_ra.overall_score IS NOT NULL
                        THEN latest_ra.overall_score END), 1) AS avg_job_ai_score
                FROM applications a
                JOIN jobs j ON j.id = a.job_id
                LEFT JOIN student_profiles sp ON sp.user_id = a.student_id
                LEFT JOIN (
                    SELECT
                        student_id,
                        MAX(CASE WHEN is_active = TRUE THEN 1 ELSE 0 END) AS has_active_resume,
                        1 AS has_any_resume
                    FROM resumes
                    GROUP BY student_id
                ) res_agg ON res_agg.student_id = a.student_id
                LEFT JOIN (
                    SELECT ra1.student_id, ra1.job_id, ra1.overall_score
                    FROM resume_analyses ra1
                    INNER JOIN (
                        SELECT student_id, job_id, MAX(id) AS max_id
                        FROM resume_analyses
                        WHERE job_id IS NOT NULL
                        GROUP BY student_id, job_id
                    ) latest ON latest.max_id = ra1.id
                ) latest_ra ON latest_ra.student_id = a.student_id AND latest_ra.job_id = a.job_id
                WHERE j.recruiter_id = ?
                  AND (? IS NULL OR a.applied_at >= ?)
                  AND (? IS NULL OR a.applied_at < ?)
                """;

        RecruiterApplicantReadiness readiness = new RecruiterApplicantReadiness();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recruiterId);
            bindOptionalTimestamp(ps, 2, from);
            bindOptionalTimestamp(ps, 3, from);
            bindOptionalTimestamp(ps, 4, to);
            bindOptionalTimestamp(ps, 5, to);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    readiness.setTotalApplicationsInPeriod(rs.getInt("total_applications"));
                    readiness.setUniqueApplicants(rs.getInt("unique_applicants"));
                    readiness.setApplicantsWithCompleteProfile(rs.getInt("applicants_complete_profile"));
                    readiness.setApplicantsWithActiveResume(rs.getInt("applicants_active_resume"));
                    readiness.setApplicantsWithAnyResume(rs.getInt("applicants_any_resume"));
                    readiness.setApplicationsWithJobAiAnalysis(rs.getInt("apps_with_job_ai_analysis"));
                    readiness.setUniqueApplicantsWithJobAiScore(rs.getInt("unique_applicants_with_job_ai_score"));

                    double avgScore = rs.getDouble("avg_job_ai_score");
                    if (rs.wasNull() || !Double.isFinite(avgScore)) {
                        readiness.setAvgJobAiScore(null);
                    } else {
                        readiness.setAvgJobAiScore(avgScore);
                    }
                }
            }
        }
        return readiness;
    }
}

