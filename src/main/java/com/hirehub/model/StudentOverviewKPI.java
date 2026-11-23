package com.hirehub.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StudentOverviewKPI — Key performance indicators for the student dashboard.
 *
 * <p>Includes application pipeline counts, platform open job totals, profile completeness metrics,
 * and latest resume evaluation signals. Missing resume score is represented as {@code null},
 * never zero, to avoid misrepresenting un-evaluated resumes as failures.</p>
 */
public class StudentOverviewKPI {

    private int totalApplications;
    private int pendingCount;
    private int shortlistedCount;
    private int rejectedCount;
    private int selectedCount;

    private int openActiveJobs;

    private boolean hasProfile;
    private boolean profileComplete;
    private int profileCompletionScore;
    private List<String> missingProfileFields = new ArrayList<>();

    private boolean hasActiveResume;
    private boolean hasHistoricalResumes;
    private Integer latestResumeScore;
    private String latestConfidence;

    public StudentOverviewKPI() {}

    public StudentOverviewKPI(int totalApplications, int pendingCount, int shortlistedCount,
                              int rejectedCount, int selectedCount, int openActiveJobs,
                              boolean hasProfile, boolean profileComplete, int profileCompletionScore,
                              List<String> missingProfileFields, boolean hasActiveResume,
                              boolean hasHistoricalResumes, Integer latestResumeScore,
                              String latestConfidence) {
        this.totalApplications = totalApplications;
        this.pendingCount = pendingCount;
        this.shortlistedCount = shortlistedCount;
        this.rejectedCount = rejectedCount;
        this.selectedCount = selectedCount;
        this.openActiveJobs = openActiveJobs;
        this.hasProfile = hasProfile;
        this.profileComplete = profileComplete;
        this.profileCompletionScore = profileCompletionScore;
        this.missingProfileFields = missingProfileFields != null ? new ArrayList<>(missingProfileFields) : new ArrayList<>();
        this.hasActiveResume = hasActiveResume;
        this.hasHistoricalResumes = hasHistoricalResumes;
        this.latestResumeScore = latestResumeScore;
        this.latestConfidence = latestConfidence;
    }

    // Getters and Setters

    public int getTotalApplications() { return totalApplications; }
    public void setTotalApplications(int totalApplications) { this.totalApplications = totalApplications; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getShortlistedCount() { return shortlistedCount; }
    public void setShortlistedCount(int shortlistedCount) { this.shortlistedCount = shortlistedCount; }

    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    public int getSelectedCount() { return selectedCount; }
    public void setSelectedCount(int selectedCount) { this.selectedCount = selectedCount; }

    public int getOpenActiveJobs() { return openActiveJobs; }
    public void setOpenActiveJobs(int openActiveJobs) { this.openActiveJobs = openActiveJobs; }

    public boolean isHasProfile() { return hasProfile; }
    public void setHasProfile(boolean hasProfile) { this.hasProfile = hasProfile; }

    public boolean isProfileComplete() { return profileComplete; }
    public void setProfileComplete(boolean profileComplete) { this.profileComplete = profileComplete; }

    public int getProfileCompletionScore() { return profileCompletionScore; }
    public void setProfileCompletionScore(int profileCompletionScore) { this.profileCompletionScore = profileCompletionScore; }

    public List<String> getMissingProfileFields() {
        return missingProfileFields != null ? Collections.unmodifiableList(missingProfileFields) : Collections.emptyList();
    }
    public void setMissingProfileFields(List<String> missingProfileFields) {
        this.missingProfileFields = missingProfileFields != null ? new ArrayList<>(missingProfileFields) : new ArrayList<>();
    }

    public boolean isHasActiveResume() { return hasActiveResume; }
    public void setHasActiveResume(boolean hasActiveResume) { this.hasActiveResume = hasActiveResume; }

    public boolean isHasHistoricalResumes() { return hasHistoricalResumes; }
    public void setHasHistoricalResumes(boolean hasHistoricalResumes) { this.hasHistoricalResumes = hasHistoricalResumes; }

    public Integer getLatestResumeScore() { return latestResumeScore; }
    public void setLatestResumeScore(Integer latestResumeScore) { this.latestResumeScore = latestResumeScore; }

    public String getLatestConfidence() { return latestConfidence; }
    public void setLatestConfidence(String latestConfidence) { this.latestConfidence = latestConfidence; }
}

