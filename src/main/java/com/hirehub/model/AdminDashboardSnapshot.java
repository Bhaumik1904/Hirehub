package com.hirehub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level container for the Admin Dashboard data snapshot.
 *
 * Loaded by AdminAnalyticsService and passed to the Admin Dashboard controller.
 * refreshedAt is set at load time (server-side clock, not DB clock).
 * dateRangeLabel is a human-readable string set by the service (e.g., "Last 30 days").
 *
 * loadError is null on success; non-null contains the error message when the
 * service could not fully load data (partial or empty snapshot).
 */
public class AdminDashboardSnapshot {

    private AdminOverviewKPI overviewKPI;
    private AdminJobKPI jobKPI;
    private AdminApplicationKPI applicationKPI;
    private List<AdminStatusDistribution> statusDistribution = new ArrayList<>();
    private List<AdminJobTypeStat> jobTypeStats = new ArrayList<>();
    private List<AdminTopJob> topJobs = new ArrayList<>();
    private AdminStudentParticipation studentParticipation;
    private List<AdminRecruiterEngagement> recruiterEngagement = new ArrayList<>();
    private List<AdminActivityEvent> recentActivity = new ArrayList<>();
    private List<AdminTrendPoint> applicationTrend = new ArrayList<>();

    private LocalDate filterFromDate; // nullable (All Time)
    private LocalDate filterToDate;   // nullable (All Time)
    private LocalDateTime refreshedAt;
    private String dateRangeLabel;
    private String loadError;  // null = success

    public AdminDashboardSnapshot() {}

    // --- Getters ---

    public AdminOverviewKPI getOverviewKPI() { return overviewKPI; }
    public AdminJobKPI getJobKPI() { return jobKPI; }
    public AdminApplicationKPI getApplicationKPI() { return applicationKPI; }
    public List<AdminStatusDistribution> getStatusDistribution() { return statusDistribution; }
    public List<AdminJobTypeStat> getJobTypeStats() { return jobTypeStats; }
    public List<AdminTopJob> getTopJobs() { return topJobs; }
    public AdminStudentParticipation getStudentParticipation() { return studentParticipation; }
    public List<AdminRecruiterEngagement> getRecruiterEngagement() { return recruiterEngagement; }
    public List<AdminActivityEvent> getRecentActivity() { return recentActivity; }
    public List<AdminTrendPoint> getApplicationTrend() { return applicationTrend; }
    public LocalDate getFilterFromDate() { return filterFromDate; }
    public LocalDate getFilterToDate() { return filterToDate; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }
    public String getDateRangeLabel() { return dateRangeLabel; }
    public String getLoadError() { return loadError; }
    public boolean hasError() { return loadError != null && !loadError.isBlank(); }

    // --- Setters ---

    public void setOverviewKPI(AdminOverviewKPI overviewKPI) { this.overviewKPI = overviewKPI; }
    public void setJobKPI(AdminJobKPI jobKPI) { this.jobKPI = jobKPI; }
    public void setApplicationKPI(AdminApplicationKPI applicationKPI) { this.applicationKPI = applicationKPI; }
    public void setStatusDistribution(List<AdminStatusDistribution> statusDistribution) {
        this.statusDistribution = statusDistribution != null ? statusDistribution : new ArrayList<>();
    }
    public void setJobTypeStats(List<AdminJobTypeStat> jobTypeStats) {
        this.jobTypeStats = jobTypeStats != null ? jobTypeStats : new ArrayList<>();
    }
    public void setTopJobs(List<AdminTopJob> topJobs) {
        this.topJobs = topJobs != null ? topJobs : new ArrayList<>();
    }
    public void setStudentParticipation(AdminStudentParticipation studentParticipation) { this.studentParticipation = studentParticipation; }
    public void setRecruiterEngagement(List<AdminRecruiterEngagement> recruiterEngagement) {
        this.recruiterEngagement = recruiterEngagement != null ? recruiterEngagement : new ArrayList<>();
    }
    public void setRecentActivity(List<AdminActivityEvent> recentActivity) {
        this.recentActivity = recentActivity != null ? recentActivity : new ArrayList<>();
    }
    public void setApplicationTrend(List<AdminTrendPoint> applicationTrend) {
        this.applicationTrend = applicationTrend != null ? applicationTrend : new ArrayList<>();
    }
    public void setFilterFromDate(LocalDate filterFromDate) { this.filterFromDate = filterFromDate; }
    public void setFilterToDate(LocalDate filterToDate) { this.filterToDate = filterToDate; }
    public void setRefreshedAt(LocalDateTime refreshedAt) { this.refreshedAt = refreshedAt; }
    public void setDateRangeLabel(String dateRangeLabel) { this.dateRangeLabel = dateRangeLabel; }
    public void setLoadError(String loadError) { this.loadError = loadError; }

    @Override
    public String toString() {
        return "AdminDashboardSnapshot{range='" + dateRangeLabel + "', refreshedAt=" + refreshedAt
                + ", hasError=" + hasError() + "}";
    }
}

