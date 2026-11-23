package com.hirehub.model;

/**
 * Recruiter-scoped application pipeline KPIs and safe conversion metrics.
 *
 * <p>Date filter applies to {@code applications.applied_at} for applications
 * belonging to jobs owned by the target recruiter.</p>
 *
 * <p>All rates are bounded finite percentages in {@code [0.0, 100.0]} and fall back
 * cleanly to {@code 0.0} when denominators are zero.</p>
 */
public class RecruiterPipelineKPI {

    // --- Raw counts ---
    private int totalApplications;
    private int pendingApplications;
    private int shortlistedApplications;
    private int selectedApplications;
    private int rejectedApplications;
    private int reviewedApplications;       // shortlisted + selected + rejected

    private int uniqueApplicants;           // COUNT(DISTINCT student_id)
    private int uniquePendingApplicants;
    private int uniqueShortlistedApplicants;
    private int uniqueSelectedApplicants;
    private int uniqueRejectedApplicants;
    private int jobsWithApplications;       // distinct recruiter jobs receiving >= 1 app in period

    // --- Computed rates (0.0 - 100.0, 0.0 when denominator is 0) ---
    private double reviewCompletionRate;    // reviewedApplications / totalApplications * 100
    private double shortlistRate;           // shortlistedApplications / totalApplications * 100
    private double selectionRate;           // selectedApplications / totalApplications * 100
    private double rejectionRate;           // rejectedApplications / totalApplications * 100
    private double advancementRate;         // (shortlisted + selected) / totalApplications * 100
    private double uniqueHireRate;          // uniqueSelectedApplicants / uniqueApplicants * 100

    public RecruiterPipelineKPI() {}

    // --- Getters ---

    public int getTotalApplications() { return totalApplications; }
    public int getPendingApplications() { return pendingApplications; }
    public int getShortlistedApplications() { return shortlistedApplications; }
    public int getSelectedApplications() { return selectedApplications; }
    public int getRejectedApplications() { return rejectedApplications; }
    public int getReviewedApplications() { return reviewedApplications; }

    public int getUniqueApplicants() { return uniqueApplicants; }
    public int getUniquePendingApplicants() { return uniquePendingApplicants; }
    public int getUniqueShortlistedApplicants() { return uniqueShortlistedApplicants; }
    public int getUniqueSelectedApplicants() { return uniqueSelectedApplicants; }
    public int getUniqueRejectedApplicants() { return uniqueRejectedApplicants; }
    public int getJobsWithApplications() { return jobsWithApplications; }

    public double getReviewCompletionRate() { return reviewCompletionRate; }
    public double getShortlistRate() { return shortlistRate; }
    public double getSelectionRate() { return selectionRate; }
    public double getRejectionRate() { return rejectionRate; }
    public double getAdvancementRate() { return advancementRate; }
    public double getUniqueHireRate() { return uniqueHireRate; }

    // --- Setters ---

    public void setTotalApplications(int totalApplications) { this.totalApplications = totalApplications; }
    public void setPendingApplications(int pendingApplications) { this.pendingApplications = pendingApplications; }
    public void setShortlistedApplications(int shortlistedApplications) { this.shortlistedApplications = shortlistedApplications; }
    public void setSelectedApplications(int selectedApplications) { this.selectedApplications = selectedApplications; }
    public void setRejectedApplications(int rejectedApplications) { this.rejectedApplications = rejectedApplications; }
    public void setReviewedApplications(int reviewedApplications) { this.reviewedApplications = reviewedApplications; }

    public void setUniqueApplicants(int uniqueApplicants) { this.uniqueApplicants = uniqueApplicants; }
    public void setUniquePendingApplicants(int uniquePendingApplicants) { this.uniquePendingApplicants = uniquePendingApplicants; }
    public void setUniqueShortlistedApplicants(int uniqueShortlistedApplicants) { this.uniqueShortlistedApplicants = uniqueShortlistedApplicants; }
    public void setUniqueSelectedApplicants(int uniqueSelectedApplicants) { this.uniqueSelectedApplicants = uniqueSelectedApplicants; }
    public void setUniqueRejectedApplicants(int uniqueRejectedApplicants) { this.uniqueRejectedApplicants = uniqueRejectedApplicants; }
    public void setJobsWithApplications(int jobsWithApplications) { this.jobsWithApplications = jobsWithApplications; }

    public void setReviewCompletionRate(double reviewCompletionRate) { this.reviewCompletionRate = reviewCompletionRate; }
    public void setShortlistRate(double shortlistRate) { this.shortlistRate = shortlistRate; }
    public void setSelectionRate(double selectionRate) { this.selectionRate = selectionRate; }
    public void setRejectionRate(double rejectionRate) { this.rejectionRate = rejectionRate; }
    public void setAdvancementRate(double advancementRate) { this.advancementRate = advancementRate; }
    public void setUniqueHireRate(double uniqueHireRate) { this.uniqueHireRate = uniqueHireRate; }

    @Override
    public String toString() {
        return "RecruiterPipelineKPI{total=" + totalApplications
                + ", pending=" + pendingApplications
                + ", shortlisted=" + shortlistedApplications
                + ", selected=" + selectedApplications
                + ", rejected=" + rejectedApplications
                + ", uniqueApplicants=" + uniqueApplicants + "}";
    }
}

