package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * Lightweight triage record for a recent {@code PENDING} application on one of the
 * recruiter's jobs. Avoids exposing unnecessary private contact details or file paths.
 */
public class RecruiterPendingApplicant {

    private int applicationId;
    private int jobId;
    private String jobTitle;
    private String jobType;
    private String applicantName;
    private String college;             // nullable academic context
    private String degree;              // nullable academic context
    private LocalDateTime appliedAt;
    private long daysPending;           // non-negative whole days since appliedAt

    public RecruiterPendingApplicant() {}

    public RecruiterPendingApplicant(int applicationId, int jobId, String jobTitle,
                                     String jobType, String applicantName,
                                     String college, String degree,
                                     LocalDateTime appliedAt, long daysPending) {
        this.applicationId = applicationId;
        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.jobType = jobType;
        this.applicantName = applicantName;
        this.college = college;
        this.degree = degree;
        this.appliedAt = appliedAt;
        this.daysPending = Math.max(0L, daysPending);
    }

    // --- Getters ---

    public int getApplicationId() { return applicationId; }
    public int getJobId() { return jobId; }
    public String getJobTitle() { return jobTitle; }
    public String getJobType() { return jobType; }
    public String getApplicantName() { return applicantName; }
    public String getCollege() { return college; }
    public String getDegree() { return degree; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public long getDaysPending() { return daysPending; }

    // --- Setters ---

    public void setApplicationId(int applicationId) { this.applicationId = applicationId; }
    public void setJobId(int jobId) { this.jobId = jobId; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public void setCollege(String college) { this.college = college; }
    public void setDegree(String degree) { this.degree = degree; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public void setDaysPending(long daysPending) { this.daysPending = Math.max(0L, daysPending); }

    @Override
    public String toString() {
        return "RecruiterPendingApplicant{appId=" + applicationId + ", jobId=" + jobId
                + ", job='" + jobTitle + "', applicant='" + applicantName
                + "', appliedAt=" + appliedAt + ", daysPending=" + daysPending + "}";
    }
}

