package com.hirehub.model;

import java.time.LocalDate;

/**
 * Single time-bucketed data point for the recruiter application trend series.
 *
 * <p>Service layer determines {@link TrendGranularity} ({@code DAILY}, {@code WEEKLY},
 * or {@code MONTHLY}) from the selected date range and zero-fills missing buckets
 * in strict ascending chronological order.</p>
 */
public class RecruiterTrendPoint {

    /** Bucket size selected dynamically from the requested date span. */
    public enum TrendGranularity {
        DAILY, WEEKLY, MONTHLY
    }

    private TrendGranularity granularity;
    private LocalDate periodStart;      // Day date, Monday date (WEEKLY), or 1st of month (MONTHLY)
    private LocalDate periodEnd;        // Inclusive bucket end date
    private String periodLabel;         // Formatted chart label assigned by service
    private int applicationsCount;
    private int shortlistedCount;
    private int selectedCount;
    private int rejectedCount;

    public RecruiterTrendPoint() {}

    public RecruiterTrendPoint(TrendGranularity granularity, LocalDate periodStart, LocalDate periodEnd,
                               String periodLabel, int applicationsCount, int shortlistedCount,
                               int selectedCount, int rejectedCount) {
        this.granularity = granularity;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.periodLabel = periodLabel;
        this.applicationsCount = applicationsCount;
        this.shortlistedCount = shortlistedCount;
        this.selectedCount = selectedCount;
        this.rejectedCount = rejectedCount;
    }

    // --- Getters ---

    public TrendGranularity getGranularity() { return granularity; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public String getPeriodLabel() { return periodLabel; }
    public int getApplicationsCount() { return applicationsCount; }
    public int getShortlistedCount() { return shortlistedCount; }
    public int getSelectedCount() { return selectedCount; }
    public int getRejectedCount() { return rejectedCount; }

    // --- Setters ---

    public void setGranularity(TrendGranularity granularity) { this.granularity = granularity; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public void setPeriodLabel(String periodLabel) { this.periodLabel = periodLabel; }
    public void setApplicationsCount(int applicationsCount) { this.applicationsCount = applicationsCount; }
    public void setShortlistedCount(int shortlistedCount) { this.shortlistedCount = shortlistedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    @Override
    public String toString() {
        return "RecruiterTrendPoint{granularity=" + granularity
                + ", start=" + periodStart + ", label='" + periodLabel + "'"
                + ", apps=" + applicationsCount + ", shortlisted=" + shortlistedCount
                + ", selected=" + selectedCount + ", rejected=" + rejectedCount + "}";
    }
}

