package com.hirehub.model;

/**
 * Per-status row in the application status distribution breakdown.
 *
 * Date filter applies to applications.applied_at.
 * One instance per Application.Status value (PENDING, SHORTLISTED, SELECTED, REJECTED).
 * Missing statuses are zero-filled by the service layer so all 4 are always present.
 */
public class AdminStatusDistribution {

    private String status;           // matches Application.Status name
    private int applicationCount;    // total rows with this status
    private int uniqueStudents;      // COUNT(DISTINCT student_id) with this status
    private double percentage;       // applicationCount / totalApplications * 100 (set by service)

    public AdminStatusDistribution() {}

    public AdminStatusDistribution(String status, int applicationCount, int uniqueStudents) {
        this.status = status;
        this.applicationCount = applicationCount;
        this.uniqueStudents = uniqueStudents;
    }

    // --- Getters ---

    public String getStatus() { return status; }
    public int getApplicationCount() { return applicationCount; }
    public int getUniqueStudents() { return uniqueStudents; }
    public double getPercentage() { return percentage; }

    // --- Setters ---

    public void setStatus(String status) { this.status = status; }
    public void setApplicationCount(int applicationCount) { this.applicationCount = applicationCount; }
    public void setUniqueStudents(int uniqueStudents) { this.uniqueStudents = uniqueStudents; }
    public void setPercentage(double percentage) { this.percentage = percentage; }

    @Override
    public String toString() {
        return "AdminStatusDistribution{status='" + status + "', count=" + applicationCount
                + ", uniqueStudents=" + uniqueStudents + ", pct=" + String.format("%.1f", percentage) + "%}";
    }
}

