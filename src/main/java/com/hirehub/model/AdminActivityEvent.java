package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * A single platform activity event for the recent-activity feed.
 *
 * Date filter applies to occurred_at (the actual event timestamp).
 * Limited to N most-recent events by the DAO.
 *
 * eventType values (non-exhaustive): "USER_REGISTERED", "JOB_POSTED",
 *   "APPLICATION_SUBMITTED", "STATUS_CHANGED", "RESUME_UPLOADED", "RESUME_ANALYZED"
 */
public class AdminActivityEvent {

    private String eventType;      // human-readable category
    private String actorName;      // display name of the user who caused the event
    private String summary;        // one-line description of the event
    private LocalDateTime occurredAt;

    public AdminActivityEvent() {}

    public AdminActivityEvent(String eventType, String actorName,
                              String summary, LocalDateTime occurredAt) {
        this.eventType = eventType;
        this.actorName = actorName;
        this.summary = summary;
        this.occurredAt = occurredAt;
    }

    // --- Getters ---

    public String getEventType() { return eventType; }
    public String getActorName() { return actorName; }
    public String getSummary() { return summary; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    // --- Setters ---

    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    @Override
    public String toString() {
        return "AdminActivityEvent{type='" + eventType + "', actor='" + actorName
                + "', at=" + occurredAt + ", summary='" + summary + "'}";
    }
}

