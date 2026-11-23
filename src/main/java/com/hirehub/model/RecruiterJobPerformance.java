package com.hirehub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-job application funnel performance row for a recruiter's job portfolio.
 *
 * <p>Jobs owned by the recruiter with zero applications are preserved cleanly.
 * Both lifetime totals and period-scoped {@code [from, to)} application metrics
 * are tracked explicitly so date filters on incoming applications never hide
 * active portfolio postings.</p>
 */
public class RecruiterJobPerformance {

    private int jobId;
    private String title;
    private String companyName;
    private String jobType;             // Job.JobType name
    private String location;
    private LocalDate deadline;         // nullable
    private String status;              // Job.Status name
    private boolean expiredActive;      // true when status == ACTIVE and deadline < today
    private LocalDateTime createdAt;

    // Lifetime totals for this posting
    private int lifetimeApplications;
    private int lifetimePendingCount;
    private LocalDateTime lastAppliedAt; // nullable

    // Period-scoped [from, to) metrics (equals lifetime when All Time is selected)
    private int periodApplications;
    private int pendingCount;
    private int shortlistedCount;
    private int selectedCount;
    private int rejectedCount;

    // Computed conversion rates over periodApplications (0.0 when 0 applications)
    private double shortlistRate;
    private double selectionRate;

    public RecruiterJobPerformance() {}

    // --- Getters ---

    public int getJobId() { return jobId; }
    public String getTitle() { return title; }
    public String getCompanyName() { return companyName; }
    public String getJobType() { return jobType; }
    public String getLocation() { return location; }
    public LocalDate getDeadline() { return deadline; }
    public String getStatus() { return status; }
    public boolean isExpiredActive() { return expiredActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public int getLifetimeApplications() { return lifetimeApplications; }
    public int getLifetimePendingCount() { return lifetimePendingCount; }
    public LocalDateTime getLastAppliedAt() { return lastAppliedAt; }

    public int getPeriodApplications() { return periodApplications; }
    public int getPendingCount() { return pendingCount; }
    public int getShortlistedCount() { return shortlistedCount; }
    public int getSelectedCount() { return selectedCount; }
    public int getRejectedCount() { return rejectedCount; }

    public double getShortlistRate() { return shortlistRate; }
    public double getSelectionRate() { return selectionRate; }

    // --- Setters ---

    public void setJobId(int jobId) { this.jobId = jobId; }
    public void setTitle(String title) { this.title = title; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public void setLocation(String location) { this.location = location; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public void setStatus(String status) { this.status = status; }
    public void setExpiredActive(boolean expiredActive) { this.expiredActive = expiredActive; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public void setLifetimeApplications(int lifetimeApplications) { this.lifetimeApplications = lifetimeApplications; }
    public void setLifetimePendingCount(int lifetimePendingCount) { this.lifetimePendingCount = lifetimePendingCount; }
    public void setLastAppliedAt(LocalDateTime lastAppliedAt) { this.lastAppliedAt = lastAppliedAt; }

    public void setPeriodApplications(int periodApplications) { this.periodApplications = periodApplications; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    public void setShortlistedCount(int shortlistedCount) { this.shortlistedCount = shortlistedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    public void setShortlistRate(double shortlistRate) { this.shortlistRate = shortlistRate; }
    public void setSelectionRate(double selectionRate) { this.selectionRate = selectionRate; }

    @Override
    public String toString() {
        return "RecruiterJobPerformance{jobId=" + jobId + ", title='" + title
                + "', status='" + status + "', expiredActive=" + expiredActive
                + ", periodApps=" + periodApplications + ", lifetimeApps=" + lifetimeApplications + "}";
    }
}

