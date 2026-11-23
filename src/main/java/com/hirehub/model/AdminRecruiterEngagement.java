package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * Per-recruiter engagement metrics for the Admin Analytics Dashboard.
 *
 * Date-filter semantics (Q8 — two distinct date scopes):
 *   Scope A — jobs.created_at in reporting period:
 *     totalJobsPosted, activeJobs, openJobs, lastJobPostedAt
 *   Scope B — applications for Scope-A jobs (no additional apply-date filter):
 *     applicationsReceived, shortlistedCount, selectedCount
 *     Rationale: measures recruiter's conversion success on jobs they posted in period,
 *     regardless of when students submitted those applications.
 *
 * lastAppReviewAt: MAX(applications.updated_at) for recruiter's jobs — proxy for
 * "last time recruiter reviewed/changed an application status". Nullable.
 *
 * profileUpdatedAt: recruiter_profiles.updated_at. Nullable (recruiter may not have a profile row).
 */
public class AdminRecruiterEngagement {

    private int recruiterId;
    private String recruiterName;
    private String recruiterEmail;
    private String companyName;      // from recruiter_profiles (or fallback to jobs.company_name); nullable
    private String industry;         // from recruiter_profiles; nullable

    // Scope A: jobs posted in the period
    private int totalJobsPosted;
    private int activeJobs;
    private int openJobs;            // activeJobs with deadline >= today
    private LocalDateTime lastJobPostedAt;  // nullable

    // Scope B: applications on those jobs
    private int applicationsReceived;
    private int shortlistedCount;
    private int selectedCount;

    // Profile / activity metadata
    private LocalDateTime profileUpdatedAt;  // nullable — recruiter_profiles.updated_at
    private LocalDateTime lastAppReviewAt;   // nullable — MAX(applications.updated_at)

    public AdminRecruiterEngagement() {}

    // --- Getters ---

    public int getRecruiterId() { return recruiterId; }
    public String getRecruiterName() { return recruiterName; }
    public String getRecruiterEmail() { return recruiterEmail; }
    public String getCompanyName() { return companyName; }
    public String getIndustry() { return industry; }
    public int getTotalJobsPosted() { return totalJobsPosted; }
    public int getActiveJobs() { return activeJobs; }
    public int getOpenJobs() { return openJobs; }
    public LocalDateTime getLastJobPostedAt() { return lastJobPostedAt; }
    public int getApplicationsReceived() { return applicationsReceived; }
    public int getShortlistedCount() { return shortlistedCount; }
    public int getSelectedCount() { return selectedCount; }
    public LocalDateTime getProfileUpdatedAt() { return profileUpdatedAt; }
    public LocalDateTime getLastAppReviewAt() { return lastAppReviewAt; }

    // --- Setters ---

    public void setRecruiterId(int recruiterId) { this.recruiterId = recruiterId; }
    public void setRecruiterName(String recruiterName) { this.recruiterName = recruiterName; }
    public void setRecruiterEmail(String recruiterEmail) { this.recruiterEmail = recruiterEmail; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setIndustry(String industry) { this.industry = industry; }
    public void setTotalJobsPosted(int totalJobsPosted) { this.totalJobsPosted = totalJobsPosted; }
    public void setActiveJobs(int activeJobs) { this.activeJobs = activeJobs; }
    public void setOpenJobs(int openJobs) { this.openJobs = openJobs; }
    public void setLastJobPostedAt(LocalDateTime lastJobPostedAt) { this.lastJobPostedAt = lastJobPostedAt; }
    public void setApplicationsReceived(int applicationsReceived) { this.applicationsReceived = applicationsReceived; }
    public void setShortlistedCount(int shortlistedCount) { this.shortlistedCount = shortlistedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }
    public void setProfileUpdatedAt(LocalDateTime profileUpdatedAt) { this.profileUpdatedAt = profileUpdatedAt; }
    public void setLastAppReviewAt(LocalDateTime lastAppReviewAt) { this.lastAppReviewAt = lastAppReviewAt; }

    @Override
    public String toString() {
        return "AdminRecruiterEngagement{id=" + recruiterId + ", name='" + recruiterName
                + "', company='" + companyName + "', jobs=" + totalJobsPosted
                + ", apps=" + applicationsReceived + "}";
    }
}

