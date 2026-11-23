package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * Single recruiter-scoped hiring activity event derived strictly from
 * persisted database timestamps ({@code jobs.created_at}, {@code applications.applied_at},
 * and {@code applications.updated_at > applications.applied_at}).
 *
 * <p>Event types emitted: {@code JOB_POSTED}, {@code APPLICATION_RECEIVED},
 * {@code APPLICATION_REVIEWED}.</p>
 */
public class RecruiterActivityEvent {

    private String eventType;
    private int jobId;
    private String subjectName;         // Applicant display name or company/job actor label
    private String jobTitle;
    private String summary;
    private LocalDateTime occurredAt;

    public RecruiterActivityEvent() {}

    public RecruiterActivityEvent(String eventType, int jobId, String subjectName,
                                  String jobTitle, String summary, LocalDateTime occurredAt) {
        this.eventType = eventType;
        this.jobId = jobId;
        this.subjectName = subjectName;
        this.jobTitle = jobTitle;
        this.summary = summary;
        this.occurredAt = occurredAt;
    }

    // --- Getters ---

    public String getEventType() { return eventType; }
    public int getJobId() { return jobId; }
    public String getSubjectName() { return subjectName; }
    public String getJobTitle() { return jobTitle; }
    public String getSummary() { return summary; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    // --- Setters ---

    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setJobId(int jobId) { this.jobId = jobId; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    @Override
    public String toString() {
        return "RecruiterActivityEvent{type='" + eventType + "', jobId=" + jobId
                + ", at=" + occurredAt + ", summary='" + summary + "'}";
    }
}

