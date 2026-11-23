package com.hirehub.model;

/**
 * StudentStatusDistribution — Application status breakdown with counts and percentages.
 *
 * <p>Always provides zero-filled metrics when no applications exist, safely guarding against
 * division-by-zero.</p>
 */
public class StudentStatusDistribution {

    private int pendingCount;
    private int shortlistedCount;
    private int rejectedCount;
    private int selectedCount;

    private double pendingPct;
    private double shortlistedPct;
    private double rejectedPct;
    private double selectedPct;

    private int total;

    public StudentStatusDistribution() {}

    public StudentStatusDistribution(int pendingCount, int shortlistedCount,
                                     int rejectedCount, int selectedCount) {
        this.pendingCount = pendingCount;
        this.shortlistedCount = shortlistedCount;
        this.rejectedCount = rejectedCount;
        this.selectedCount = selectedCount;
        this.total = pendingCount + shortlistedCount + rejectedCount + selectedCount;

        if (this.total > 0) {
            this.pendingPct = Math.round((pendingCount * 100.0 / total) * 10.0) / 10.0;
            this.shortlistedPct = Math.round((shortlistedCount * 100.0 / total) * 10.0) / 10.0;
            this.rejectedPct = Math.round((rejectedCount * 100.0 / total) * 10.0) / 10.0;
            this.selectedPct = Math.round((selectedCount * 100.0 / total) * 10.0) / 10.0;
        } else {
            this.pendingPct = 0.0;
            this.shortlistedPct = 0.0;
            this.rejectedPct = 0.0;
            this.selectedPct = 0.0;
        }
    }

    // Getters and Setters

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getShortlistedCount() { return shortlistedCount; }
    public void setShortlistedCount(int shortlistedCount) { this.shortlistedCount = shortlistedCount; }

    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    public int getSelectedCount() { return selectedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }

    public double getPendingPct() { return pendingPct; }
    public void setPendingPct(double pendingPct) { this.pendingPct = pendingPct; }

    public double getShortlistedPct() { return shortlistedPct; }
    public void setShortlistedPct(double shortlistedPct) { this.shortlistedPct = shortlistedPct; }

    public double getRejectedPct() { return rejectedPct; }
    public void setRejectedPct(double rejectedPct) { this.rejectedPct = rejectedPct; }

    public double getSelectedPct() { return selectedPct; }
    public void setSelectedPct(double selectedPct) { this.selectedPct = selectedPct; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}

