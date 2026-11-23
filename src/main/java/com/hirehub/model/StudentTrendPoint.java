package com.hirehub.model;

import java.time.LocalDate;

/**
 * StudentTrendPoint — Single time-series data point for student application volume and progress over time.
 */
public class StudentTrendPoint {

    private LocalDate date;
    private int applicationCount;
    private int shortlistedCount;
    private int selectedCount;

    public StudentTrendPoint() {}

    public StudentTrendPoint(LocalDate date, int applicationCount, int shortlistedCount, int selectedCount) {
        this.date = date;
        this.applicationCount = applicationCount;
        this.shortlistedCount = shortlistedCount;
        this.selectedCount = selectedCount;
    }

    // Getters and Setters

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public int getApplicationCount() { return applicationCount; }
    public void setApplicationCount(int applicationCount) { this.applicationCount = applicationCount; }

    public int getShortlistedCount() { return shortlistedCount; }
    public void setShortlistedCount(int shortlistedCount) { this.shortlistedCount = shortlistedCount; }

    public int getSelectedCount() { return selectedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }
}

