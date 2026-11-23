package com.hirehub.model;

/**
 * Recruiter-scoped statistics broken down by {@link Job.JobType}.
 *
 * <p>All canonical {@link Job.JobType} categories ({@code INTERNSHIP}, {@code FULL_TIME},
 * {@code PART_TIME}) are zero-filled by the service layer when absent.</p>
 */
public class RecruiterJobTypeStat {

    private String jobType;
    private int totalJobs;
    private int activeStatusJobs;
    private int openAcceptingJobs;
    private int expiredActiveJobs;
    private int closedJobs;
    private int draftJobs;

    // Applications within [from, to) received by recruiter jobs of this type
    private int periodApplications;
    private int shortlistedApplications;
    private int selectedApplications;
    private int rejectedApplications;

    public RecruiterJobTypeStat() {}

    public RecruiterJobTypeStat(String jobType, int totalJobs, int activeStatusJobs,
                                int openAcceptingJobs, int expiredActiveJobs,
                                int closedJobs, int draftJobs,
                                int periodApplications, int shortlistedApplications,
                                int selectedApplications, int rejectedApplications) {
        this.jobType = jobType;
        this.totalJobs = totalJobs;
        this.activeStatusJobs = activeStatusJobs;
        this.openAcceptingJobs = openAcceptingJobs;
        this.expiredActiveJobs = expiredActiveJobs;
        this.closedJobs = closedJobs;
        this.draftJobs = draftJobs;
        this.periodApplications = periodApplications;
        this.shortlistedApplications = shortlistedApplications;
        this.selectedApplications = selectedApplications;
        this.rejectedApplications = rejectedApplications;
    }

    // --- Getters ---

    public String getJobType() { return jobType; }
    public int getTotalJobs() { return totalJobs; }
    public int getActiveStatusJobs() { return activeStatusJobs; }
    public int getOpenAcceptingJobs() { return openAcceptingJobs; }
    public int getExpiredActiveJobs() { return expiredActiveJobs; }
    public int getClosedJobs() { return closedJobs; }
    public int getDraftJobs() { return draftJobs; }

    public int getPeriodApplications() { return periodApplications; }
    public int getShortlistedApplications() { return shortlistedApplications; }
    public int getSelectedApplications() { return selectedApplications; }
    public int getRejectedApplications() { return rejectedApplications; }

    // --- Setters ---

    public void setJobType(String jobType) { this.jobType = jobType; }
    public void setTotalJobs(int totalJobs) { this.totalJobs = totalJobs; }
    public void setActiveStatusJobs(int activeStatusJobs) { this.activeStatusJobs = activeStatusJobs; }
    public void setOpenAcceptingJobs(int openAcceptingJobs) { this.openAcceptingJobs = openAcceptingJobs; }
    public void setExpiredActiveJobs(int expiredActiveJobs) { this.expiredActiveJobs = expiredActiveJobs; }
    public void setClosedJobs(int closedJobs) { this.closedJobs = closedJobs; }
    public void setDraftJobs(int draftJobs) { this.draftJobs = draftJobs; }

    public void setPeriodApplications(int periodApplications) { this.periodApplications = periodApplications; }
    public void setShortlistedApplications(int shortlistedApplications) { this.shortlistedApplications = shortlistedApplications; }
    public void setSelectedApplications(int selectedApplications) { this.selectedApplications = selectedApplications; }
    public void setRejectedApplications(int rejectedApplications) { this.rejectedApplications = rejectedApplications; }

    @Override
    public String toString() {
        return "RecruiterJobTypeStat{type='" + jobType + "', jobs=" + totalJobs
                + ", open=" + openAcceptingJobs + ", periodApps=" + periodApplications + "}";
    }
}

