package com.hirehub.model;

import java.time.LocalDate;

/**
 * A single data point for the application-activity trend chart.
 *
 * Date filter applies to applications.applied_at (grouped by date).
 * One instance per calendar day that had at least one application.
 * Days with zero activity are NOT zero-filled by the DAO — the service or
 * UI layer is responsible for filling gaps if a continuous axis is needed.
 */
public class AdminTrendPoint {

    private LocalDate activityDate;
    private int applicationsCount;   // total applications on this date
    private int shortlistedCount;    // applications moved to SHORTLISTED on this date (by apply date)
    private int selectedCount;       // applications moved to SELECTED on this date (by apply date)

    public AdminTrendPoint() {}

    public AdminTrendPoint(LocalDate activityDate, int applicationsCount,
                           int shortlistedCount, int selectedCount) {
        this.activityDate = activityDate;
        this.applicationsCount = applicationsCount;
        this.shortlistedCount = shortlistedCount;
        this.selectedCount = selectedCount;
    }

    // --- Getters ---

    public LocalDate getActivityDate() { return activityDate; }
    public int getApplicationsCount() { return applicationsCount; }
    public int getShortlistedCount() { return shortlistedCount; }
    public int getSelectedCount() { return selectedCount; }

    // --- Setters ---

    public void setActivityDate(LocalDate activityDate) { this.activityDate = activityDate; }
    public void setApplicationsCount(int applicationsCount) { this.applicationsCount = applicationsCount; }
    public void setShortlistedCount(int shortlistedCount) { this.shortlistedCount = shortlistedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }

    @Override
    public String toString() {
        return "AdminTrendPoint{date=" + activityDate + ", apps=" + applicationsCount
                + ", shortlisted=" + shortlistedCount + ", selected=" + selectedCount + "}";
    }
}

