package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * StudentActivityEvent — Consolidated recent event in the student's career journey.
 */
public class StudentActivityEvent {

    public enum EventType {
        APPLICATION_SUBMITTED,
        STATUS_CHANGED,
        RESUME_UPLOADED,
        RESUME_ANALYZED
    }

    private EventType type;
    private LocalDateTime timestamp;
    private String title;
    private String description;
    private String badgeText;
    private String badgeStyleClass;

    public StudentActivityEvent() {}

    public StudentActivityEvent(EventType type, LocalDateTime timestamp, String title,
                                String description, String badgeText, String badgeStyleClass) {
        this.type = type;
        this.timestamp = timestamp;
        this.title = title;
        this.description = description;
        this.badgeText = badgeText;
        this.badgeStyleClass = badgeStyleClass;
    }

    // Getters and Setters

    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBadgeText() { return badgeText; }
    public void setBadgeText(String badgeText) { this.badgeText = badgeText; }

    public String getBadgeStyleClass() { return badgeStyleClass; }
    public void setBadgeStyleClass(String badgeStyleClass) { this.badgeStyleClass = badgeStyleClass; }
}

