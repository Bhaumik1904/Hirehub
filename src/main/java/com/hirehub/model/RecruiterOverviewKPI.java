package com.hirehub.model;

/**
 * Recruiter-scoped overview KPIs for the Recruiter Analytics Dashboard.
 *
 * <p>Date filter semantics (RQ9):
 * <ul>
 *   <li><strong>Portfolio job counts</strong> ({@code totalJobs}, {@code activeStatusJobs},
 *       {@code openAcceptingJobs}, {@code expiredActiveJobs}, {@code closingWithin7DaysJobs},
 *       {@code draftJobs}, {@code closedJobs}, {@code zeroApplicantOpenJobs}) always reflect the
 *       recruiter's full job portfolio so bounded date windows do not hide open postings.</li>
 *   <li><strong>Period counts</strong> ({@code jobsCreatedInPeriod}, {@code periodTotalApplications},
 *       {@code periodPendingApplications}, {@code periodShortlistedApplications},
 *       {@code periodSelectedApplications}, {@code periodRejectedApplications}) respect the active
 *       {@code [from, to)} filter on {@code jobs.created_at} / {@code applications.applied_at}.</li>
 * </ul>
 */
public class RecruiterOverviewKPI {

    // --- Recruiter identity & profile completeness ---
    private boolean recruiterAccountPresent;
    private String recruiterName;
    private String companyName;
    private String industry;
    private boolean profileCompleted;
    private boolean companyWebsitePresent;
    private boolean companyDescriptionPresent;
    private double profileCompletenessRate;   // 0.0 - 100.0 across company profile fields

    // --- Lifetime job portfolio ---
    private int totalJobs;
    private int activeStatusJobs;             // status = 'ACTIVE' (open + expired)
    private int openAcceptingJobs;            // status = 'ACTIVE' AND (deadline IS NULL OR deadline >= today)
    private int expiredActiveJobs;            // status = 'ACTIVE' AND deadline < today
    private int closingWithin7DaysJobs;       // status = 'ACTIVE' AND deadline in [today, today + 7d]
    private int draftJobs;                    // status = 'DRAFT'
    private int closedJobs;                   // status = 'CLOSED'
    private int zeroApplicantOpenJobs;        // openAcceptingJobs with 0 lifetime applications

    // --- Lifetime application totals ---
    private int lifetimeTotalApplications;
    private int lifetimePendingApplications;

    // --- Period-scoped counts [from, to) ---
    private int jobsCreatedInPeriod;
    private int periodTotalApplications;
    private int periodPendingApplications;
    private int periodShortlistedApplications;
    private int periodSelectedApplications;
    private int periodRejectedApplications;

    public RecruiterOverviewKPI() {}

    // --- Getters ---

    public boolean isRecruiterAccountPresent() { return recruiterAccountPresent; }
    public String getRecruiterName() { return recruiterName; }
    public String getCompanyName() { return companyName; }
    public String getIndustry() { return industry; }
    public boolean isProfileCompleted() { return profileCompleted; }
    public boolean isCompanyWebsitePresent() { return companyWebsitePresent; }
    public boolean isCompanyDescriptionPresent() { return companyDescriptionPresent; }
    public double getProfileCompletenessRate() { return profileCompletenessRate; }

    public int getTotalJobs() { return totalJobs; }
    public int getActiveStatusJobs() { return activeStatusJobs; }
    public int getOpenAcceptingJobs() { return openAcceptingJobs; }
    public int getExpiredActiveJobs() { return expiredActiveJobs; }
    public int getClosingWithin7DaysJobs() { return closingWithin7DaysJobs; }
    public int getDraftJobs() { return draftJobs; }
    public int getClosedJobs() { return closedJobs; }
    public int getZeroApplicantOpenJobs() { return zeroApplicantOpenJobs; }

    public int getLifetimeTotalApplications() { return lifetimeTotalApplications; }
    public int getLifetimePendingApplications() { return lifetimePendingApplications; }

    public int getJobsCreatedInPeriod() { return jobsCreatedInPeriod; }
    public int getPeriodTotalApplications() { return periodTotalApplications; }
    public int getPeriodPendingApplications() { return periodPendingApplications; }
    public int getPeriodShortlistedApplications() { return periodShortlistedApplications; }
    public int getPeriodSelectedApplications() { return periodSelectedApplications; }
    public int getPeriodRejectedApplications() { return periodRejectedApplications; }

    // --- Setters ---

    public void setRecruiterAccountPresent(boolean recruiterAccountPresent) { this.recruiterAccountPresent = recruiterAccountPresent; }
    public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setIndustry(String industry) { this.industry = industry; }
    public void setProfileCompleted(boolean profileCompleted) { this.profileCompleted = profileCompleted; }
    public void setCompanyWebsitePresent(boolean companyWebsitePresent) { this.companyWebsitePresent = companyWebsitePresent; }
    public void setCompanyDescriptionPresent(boolean companyDescriptionPresent) { this.companyDescriptionPresent = companyDescriptionPresent; }
    public void setProfileCompletenessRate(double profileCompletenessRate) { this.profileCompletenessRate = profileCompletenessRate; }

    public void setTotalJobs(int totalJobs) { this.totalJobs = totalJobs; }
    public void setActiveStatusJobs(int activeStatusJobs) { this.activeStatusJobs = activeStatusJobs; }
    public void setOpenAcceptingJobs(int openAcceptingJobs) { this.openAcceptingJobs = openAcceptingJobs; }
    public void setExpiredActiveJobs(int expiredActiveJobs) { this.expiredActiveJobs = expiredActiveJobs; }
    public void setClosingWithin7DaysJobs(int closingWithin7DaysJobs) { this.closingWithin7DaysJobs = closingWithin7DaysJobs; }
    public void setDraftJobs(int draftJobs) { this.draftJobs = draftJobs; }
    public void setClosedJobs(int closedJobs) { this.closedJobs = closedJobs; }
    public void setZeroApplicantOpenJobs(int zeroApplicantOpenJobs) { this.zeroApplicantOpenJobs = zeroApplicantOpenJobs; }

    public void setLifetimeTotalApplications(int lifetimeTotalApplications) { this.lifetimeTotalApplications = lifetimeTotalApplications; }
    public void setLifetimePendingApplications(int lifetimePendingApplications) { this.lifetimePendingApplications = lifetimePendingApplications; }

    public void setJobsCreatedInPeriod(int jobsCreatedInPeriod) { this.jobsCreatedInPeriod = jobsCreatedInPeriod; }
    public void setPeriodTotalApplications(int periodTotalApplications) { this.periodTotalApplications = periodTotalApplications; }
    public void setPeriodPendingApplications(int periodPendingApplications) { this.periodPendingApplications = periodPendingApplications; }
    public void setPeriodShortlistedApplications(int periodShortlistedApplications) { this.periodShortlistedApplications = periodShortlistedApplications; }
    public void setPeriodSelectedApplications(int periodSelectedApplications) { this.periodSelectedApplications = periodSelectedApplications; }
    public void setPeriodRejectedApplications(int periodRejectedApplications) { this.periodRejectedApplications = periodRejectedApplications; }

    @Override
    public String toString() {
        return "RecruiterOverviewKPI{present=" + recruiterAccountPresent
                + ", company='" + companyName + "', totalJobs=" + totalJobs
                + ", open=" + openAcceptingJobs + ", expiredActive=" + expiredActiveJobs
                + ", periodApps=" + periodTotalApplications + "}";
    }
}

