package com.hirehub.service;

import com.hirehub.dao.AdminAnalyticsDAO;
import com.hirehub.model.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service layer for Admin Analytics Dashboard data loading.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate {@code LocalDate} range inputs ({@code from <= to}) and convert
 *       to half-open {@code LocalDateTime} bounds {@code [from.atStartOfDay(), to.plusDays(1).atStartOfDay())}.</li>
 *   <li>Compute derived percentage / conversion rates with divide-by-zero guards.</li>
 *   <li>Zero-fill missing {@link Application.Status} and {@link Job.JobType} buckets.</li>
 *   <li>Guard all numeric derived fields against {@code NaN} and {@code Infinity}.</li>
 *   <li>Catch {@link SQLException} and log to {@code System.err}; return a non-null
 *       snapshot carrying safe default sub-objects and an error message rather than
 *       propagating raw SQL exceptions to UI controllers.</li>
 * </ul>
 *
 * <p>This service is READ-ONLY. It never modifies database records.</p>
 */
public class AdminAnalyticsService {

    private static final String LOG_PREFIX = "[AdminAnalyticsService]";

    /** Funnel display order derived from {@link Application.Status}. */
    private static final List<String> ALL_STATUSES = List.of(
            Application.Status.PENDING.name(),
            Application.Status.SHORTLISTED.name(),
            Application.Status.SELECTED.name(),
            Application.Status.REJECTED.name()
    );

    /** Canonical job types derived from {@link Job.JobType}. */
    private static final List<String> ALL_JOB_TYPES =
            Arrays.stream(Job.JobType.values()).map(Enum::name).toList();

    private static final int TOP_JOBS_LIMIT        = 10;
    private static final int RECENT_ACTIVITY_LIMIT = 20;

    private final AdminAnalyticsDAO dao;

