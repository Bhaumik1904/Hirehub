package com.hirehub.model;

/**
 * KPI summary for job postings in the Admin Analytics Dashboard.
 *
 * Date filter applies to jobs.created_at (jobs posted in the reporting period).
 *
 * Key distinction:
 *   activeStatusJobs      = jobs WHERE status = 'ACTIVE' (regardless of deadline)
 *   openAcceptingJobs     = jobs WHERE status = 'ACTIVE' AND deadline >= CURDATE()
 *   expiredActiveJobs     = jobs WHERE status = 'ACTIVE' AND deadline < CURDATE()
 *   closedJobs            = jobs WHERE status = 'CLOSED'
 *   draftJobs             = jobs WHERE status = 'DRAFT'
 */
public class AdminJobKPI {

    // --- Raw counts ---
    private int totalJobs;
    private int activeStatusJobs;       // status = 'ACTIVE' (all, including expired deadline)
    private int openAcceptingJobs;      // status = 'ACTIVE' AND deadline >= today
    private int expiredActiveJobs;      // status = 'ACTIVE' AND deadline < today
    private int closedJobs;             // status = 'CLOSED'
    private int draftJobs;              // status = 'DRAFT'
    private int activePostingRecruiters; // distinct recruiters who posted >= 1 job in the period

    public AdminJobKPI() {}

    // --- Getters ---

    public int getTotalJobs() { return totalJobs; }
    public int getActiveStatusJobs() { return activeStatusJobs; }
    public int getOpenAcceptingJobs() { return openAcceptingJobs; }
    public int getExpiredActiveJobs() { return expiredActiveJobs; }
    public int getClosedJobs() { return closedJobs; }
    public int getDraftJobs() { return draftJobs; }
    public int getActivePostingRecruiters() { return activePostingRecruiters; }

    // --- Setters ---

    public void setTotalJobs(int totalJobs) { this.totalJobs = totalJobs; }
    public void setActiveStatusJobs(int activeStatusJobs) { this.activeStatusJobs = activeStatusJobs; }
    public void setOpenAcceptingJobs(int openAcceptingJobs) { this.openAcceptingJobs = openAcceptingJobs; }
    public void setExpiredActiveJobs(int expiredActiveJobs) { this.expiredActiveJobs = expiredActiveJobs; }
    public void setClosedJobs(int closedJobs) { this.closedJobs = closedJobs; }
    public void setDraftJobs(int draftJobs) { this.draftJobs = draftJobs; }
    public void setActivePostingRecruiters(int activePostingRecruiters) { this.activePostingRecruiters = activePostingRecruiters; }

    @Override
    public String toString() {
        return "AdminJobKPI{total=" + totalJobs + ", activeStatus=" + activeStatusJobs
                + ", open=" + openAcceptingJobs + ", expiredActive=" + expiredActiveJobs
                + ", closed=" + closedJobs + ", draft=" + draftJobs
                + ", activeRecruiters=" + activePostingRecruiters + "}";
    }
}

