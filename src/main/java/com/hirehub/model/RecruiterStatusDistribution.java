package com.hirehub.model;

/**
 * Per-status row in the recruiter's application status distribution.
 *
 * <p>All canonical {@link Application.Status} values ({@code PENDING}, {@code SHORTLISTED},
 * {@code SELECTED}, {@code REJECTED}) are guaranteed present via service-layer zero-filling.</p>
 */
public class RecruiterStatusDistribution {

    private String status;
    private int applicationCount;
    private int uniqueApplicants;
    private double percentage;       // finite in [0.0, 100.0]

    public RecruiterStatusDistribution() {}

    public RecruiterStatusDistribution(String status, int applicationCount, int uniqueApplicants) {
        this.status = status;
        this.applicationCount = applicationCount;
        this.uniqueApplicants = uniqueApplicants;
    }

    // --- Getters ---

    public String getStatus() { return status; }
    public int getApplicationCount() { return applicationCount; }
    public int getUniqueApplicants() { return uniqueApplicants; }
    public double getPercentage() { return percentage; }

    // --- Setters ---

    public void setStatus(String status) { this.status = status; }
    public void setApplicationCount(int applicationCount) { this.applicationCount = applicationCount; }
    public void setUniqueApplicants(int uniqueApplicants) { this.uniqueApplicants = uniqueApplicants; }
    public void setPercentage(double percentage) { this.percentage = percentage; }

    @Override
    public String toString() {
        return "RecruiterStatusDistribution{status='" + status + "', count=" + applicationCount
                + ", unique=" + uniqueApplicants + ", pct=" + String.format("%.1f", percentage) + "%}";
    }
}

