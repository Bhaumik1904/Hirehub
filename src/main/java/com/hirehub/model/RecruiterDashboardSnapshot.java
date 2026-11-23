package com.hirehub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level container for a single recruiter's analytics snapshot.
 *
 * <p>Produced by {@link com.hirehub.service.RecruiterAnalyticsService} and consumed by
 * the Recruiter Intelligence Dashboard controller without exposing JDBC or SQL details.</p>
 */
public class RecruiterDashboardSnapshot {

    private int recruiterId;
    private RecruiterOverviewKPI overviewKPI = new RecruiterOverviewKPI();
    private RecruiterPipelineKPI pipelineKPI = new RecruiterPipelineKPI();
    private List<RecruiterStatusDistribution> statusDistribution = new ArrayList<>();
    private List<RecruiterJobPerformance> jobPerformance = new ArrayList<>();
    private List<RecruiterJobTypeStat> jobTypeStats = new ArrayList<>();
    private List<RecruiterTrendPoint> applicationTrend = new ArrayList<>();
    private List<RecruiterActivityEvent> recentActivity = new ArrayList<>();
    private List<RecruiterPendingApplicant> pendingApplicants = new ArrayList<>();
    private RecruiterApplicantReadiness applicantReadiness = new RecruiterApplicantReadiness();

    private LocalDate filterFromDate; // nullable (All Time / open lower bound)
    private LocalDate filterToDate;   // nullable (All Time / open upper bound)
    private RecruiterTrendPoint.TrendGranularity trendGranularity;
    private LocalDateTime refreshedAt;
    private String dateRangeLabel;
    private String loadError;         // null on success

    public RecruiterDashboardSnapshot() {}

    // --- Getters ---

    public int getRecruiterId() { return recruiterId; }
    public RecruiterOverviewKPI getOverviewKPI() { return overviewKPI; }
    public RecruiterPipelineKPI getPipelineKPI() { return pipelineKPI; }
    public List<RecruiterStatusDistribution> getStatusDistribution() { return statusDistribution; }
    public List<RecruiterJobPerformance> getJobPerformance() { return jobPerformance; }
    public List<RecruiterJobTypeStat> getJobTypeStats() { return jobTypeStats; }
    public List<RecruiterTrendPoint> getApplicationTrend() { return applicationTrend; }
    public List<RecruiterActivityEvent> getRecentActivity() { return recentActivity; }
    public List<RecruiterPendingApplicant> getPendingApplicants() { return pendingApplicants; }
    public RecruiterApplicantReadiness getApplicantReadiness() { return applicantReadiness; }

    public LocalDate getFilterFromDate() { return filterFromDate; }
    public LocalDate getFilterToDate() { return filterToDate; }
    public RecruiterTrendPoint.TrendGranularity getTrendGranularity() { return trendGranularity; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }
    public String getDateRangeLabel() { return dateRangeLabel; }
    public String getLoadError() { return loadError; }
    public boolean hasError() { return loadError != null && !loadError.isBlank(); }

    // --- Setters ---

    public void setRecruiterId(int recruiterId) { this.recruiterId = recruiterId; }
    public void setOverviewKPI(RecruiterOverviewKPI overviewKPI) {
        this.overviewKPI = overviewKPI != null ? overviewKPI : new RecruiterOverviewKPI();
    }
    public void setPipelineKPI(RecruiterPipelineKPI pipelineKPI) {
        this.pipelineKPI = pipelineKPI != null ? pipelineKPI : new RecruiterPipelineKPI();
    }
    public void setStatusDistribution(List<RecruiterStatusDistribution> statusDistribution) {
        this.statusDistribution = statusDistribution != null ? statusDistribution : new ArrayList<>();
    }
    public void setJobPerformance(List<RecruiterJobPerformance> jobPerformance) {
        this.jobPerformance = jobPerformance != null ? jobPerformance : new ArrayList<>();
    }
    public void setJobTypeStats(List<RecruiterJobTypeStat> jobTypeStats) {
        this.jobTypeStats = jobTypeStats != null ? jobTypeStats : new ArrayList<>();
    }
    public void setApplicationTrend(List<RecruiterTrendPoint> applicationTrend) {
        this.applicationTrend = applicationTrend != null ? applicationTrend : new ArrayList<>();
    }
    public void setRecentActivity(List<RecruiterActivityEvent> recentActivity) {
        this.recentActivity = recentActivity != null ? recentActivity : new ArrayList<>();
    }
    public void setPendingApplicants(List<RecruiterPendingApplicant> pendingApplicants) {
        this.pendingApplicants = pendingApplicants != null ? pendingApplicants : new ArrayList<>();
    }
    public void setApplicantReadiness(RecruiterApplicantReadiness applicantReadiness) {
        this.applicantReadiness = applicantReadiness != null ? applicantReadiness : new RecruiterApplicantReadiness();
    }

    public void setFilterFromDate(LocalDate filterFromDate) { this.filterFromDate = filterFromDate; }
    public void setFilterToDate(LocalDate filterToDate) { this.filterToDate = filterToDate; }
    public void setTrendGranularity(RecruiterTrendPoint.TrendGranularity trendGranularity) { this.trendGranularity = trendGranularity; }
    public void setRefreshedAt(LocalDateTime refreshedAt) { this.refreshedAt = refreshedAt; }
    public void setDateRangeLabel(String dateRangeLabel) { this.dateRangeLabel = dateRangeLabel; }
    public void setLoadError(String loadError) { this.loadError = loadError; }

    @Override
    public String toString() {
        return "RecruiterDashboardSnapshot{recruiterId=" + recruiterId
                + ", range='" + dateRangeLabel + "', granularity=" + trendGranularity
                + ", refreshedAt=" + refreshedAt + ", hasError=" + hasError() + "}";
    }
}