    public AdminAnalyticsService() {
        this.dao = new AdminAnalyticsDAO();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates a date range, rejecting {@code from > to}.
     * Either or both bounds may be {@code null} (open-ended).
     *
     * @throws IllegalArgumentException if both bounds are non-null and {@code from.isAfter(to)}
     */
    public void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Date range is invalid: 'from' (" + from + ") is after 'to' (" + to + ").");
        }
    }

    /**
     * Loads a complete dashboard snapshot for the given inclusive date range.
     *
     * @param from inclusive start date ({@code null} = unbounded start)
     * @param to   inclusive end date   ({@code null} = unbounded end)
     * @return non-null snapshot with data or error state
     * @throws IllegalArgumentException if {@code from != null && to != null && from.isAfter(to)}
     */
    public AdminDashboardSnapshot loadSnapshot(LocalDate from, LocalDate to) {
        validateRange(from, to);

        LocalDateTime dtFrom = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime dtTo   = (to   != null) ? to.plusDays(1).atStartOfDay() : null;

        String rangeLabel = buildRangeLabel(from, to);
        return loadInternal(from, to, dtFrom, dtTo, rangeLabel);
    }

    /**
     * Loads a complete dashboard snapshot covering all time.
     *
     * @return non-null snapshot with data or error state
     */
    public AdminDashboardSnapshot loadAllTimeSnapshot() {
        return loadInternal(null, null, null, null, "All Time");
    }

    /**
     * Loads a snapshot for the most recent {@code days} calendar days (inclusive of today).
     *
     * @param days number of days (must be &gt;= 1)
     * @return non-null snapshot with data or error state
     * @throws IllegalArgumentException if {@code days < 1}
     */
    public AdminDashboardSnapshot loadLastNDaysSnapshot(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be >= 1, got: " + days);
        }
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        return loadSnapshot(from, to);
    }

    // -------------------------------------------------------------------------
    // Internal loader
    // -------------------------------------------------------------------------

    private AdminDashboardSnapshot loadInternal(LocalDate fromDate, LocalDate toDate,
                                                LocalDateTime from, LocalDateTime to,
                                                String rangeLabel) {
        AdminDashboardSnapshot snapshot = new AdminDashboardSnapshot();
        snapshot.setFilterFromDate(fromDate);
        snapshot.setFilterToDate(toDate);
        snapshot.setRefreshedAt(LocalDateTime.now());
        snapshot.setDateRangeLabel(rangeLabel);

        // Pre-populate safe empty sub-objects so partial failures never expose nulls
        snapshot.setOverviewKPI(new AdminOverviewKPI());
        snapshot.setJobKPI(new AdminJobKPI());
        snapshot.setApplicationKPI(new AdminApplicationKPI());
        snapshot.setStatusDistribution(zeroFillStatusDistribution(Collections.emptyList()));
        snapshot.setJobTypeStats(zeroFillJobTypeStats(Collections.emptyList()));
        snapshot.setStudentParticipation(new AdminStudentParticipation());

        try {
            // Q1 — Overview KPI
            AdminOverviewKPI overviewKPI = dao.fetchOverviewKPI(from, to);
            computeOverviewRates(overviewKPI);
            snapshot.setOverviewKPI(overviewKPI);

            // Q2 — Job KPI
            AdminJobKPI jobKPI = dao.fetchJobKPI(from, to);
            snapshot.setJobKPI(jobKPI);

            // Q3 — Application KPI
            AdminApplicationKPI appKPI = dao.fetchApplicationKPI(from, to);
            computeApplicationRates(appKPI);
            snapshot.setApplicationKPI(appKPI);

            // Q4 — Status Distribution (zero-fill + percentages)
            List<AdminStatusDistribution> statusDist = dao.fetchStatusDistribution(from, to);
            statusDist = zeroFillStatusDistribution(statusDist);
            computeStatusPercentages(statusDist, appKPI.getTotalApplications());
            snapshot.setStatusDistribution(statusDist);

            // Q5 — Job Type Stats (zero-fill)
            List<AdminJobTypeStat> jobTypeStats = dao.fetchJobTypeStats(from, to);
            jobTypeStats = zeroFillJobTypeStats(jobTypeStats);
            snapshot.setJobTypeStats(jobTypeStats);

            // Q6 — Top Jobs
            List<AdminTopJob> topJobs = dao.fetchTopJobs(from, to, TOP_JOBS_LIMIT);
            snapshot.setTopJobs(topJobs);

            // Q7 — Student Participation
            AdminStudentParticipation participation = dao.fetchStudentParticipation(from, to);
            sanitizeStudentParticipation(participation);
            snapshot.setStudentParticipation(participation);

            // Q8 — Recruiter Engagement
            List<AdminRecruiterEngagement> engagement = dao.fetchRecruiterEngagement(from, to);
            snapshot.setRecruiterEngagement(engagement);

            // Q9 — Recent Activity
            List<AdminActivityEvent> activity = dao.fetchRecentActivity(from, to, RECENT_ACTIVITY_LIMIT);
            snapshot.setRecentActivity(activity);

            // Q10 — Application Trend
            List<AdminTrendPoint> trend = dao.fetchApplicationTrend(from, to);
            snapshot.setApplicationTrend(trend);

        } catch (SQLException e) {
            System.err.println(LOG_PREFIX + " SQL error loading dashboard snapshot: " + e.getMessage());
            snapshot.setLoadError("Failed to load analytics data: " + e.getMessage());
        } catch (Exception e) {
            System.err.println(LOG_PREFIX + " Unexpected error loading dashboard snapshot: " + e.getMessage());
            snapshot.setLoadError("Unexpected error: " + e.getMessage());
        }

        return snapshot;
    }

    // -------------------------------------------------------------------------
    // Rate / percentage computation helpers
    // -------------------------------------------------------------------------

    private void computeOverviewRates(AdminOverviewKPI kpi) {
        kpi.setStudentProfileCompletionRate(
                safePct(kpi.getStudentsCompletedProfile(), kpi.getTotalStudents()));
        kpi.setStudentResumeUploadRate(
                safePct(kpi.getStudentsWithAnyResume(), kpi.getTotalStudents()));
        kpi.setRecruiterProfileCompletionRate(
                safePct(kpi.getRecruitersCompletedProfile(), kpi.getTotalRecruiters()));
    }

    private void computeApplicationRates(AdminApplicationKPI kpi) {
        kpi.setSelectionRate(
                safePct(kpi.getSelectedApplications(), kpi.getTotalApplications()));
        kpi.setShortlistRate(
                safePct(kpi.getShortlistedApplications(), kpi.getTotalApplications()));
        kpi.setUniqueHireRate(
                safePct(kpi.getUniqueSelectedStudents(), kpi.getUniqueApplyingStudents()));
    }

    private void computeStatusPercentages(List<AdminStatusDistribution> list, int total) {
        for (AdminStatusDistribution row : list) {
            row.setPercentage(safePct(row.getApplicationCount(), total));
        }
    }

    /** Returns {@code numerator / denominator * 100}, or {@code 0.0} when denominator is zero. */
    private double safePct(int numerator, int denominator) {
        if (denominator <= 0) return 0.0;
        double result = (double) numerator / denominator * 100.0;
        return Double.isFinite(result) ? result : 0.0;
    }

    // -------------------------------------------------------------------------
    // Zero-fill helpers
    // -------------------------------------------------------------------------

    /**
     * Ensures all 4 {@link Application.Status} values are present in funnel display order.
     */
    private List<AdminStatusDistribution> zeroFillStatusDistribution(
            List<AdminStatusDistribution> fromDb) {

        Map<String, AdminStatusDistribution> byStatus = new LinkedHashMap<>();
        for (String s : ALL_STATUSES) {
            byStatus.put(s, new AdminStatusDistribution(s, 0, 0));
        }
        for (AdminStatusDistribution row : fromDb) {
            if (row.getStatus() != null) {
                byStatus.put(row.getStatus(), row);
            }
        }
        return new ArrayList<>(byStatus.values());
    }

    /**
     * Ensures all canonical {@link Job.JobType} values are present in the returned list.
     */
    private List<AdminJobTypeStat> zeroFillJobTypeStats(List<AdminJobTypeStat> fromDb) {
        Map<String, AdminJobTypeStat> byType = new LinkedHashMap<>();
        for (String t : ALL_JOB_TYPES) {
            byType.put(t, new AdminJobTypeStat(t, 0, 0, 0, 0, 0));
        }
        for (AdminJobTypeStat row : fromDb) {
            if (row.getJobType() != null) {
                byType.put(row.getJobType(), row);
            }
        }
        return new ArrayList<>(byType.values());
    }

    // -------------------------------------------------------------------------
    // Sanitization
    // -------------------------------------------------------------------------

    /** Ensures {@code avgResumeScore} is {@code null} when no analyses exist or non-finite. */
    public static void sanitizeStudentParticipation(AdminStudentParticipation p) {
        if (p == null) return;
        if (p.getTotalResumeAnalyses() == 0) {
            p.setAvgResumeScore(null);
            return;
        }
        if (p.getAvgResumeScore() != null && !Double.isFinite(p.getAvgResumeScore())) {
            p.setAvgResumeScore(null);
        }
    }

    // -------------------------------------------------------------------------
    // Label helper
    // -------------------------------------------------------------------------

    private String buildRangeLabel(LocalDate from, LocalDate to) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        if (from == null && to == null) return "All Time";
        if (from == null) return "Up to " + to.format(fmt);
        if (to == null)   return "From " + from.format(fmt);
        return from.format(fmt) + " - " + to.format(fmt);
    }
}
