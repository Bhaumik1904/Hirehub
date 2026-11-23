package com.hirehub.model;

/**
 * KPI summary for job applications in the Admin Analytics Dashboard.
 *
 * Date filter applies to applications.applied_at (applications submitted in the period).
 *
 * Conversion rates are computed by the service layer:
 *   selectionRate      = selectedApplications / totalApplications * 100
 *   shortlistRate      = shortlistedApplications / totalApplications * 100
 *   uniqueHireRate     = uniqueSelectedStudents / uniqueApplyingStudents * 100
 */
public class AdminApplicationKPI {

    // --- Raw counts ---
    private int totalApplications;
    private int pendingApplications;
    private int shortlistedApplications;
    private int selectedApplications;
    private int rejectedApplications;
    private int uniqueApplyingStudents;    // COUNT(DISTINCT student_id) across all statuses
    private int uniqueShortlistedStudents; // COUNT(DISTINCT student_id WHERE status='SHORTLISTED')
    private int uniqueSelectedStudents;    // COUNT(DISTINCT student_id WHERE status='SELECTED')

    // --- Computed rates (0.0 when denominator is zero) ---
    private double selectionRate;     // selectedApplications / totalApplications * 100
    private double shortlistRate;     // shortlistedApplications / totalApplications * 100
    private double uniqueHireRate;    // uniqueSelectedStudents / uniqueApplyingStudents * 100

    public AdminApplicationKPI() {}

    // --- Getters ---

    public int getTotalApplications() { return totalApplications; }
    public int getPendingApplications() { return pendingApplications; }
    public int getShortlistedApplications() { return shortlistedApplications; }
    public int getSelectedApplications() { return selectedApplications; }
    public int getRejectedApplications() { return rejectedApplications; }
    public int getUniqueApplyingStudents() { return uniqueApplyingStudents; }
    public int getUniqueShortlistedStudents() { return uniqueShortlistedStudents; }
    public int getUniqueSelectedStudents() { return uniqueSelectedStudents; }
    public double getSelectionRate() { return selectionRate; }
    public double getShortlistRate() { return shortlistRate; }
    public double getUniqueHireRate() { return uniqueHireRate; }

    // --- Setters ---

    public void setTotalApplications(int totalApplications) { this.totalApplications = totalApplications; }
    public void setPendingApplications(int pendingApplications) { this.pendingApplications = pendingApplications; }
    public void setShortlistedApplications(int shortlistedApplications) { this.shortlistedApplications = shortlistedApplications; }
    public void setSelectedApplications(int selectedApplications) { this.selectedApplications = selectedApplications; }
    public void setRejectedApplications(int rejectedApplications) { this.rejectedApplications = rejectedApplications; }
    public void setUniqueApplyingStudents(int uniqueApplyingStudents) { this.uniqueApplyingStudents = uniqueApplyingStudents; }
    public void setUniqueShortlistedStudents(int uniqueShortlistedStudents) { this.uniqueShortlistedStudents = uniqueShortlistedStudents; }
    public void setUniqueSelectedStudents(int uniqueSelectedStudents) { this.uniqueSelectedStudents = uniqueSelectedStudents; }
    public void setSelectionRate(double selectionRate) { this.selectionRate = selectionRate; }
    public void setShortlistRate(double shortlistRate) { this.shortlistRate = shortlistRate; }
    public void setUniqueHireRate(double uniqueHireRate) { this.uniqueHireRate = uniqueHireRate; }

    @Override
    public String toString() {
        return "AdminApplicationKPI{total=" + totalApplications
                + ", pending=" + pendingApplications + ", shortlisted=" + shortlistedApplications
                + ", selected=" + selectedApplications + ", rejected=" + rejectedApplications
                + ", uniqueApplied=" + uniqueApplyingStudents
                + ", uniqueSelected=" + uniqueSelectedStudents + "}";
    }
}

