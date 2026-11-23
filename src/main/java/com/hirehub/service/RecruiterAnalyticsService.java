package com.hirehub.service;

import com.hirehub.dao.RecruiterAnalyticsDAO;
import com.hirehub.model.*;
import com.hirehub.model.RecruiterTrendPoint.TrendGranularity;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Service layer for recruiter-scoped analytics.
 *
 * <p>Enforces positive {@code recruiterId} validation, validates date ranges,
 * selects trend granularity ({@code DAILY}, {@code WEEKLY}, {@code MONTHLY}),
 * zero-fills missing status/job-type/trend buckets, protects all ratios against
 * division by zero / {@code NaN} / {@code Infinity}, and wraps SQL errors into
 * {@link RecruiterDashboardSnapshot#getLoadError()}.</p>
 */
public class RecruiterAnalyticsService {

    public static final int DEFAULT_ACTIVITY_LIMIT = 15;
    public static final int DEFAULT_PENDING_LIMIT = 20;

    private static final DateTimeFormatter LABEL_DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TREND_DAY_FMT = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter TREND_MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy");

    private final RecruiterAnalyticsDAO analyticsDAO;

    public RecruiterAnalyticsService() {
        this(new RecruiterAnalyticsDAO());
    }

    public RecruiterAnalyticsService(RecruiterAnalyticsDAO analyticsDAO) {
        this.analyticsDAO = Objects.requireNonNull(analyticsDAO, "analyticsDAO must not be null");
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Loads a complete, isolated analytics snapshot for {@code recruiterId}.
     *
     * @param recruiterId positive recruiter user ID
     * @param from        inclusive start date (nullable)
     * @param to          inclusive end date (nullable)
     * @return non-null snapshot (check {@link RecruiterDashboardSnapshot#hasError()})
     * @throws IllegalArgumentException if {@code recruiterId <= 0} or {@code from > to}
     */
    public RecruiterDashboardSnapshot loadSnapshot(int recruiterId, LocalDate from, LocalDate to) {
        validateRecruiterId(recruiterId);
        validateRange(from, to);

        TrendGranularity granularity = determineTrendGranularity(from, to);

        RecruiterDashboardSnapshot snapshot = new RecruiterDashboardSnapshot();
        snapshot.setRecruiterId(recruiterId);
        snapshot.setFilterFromDate(from);
        snapshot.setFilterToDate(to);
        snapshot.setTrendGranularity(granularity);
        snapshot.setDateRangeLabel(buildRangeLabel(from, to));
        snapshot.setRefreshedAt(LocalDateTime.now());

        LocalDateTime dtFrom = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime dtTo = (to != null) ? to.plusDays(1).atStartOfDay() : null;

        try {
            RecruiterOverviewKPI overview = analyticsDAO.fetchOverviewKPI(recruiterId, dtFrom, dtTo);
            snapshot.setOverviewKPI(enrichOverviewKPI(overview));

            RecruiterPipelineKPI pipeline = analyticsDAO.fetchPipelineKPI(recruiterId, dtFrom, dtTo);
            enrichPipelineKPI(pipeline);
            snapshot.setPipelineKPI(pipeline);

            List<RecruiterStatusDistribution> rawStatuses =
                    analyticsDAO.fetchStatusDistribution(recruiterId, dtFrom, dtTo);
            snapshot.setStatusDistribution(
                    zeroFillStatusDistribution(rawStatuses, pipeline.getTotalApplications()));

            List<RecruiterJobPerformance> jobPerf =
                    analyticsDAO.fetchJobPerformance(recruiterId, dtFrom, dtTo);
            enrichJobPerformance(jobPerf);
            snapshot.setJobPerformance(jobPerf);

            List<RecruiterJobTypeStat> rawTypes =
                    analyticsDAO.fetchJobTypeStats(recruiterId, dtFrom, dtTo);
            snapshot.setJobTypeStats(zeroFillJobTypeStats(rawTypes));

            List<RecruiterTrendPoint> rawTrend =
                    analyticsDAO.fetchApplicationTrend(recruiterId, dtFrom, dtTo, granularity);
            snapshot.setApplicationTrend(
                    fillMissingTrendBuckets(rawTrend, from, to, granularity));

            List<RecruiterActivityEvent> activity =
                    analyticsDAO.fetchRecentActivity(recruiterId, dtFrom, dtTo, DEFAULT_ACTIVITY_LIMIT);
            snapshot.setRecentActivity(activity);

            List<RecruiterPendingApplicant> pending =
                    analyticsDAO.fetchPendingApplicants(recruiterId, dtFrom, dtTo, DEFAULT_PENDING_LIMIT);
            snapshot.setPendingApplicants(pending);

            RecruiterApplicantReadiness readiness =
                    analyticsDAO.fetchApplicantReadiness(recruiterId, dtFrom, dtTo);
            enrichApplicantReadiness(readiness);
            snapshot.setApplicantReadiness(readiness);

        } catch (SQLException e) {
            applyFallbackDefaults(snapshot, from, to, granularity);
            snapshot.setLoadError("Database error loading recruiter analytics: " + e.getMessage());
            System.err.println("[RecruiterAnalyticsService] SQL error: " + e.getMessage());
        } catch (Exception e) {
            applyFallbackDefaults(snapshot, from, to, granularity);
            snapshot.setLoadError("Unexpected error loading recruiter analytics: " + e.getMessage());
            System.err.println("[RecruiterAnalyticsService] Error: " + e.getMessage());
        }

        return snapshot;
    }

    /** Convenience method for loading an all-time snapshot for {@code recruiterId}. */
    public RecruiterDashboardSnapshot loadAllTimeSnapshot(int recruiterId) {
        return loadSnapshot(recruiterId, null, null);
    }

    /** Convenience method for loading a rolling last-N-days snapshot (inclusive of today). */
    public RecruiterDashboardSnapshot loadLastNDaysSnapshot(int recruiterId, int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days must be > 0, got: " + days);
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        return loadSnapshot(recruiterId, from, to);
    }

    // =========================================================================
    // Validation & Utility Helpers (public/package-visible for verification)
    // =========================================================================

    public void validateRecruiterId(int recruiterId) {
        if (recruiterId <= 0) {
            throw new IllegalArgumentException("Recruiter ID must be positive, got: " + recruiterId);
        }
    }

    public void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Invalid date range: 'from' (" + from + ") is after 'to' (" + to + ")");
        }
    }

    /**
     * Chooses trend bucket granularity from the requested range:
     * {@code <= 31} days -> {@link TrendGranularity#DAILY},
     * {@code 32..180} days -> {@link TrendGranularity#WEEKLY},
     * {@code > 180} days or unbounded -> {@link TrendGranularity#MONTHLY}.
     */
    public TrendGranularity determineTrendGranularity(LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            long days = ChronoUnit.DAYS.between(from, to) + 1L;
            if (days <= 31L) {
                return TrendGranularity.DAILY;
            }
            if (days <= 180L) {
                return TrendGranularity.WEEKLY;
            }
            return TrendGranularity.MONTHLY;
        }
        return TrendGranularity.MONTHLY;
    }

    /**
     * Zero-fills missing chronological buckets across {@code rawPoints} using
     * continuous stepping according to {@code granularity}.
     */
    public List<RecruiterTrendPoint> fillMissingTrendBuckets(List<RecruiterTrendPoint> rawPoints,
                                                             LocalDate from,
                                                             LocalDate to,
                                                             TrendGranularity granularity) {
        TrendGranularity effectiveGranularity =
                granularity != null ? granularity : determineTrendGranularity(from, to);

        Map<LocalDate, RecruiterTrendPoint> byStart = new TreeMap<>();
        if (rawPoints != null) {
            for (RecruiterTrendPoint pt : rawPoints) {
                if (pt == null || pt.getPeriodStart() == null) {
                    continue;
                }
                LocalDate bucketStart = alignToBucketStart(pt.getPeriodStart(), effectiveGranularity);
                byStart.merge(bucketStart, normalizeTrendPoint(pt, bucketStart, effectiveGranularity),
                        (a, b) -> new RecruiterTrendPoint(
                                effectiveGranularity,
                                bucketStart,
                                alignToBucketEnd(bucketStart, effectiveGranularity),
                                formatTrendLabel(bucketStart, effectiveGranularity),
                                a.getApplicationsCount() + b.getApplicationsCount(),
                                a.getShortlistedCount() + b.getShortlistedCount(),
                                a.getSelectedCount() + b.getSelectedCount(),
                                a.getRejectedCount() + b.getRejectedCount()
                        ));
            }
        }

        LocalDate axisStart;
        LocalDate axisEnd;

        if (from != null && to != null) {
            axisStart = alignToBucketStart(from, effectiveGranularity);
            axisEnd = alignToBucketStart(to, effectiveGranularity);
        } else if (from != null) {
            LocalDate latest = byStart.isEmpty() ? from : Collections.max(byStart.keySet());
            LocalDate upper = latest.isAfter(LocalDate.now()) ? latest : LocalDate.now();
            if (upper.isBefore(from)) {
                upper = from;
            }
            axisStart = alignToBucketStart(from, effectiveGranularity);
            axisEnd = alignToBucketStart(upper, effectiveGranularity);
        } else if (to != null) {
            if (byStart.isEmpty()) {
                return new ArrayList<>();
            }
            axisStart = Collections.min(byStart.keySet());
            axisEnd = alignToBucketStart(to, effectiveGranularity);
            if (axisStart.isAfter(axisEnd)) {
                return new ArrayList<>();
            }
        } else {
            if (byStart.isEmpty()) {
                return new ArrayList<>();
            }
            axisStart = Collections.min(byStart.keySet());
            axisEnd = Collections.max(byStart.keySet());
        }

        List<RecruiterTrendPoint> filled = new ArrayList<>();
        LocalDate cursor = axisStart;
        while (!cursor.isAfter(axisEnd)) {
            RecruiterTrendPoint existing = byStart.get(cursor);
            if (existing != null) {
                filled.add(existing);
            } else {
                filled.add(new RecruiterTrendPoint(
                        effectiveGranularity,
                        cursor,
                        alignToBucketEnd(cursor, effectiveGranularity),
                        formatTrendLabel(cursor, effectiveGranularity),
                        0, 0, 0, 0
                ));
            }
            cursor = nextBucketStart(cursor, effectiveGranularity);
        }
        return filled;
    }

    /** Zero-fills all canonical {@link Application.Status} rows and computes percentages. */
    public List<RecruiterStatusDistribution> zeroFillStatusDistribution(
            List<RecruiterStatusDistribution> raw, int totalApplications) {
        Map<String, RecruiterStatusDistribution> byStatus = new LinkedHashMap<>();
        String[] orderedCanonical = {"PENDING", "SHORTLISTED", "SELECTED", "REJECTED"};
        for (String name : orderedCanonical) {
            byStatus.put(name, new RecruiterStatusDistribution(name, 0, 0));
        }
        for (Application.Status st : Application.Status.values()) {
            byStatus.putIfAbsent(st.name(), new RecruiterStatusDistribution(st.name(), 0, 0));
        }

        int fallbackTotal = 0;
        if (raw != null) {
            for (RecruiterStatusDistribution item : raw) {
                if (item == null || item.getStatus() == null) {
                    continue;
                }
                String key = item.getStatus().trim().toUpperCase(Locale.ROOT);
                int count = Math.max(0, item.getApplicationCount());
                int unique = Math.max(0, item.getUniqueApplicants());
                fallbackTotal += count;
                byStatus.put(key, new RecruiterStatusDistribution(key, count, unique));
            }
        }

        int denom = totalApplications > 0 ? totalApplications : fallbackTotal;
        List<RecruiterStatusDistribution> result = new ArrayList<>(byStatus.values());
        for (RecruiterStatusDistribution item : result) {
            item.setPercentage(safePct(item.getApplicationCount(), denom));
        }
        return result;
    }

    /** Zero-fills all canonical {@link Job.JobType} categories in deterministic order. */
    public List<RecruiterJobTypeStat> zeroFillJobTypeStats(List<RecruiterJobTypeStat> raw) {
        Map<String, RecruiterJobTypeStat> byType = new LinkedHashMap<>();
        for (Job.JobType jt : Job.JobType.values()) {
            byType.put(jt.name(), new RecruiterJobTypeStat(jt.name(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        }

        if (raw != null) {
            for (RecruiterJobTypeStat item : raw) {
                if (item == null || item.getJobType() == null) {
                    continue;
                }
                String key = item.getJobType().trim().toUpperCase(Locale.ROOT);
                byType.put(key, new RecruiterJobTypeStat(
                        key,
                        Math.max(0, item.getTotalJobs()),
                        Math.max(0, item.getActiveStatusJobs()),
                        Math.max(0, item.getOpenAcceptingJobs()),
                        Math.max(0, item.getExpiredActiveJobs()),
                        Math.max(0, item.getClosedJobs()),
                        Math.max(0, item.getDraftJobs()),
                        Math.max(0, item.getPeriodApplications()),
                        Math.max(0, item.getShortlistedApplications()),
                        Math.max(0, item.getSelectedApplications()),
                        Math.max(0, item.getRejectedApplications())
                ));
            }
        }
        return new ArrayList<>(byType.values());
    }

    /** Computes finite pipeline percentages guarded against divide-by-zero / NaN / Infinity. */
    public void enrichPipelineKPI(RecruiterPipelineKPI kpi) {
        if (kpi == null) {
            return;
        }
        int total = Math.max(0, kpi.getTotalApplications());
        int pending = Math.max(0, kpi.getPendingApplications());
        int shortlisted = Math.max(0, kpi.getShortlistedApplications());
        int selected = Math.max(0, kpi.getSelectedApplications());
        int rejected = Math.max(0, kpi.getRejectedApplications());
        int reviewed = shortlisted + selected + rejected;
        int unique = Math.max(0, kpi.getUniqueApplicants());
        int uniqueSelected = Math.max(0, kpi.getUniqueSelectedApplicants());

        kpi.setTotalApplications(total);
        kpi.setPendingApplications(pending);
        kpi.setShortlistedApplications(shortlisted);
        kpi.setSelectedApplications(selected);
        kpi.setRejectedApplications(rejected);
        kpi.setReviewedApplications(reviewed);

        kpi.setReviewCompletionRate(safePct(reviewed, total));
        kpi.setShortlistRate(safePct(shortlisted, total));
        kpi.setSelectionRate(safePct(selected, total));
        kpi.setRejectionRate(safePct(rejected, total));
        kpi.setAdvancementRate(safePct(shortlisted + selected, total));
        kpi.setUniqueHireRate(safePct(uniqueSelected, unique));
    }

    /** Computes finite per-job conversion rates and sanitizes nullable fields. */
    public void enrichJobPerformance(List<RecruiterJobPerformance> jobs) {
        if (jobs == null) {
            return;
        }
        for (RecruiterJobPerformance job : jobs) {
            if (job == null) {
                continue;
            }
            int periodApps = Math.max(0, job.getPeriodApplications());
            job.setPeriodApplications(periodApps);
            job.setShortlistRate(safePct(Math.max(0, job.getShortlistedCount()), periodApps));
            job.setSelectionRate(safePct(Math.max(0, job.getSelectedCount()), periodApps));
        }
    }

    /** Sanitizes readiness flags, rates, and nullable average AI score. */
    public void enrichApplicantReadiness(RecruiterApplicantReadiness r) {
        if (r == null) {
            return;
        }
        int totalApps = Math.max(0, r.getTotalApplicationsInPeriod());
        int unique = Math.max(0, r.getUniqueApplicants());
        int completeProfile = Math.max(0, r.getApplicantsWithCompleteProfile());
        int activeResume = Math.max(0, r.getApplicantsWithActiveResume());
        int anyResume = Math.max(0, r.getApplicantsWithAnyResume());
        int appsWithAi = Math.max(0, r.getApplicationsWithJobAiAnalysis());

        r.setTotalApplicationsInPeriod(totalApps);
        r.setUniqueApplicants(unique);
        r.setApplicantsWithCompleteProfile(completeProfile);
        r.setApplicantsWithActiveResume(activeResume);
        r.setApplicantsWithAnyResume(anyResume);
        r.setApplicationsWithJobAiAnalysis(appsWithAi);
        r.setUniqueApplicantsWithJobAiScore(Math.max(0, r.getUniqueApplicantsWithJobAiScore()));
        r.setAdvisoryDisclaimer(RecruiterApplicantReadiness.DEFAULT_ADVISORY_DISCLAIMER);

        boolean hasApplicants = unique > 0;
        r.setReadinessDataAvailable(hasApplicants);

        Double avg = r.getAvgJobAiScore();
        if (appsWithAi <= 0 || avg == null || !Double.isFinite(avg) || avg < 0.0) {
            r.setAvgJobAiScore(null);
            r.setAiAnalysisAvailable(false);
        } else {
            r.setAvgJobAiScore(Math.round(avg * 10.0) / 10.0);
            r.setAiAnalysisAvailable(true);
        }

        r.setProfileCompletionRate(safePct(completeProfile, unique));
        r.setActiveResumeCoverageRate(safePct(activeResume, unique));
        r.setAnyResumeCoverageRate(safePct(anyResume, unique));
        r.setJobAiCoverageRate(safePct(appsWithAi, totalApps));

        if (!hasApplicants) {
            r.setUnavailabilityReason("No applications received in the selected period.");
        } else if (!r.isAiAnalysisAvailable()) {
            r.setUnavailabilityReason("No job-specific AI resume analyses recorded for these applications yet.");
        } else {
            r.setUnavailabilityReason(null);
        }
    }

    /** Safe finite percentage helper returning {@code 0.0} on zero denominator. */
    public static double safePct(int numerator, int denominator) {
        if (denominator <= 0 || numerator <= 0) {
            return 0.0;
        }
        double pct = (numerator * 100.0) / denominator;
        return Double.isFinite(pct) ? Math.round(pct * 10.0) / 10.0 : 0.0;
    }

    /** Builds a human-readable date range label matching Admin analytics conventions. */
    public static String buildRangeLabel(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return "All Time";
        }
        if (from != null && to != null) {
            long days = ChronoUnit.DAYS.between(from, to) + 1L;
            if (to.equals(LocalDate.now()) && (days == 7 || days == 30 || days == 90)) {
                return "Last " + days + " Days";
            }
            return from.format(LABEL_DISPLAY_FMT) + " - " + to.format(LABEL_DISPLAY_FMT);
        }
        if (from != null) {
            return "Since " + from.format(LABEL_DISPLAY_FMT);
        }
        return "Up to " + to.format(LABEL_DISPLAY_FMT);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private RecruiterOverviewKPI enrichOverviewKPI(RecruiterOverviewKPI kpi) {
        if (kpi == null) {
            return new RecruiterOverviewKPI();
        }
        if (!Double.isFinite(kpi.getProfileCompletenessRate()) || kpi.getProfileCompletenessRate() < 0.0) {
            kpi.setProfileCompletenessRate(0.0);
        }
        return kpi;
    }

    private void applyFallbackDefaults(RecruiterDashboardSnapshot snapshot,
                                       LocalDate from,
                                       LocalDate to,
                                       TrendGranularity granularity) {
        snapshot.setOverviewKPI(new RecruiterOverviewKPI());
        RecruiterPipelineKPI emptyPipeline = new RecruiterPipelineKPI();
        enrichPipelineKPI(emptyPipeline);
        snapshot.setPipelineKPI(emptyPipeline);
        snapshot.setStatusDistribution(zeroFillStatusDistribution(Collections.emptyList(), 0));
        snapshot.setJobPerformance(Collections.emptyList());
        snapshot.setJobTypeStats(zeroFillJobTypeStats(Collections.emptyList()));
        snapshot.setApplicationTrend(fillMissingTrendBuckets(Collections.emptyList(), from, to, granularity));
        snapshot.setRecentActivity(Collections.emptyList());
        snapshot.setPendingApplicants(Collections.emptyList());
        RecruiterApplicantReadiness emptyReadiness = new RecruiterApplicantReadiness();
        enrichApplicantReadiness(emptyReadiness);
        snapshot.setApplicantReadiness(emptyReadiness);
    }

    private static LocalDate alignToBucketStart(LocalDate date, TrendGranularity granularity) {
        return switch (granularity) {
            case DAILY   -> date;
            case WEEKLY  -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate alignToBucketEnd(LocalDate bucketStart, TrendGranularity granularity) {
        return switch (granularity) {
            case DAILY   -> bucketStart;
            case WEEKLY  -> bucketStart.plusDays(6);
            case MONTHLY -> bucketStart.withDayOfMonth(bucketStart.lengthOfMonth());
        };
    }

    private static LocalDate nextBucketStart(LocalDate bucketStart, TrendGranularity granularity) {
        return switch (granularity) {
            case DAILY   -> bucketStart.plusDays(1);
            case WEEKLY  -> bucketStart.plusWeeks(1);
            case MONTHLY -> bucketStart.plusMonths(1);
        };
    }

    private static String formatTrendLabel(LocalDate bucketStart, TrendGranularity granularity) {
        return switch (granularity) {
            case DAILY   -> bucketStart.format(TREND_DAY_FMT);
            case WEEKLY  -> bucketStart.format(TREND_DAY_FMT);
            case MONTHLY -> bucketStart.format(TREND_MONTH_FMT);
        };
    }

    private static RecruiterTrendPoint normalizeTrendPoint(RecruiterTrendPoint pt,
                                                             LocalDate bucketStart,
                                                             TrendGranularity granularity) {
        return new RecruiterTrendPoint(
                granularity,
                bucketStart,
                alignToBucketEnd(bucketStart, granularity),
                formatTrendLabel(bucketStart, granularity),
                Math.max(0, pt.getApplicationsCount()),
                Math.max(0, pt.getShortlistedCount()),
                Math.max(0, pt.getSelectedCount()),
                Math.max(0, pt.getRejectedCount())
        );
    }
}

