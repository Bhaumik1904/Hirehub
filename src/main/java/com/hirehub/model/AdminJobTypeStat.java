package com.hirehub.model;

/**
 * Per-job-type statistics row for the Admin Analytics Dashboard.
 *
 * Date filter applies to jobs.created_at.
 * One instance per Job.JobType value encountered in the period.
 * Missing job types are zero-filled by the service layer.
 *
 * openJobs = activeJobs with deadline >= today (computed server-side).
 */
public class AdminJobTypeStat {

    private String jobType;    // matches Job.JobType name (e.g. "FULL_TIME", "INTERNSHIP")
    private int totalJobs;
    private int activeJobs;    // status = 'ACTIVE'
    private int openJobs;      // status = 'ACTIVE' AND deadline >= today
    private int closedJobs;    // status = 'CLOSED'
    private int draftJobs;     // status = 'DRAFT'

    public AdminJobTypeStat() {}

    public AdminJobTypeStat(String jobType, int totalJobs, int activeJobs,
                            int openJobs, int closedJobs, int draftJobs) {
        this.jobType = jobType;
        this.totalJobs = totalJobs;
        this.activeJobs = activeJobs;
        this.openJobs = openJobs;
        this.closedJobs = closedJobs;
        this.draftJobs = draftJobs;
    }

    // --- Getters ---

    public String getJobType() { return jobType; }
    public int getTotalJobs() { return totalJobs; }
    public int getActiveJobs() { return activeJobs; }
    public int getOpenJobs() { return openJobs; }
    public int getClosedJobs() { return closedJobs; }
    public int getDraftJobs() { return draftJobs; }

    // --- Setters ---

    public void setJobType(String jobType) { this.jobType = jobType; }
    public void setTotalJobs(int totalJobs) { this.totalJobs = totalJobs; }
    public void setActiveJobs(int activeJobs) { this.activeJobs = activeJobs; }
    public void setOpenJobs(int openJobs) { this.openJobs = openJobs; }
    public void setClosedJobs(int closedJobs) { this.closedJobs = closedJobs; }
    public void setDraftJobs(int draftJobs) { this.draftJobs = draftJobs; }

    @Override
    public String toString() {
        return "AdminJobTypeStat{type='" + jobType + "', total=" + totalJobs
                + ", active=" + activeJobs + ", open=" + openJobs
                + ", closed=" + closedJobs + ", draft=" + draftJobs + "}";
    }
}

