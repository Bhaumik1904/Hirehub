package com.hirehub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Summary row for a top-N job listing ranked by total application count.
 *
 * Date filter on jobs.created_at restricts which jobs are considered.
 * Application sub-counts are computed across all applications for the job
 * (not filtered by apply date), consistent with the recruiter engagement Q8 semantics.
 */
public class AdminTopJob {

    private int jobId;
    private String title;
    private String companyName;
    private String jobType;             // Job.JobType name
    private String status;             // Job.Status name
    private LocalDate deadline;        // nullable
    private LocalDateTime createdAt;

    // Application breakdown for this job
    private int totalApplications;
    private int pendingCount;
    private int shortlistedCount;
    private int selectedCount;
    private int rejectedCount;

    public AdminTopJob() {}

    // --- Getters ---

    public int getJobId() { return jobId; }
    public String getTitle() { return title; }
    public String getCompanyName() { return companyName; }
    public String getJobType() { return jobType; }
    public String getStatus() { return status; }
    public LocalDate getDeadline() { return deadline; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getTotalApplications() { return totalApplications; }
    public int getPendingCount() { return pendingCount; }
    public int getShortlistedCount() { return shortlistedCount; }
    public int getSelectedCount() { return selectedCount; }
    public int getRejectedCount() { return rejectedCount; }

    // --- Setters ---

    public void setJobId(int jobId) { this.jobId = jobId; }
    public void setTitle(String title) { this.title = title; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public void setStatus(String status) { this.status = status; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setTotalApplications(int totalApplications) { this.totalApplications = totalApplications; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    public void setShortlistedCount(int shortlistedCount) { this.shortlistedCount = shortlistedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    @Override
    public String toString() {
        return "AdminTopJob{id=" + jobId + ", title='" + title + "', company='" + companyName
                + "', apps=" + totalApplications + ", status='" + status + "'}";
    }
}

