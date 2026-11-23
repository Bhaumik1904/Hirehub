package com.hirehub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * StudentApplicationRow — Sanitized view model for a student's job application.
 *
 * <p>Exposes only safe display metadata; physical file paths, sensitive recruiter email addresses,
 * and internal applicant IDs are omitted.</p>
 */
public class StudentApplicationRow {

    private int applicationId;
    private int jobId;
    private String jobTitle;
    private String companyName;
    private String jobType;
    private String location;
    private Application.Status status;
    private LocalDateTime appliedAt;
    private LocalDateTime latestUpdateAt;
    private String jobStatus;
    private LocalDate jobDeadline;
    private boolean deadlineExpired;

    public StudentApplicationRow() {}

    public StudentApplicationRow(int applicationId, int jobId, String jobTitle,
                                 String companyName, String jobType, String location,
                                 Application.Status status, LocalDateTime appliedAt,
                                 LocalDateTime latestUpdateAt, String jobStatus,
                                 LocalDate jobDeadline, boolean deadlineExpired) {
        this.applicationId = applicationId;
        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.companyName = companyName;
        this.jobType = jobType;
        this.location = location;
        this.status = status;
        this.appliedAt = appliedAt;
        this.latestUpdateAt = latestUpdateAt;
        this.jobStatus = jobStatus;
        this.jobDeadline = jobDeadline;
        this.deadlineExpired = deadlineExpired;
    }

    // Getters and Setters

    public int getApplicationId() { return applicationId; }
    public void setApplicationId(int applicationId) { this.applicationId = applicationId; }

    public int getJobId() { return jobId; }
    public void setJobId(int jobId) { this.jobId = jobId; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Application.Status getStatus() { return status; }
    public void setStatus(Application.Status status) { this.status = status; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }

    public LocalDateTime getLatestUpdateAt() { return latestUpdateAt; }
    public void setLatestUpdateAt(LocalDateTime latestUpdateAt) { this.latestUpdateAt = latestUpdateAt; }

    public String getJobStatus() { return jobStatus; }
    public void setJobStatus(String jobStatus) { this.jobStatus = jobStatus; }

    public LocalDate getJobDeadline() { return jobDeadline; }
    public void setJobDeadline(LocalDate jobDeadline) { this.jobDeadline = jobDeadline; }

    public boolean isDeadlineExpired() { return deadlineExpired; }
    public void setDeadlineExpired(boolean deadlineExpired) { this.deadlineExpired = deadlineExpired; }
}

