package com.hirehub.model;

/**
 * Applicant pool profile, resume, and job-specific AI readiness summary for a recruiter.
 *
 * <p>Schema integrity rules:
 * <ul>
 *   <li>Only job-specific analyses matching both {@code a.student_id} and {@code a.job_id}
 *       (deduplicated to latest {@code MAX(id)} per {@code (student_id, job_id)}) are included;
 *       general resume analyses ({@code job_id IS NULL}) are never mixed in.</li>
 *   <li>{@link #getAvgJobAiScore()} returns {@code null} when no job-specific AI analyses exist
 *       for the scoped applications — averages are never fabricated.</li>
 *   <li>AI compatibility scores are strictly advisory signals and are kept separate from
 *       shortlist/selection hiring metrics.</li>
 * </ul>
 */
public class RecruiterApplicantReadiness {

    public static final String DEFAULT_ADVISORY_DISCLAIMER =
            "Advisory only: AI compatibility scores reflect automated resume-to-job signals "
                    + "and do not represent or override hiring decisions.";

    private boolean readinessDataAvailable;
    private boolean aiAnalysisAvailable;
    private String unavailabilityReason;         // nullable
    private String advisoryDisclaimer = DEFAULT_ADVISORY_DISCLAIMER;

    // Application & distinct candidate pool counts for [from, to)
    private int totalApplicationsInPeriod;
    private int uniqueApplicants;
    private int applicantsWithCompleteProfile;   // college, degree, graduation_year > 2000
    private int applicantsWithActiveResume;      // resumes.is_active = TRUE
    private int applicantsWithAnyResume;         // any resumes row OR non-blank student_profiles.resume_path

    // Job-specific AI analysis coverage for scoped (student_id, job_id) pairs
    private int applicationsWithJobAiAnalysis;
    private int uniqueApplicantsWithJobAiScore;
    private Double avgJobAiScore;                // null when applicationsWithJobAiAnalysis == 0

    // Computed safe rates (0.0 - 100.0, 0.0 when denominator is zero)
    private double profileCompletionRate;
    private double activeResumeCoverageRate;
    private double anyResumeCoverageRate;
    private double jobAiCoverageRate;

    public RecruiterApplicantReadiness() {}

    // --- Getters ---

    public boolean isReadinessDataAvailable() { return readinessDataAvailable; }
    public boolean isAiAnalysisAvailable() { return aiAnalysisAvailable; }
    public String getUnavailabilityReason() { return unavailabilityReason; }
    public String getAdvisoryDisclaimer() { return advisoryDisclaimer; }

    public int getTotalApplicationsInPeriod() { return totalApplicationsInPeriod; }
    public int getUniqueApplicants() { return uniqueApplicants; }
    public int getApplicantsWithCompleteProfile() { return applicantsWithCompleteProfile; }
    public int getApplicantsWithActiveResume() { return applicantsWithActiveResume; }
    public int getApplicantsWithAnyResume() { return applicantsWithAnyResume; }

    public int getApplicationsWithJobAiAnalysis() { return applicationsWithJobAiAnalysis; }
    public int getUniqueApplicantsWithJobAiScore() { return uniqueApplicantsWithJobAiScore; }
    /** Returns null when no job-specific AI analyses exist in the period. */
    public Double getAvgJobAiScore() { return avgJobAiScore; }

    public double getProfileCompletionRate() { return profileCompletionRate; }
    public double getActiveResumeCoverageRate() { return activeResumeCoverageRate; }
    public double getAnyResumeCoverageRate() { return anyResumeCoverageRate; }
    public double getJobAiCoverageRate() { return jobAiCoverageRate; }

    // --- Setters ---

    public void setReadinessDataAvailable(boolean readinessDataAvailable) { this.readinessDataAvailable = readinessDataAvailable; }
    public void setAiAnalysisAvailable(boolean aiAnalysisAvailable) { this.aiAnalysisAvailable = aiAnalysisAvailable; }
    public void setUnavailabilityReason(String unavailabilityReason) { this.unavailabilityReason = unavailabilityReason; }
    public void setAdvisoryDisclaimer(String advisoryDisclaimer) {
        this.advisoryDisclaimer = (advisoryDisclaimer != null && !advisoryDisclaimer.isBlank())
                ? advisoryDisclaimer
                : DEFAULT_ADVISORY_DISCLAIMER;
    }

    public void setTotalApplicationsInPeriod(int totalApplicationsInPeriod) { this.totalApplicationsInPeriod = totalApplicationsInPeriod; }
    public void setUniqueApplicants(int uniqueApplicants) { this.uniqueApplicants = uniqueApplicants; }
    public void setApplicantsWithCompleteProfile(int applicantsWithCompleteProfile) { this.applicantsWithCompleteProfile = applicantsWithCompleteProfile; }
    public void setApplicantsWithActiveResume(int applicantsWithActiveResume) { this.applicantsWithActiveResume = applicantsWithActiveResume; }
    public void setApplicantsWithAnyResume(int applicantsWithAnyResume) { this.applicantsWithAnyResume = applicantsWithAnyResume; }

    public void setApplicationsWithJobAiAnalysis(int applicationsWithJobAiAnalysis) { this.applicationsWithJobAiAnalysis = applicationsWithJobAiAnalysis; }
    public void setUniqueApplicantsWithJobAiScore(int uniqueApplicantsWithJobAiScore) { this.uniqueApplicantsWithJobAiScore = uniqueApplicantsWithJobAiScore; }
    public void setAvgJobAiScore(Double avgJobAiScore) { this.avgJobAiScore = avgJobAiScore; }

    public void setProfileCompletionRate(double profileCompletionRate) { this.profileCompletionRate = profileCompletionRate; }
    public void setActiveResumeCoverageRate(double activeResumeCoverageRate) { this.activeResumeCoverageRate = activeResumeCoverageRate; }
    public void setAnyResumeCoverageRate(double anyResumeCoverageRate) { this.anyResumeCoverageRate = anyResumeCoverageRate; }
    public void setJobAiCoverageRate(double jobAiCoverageRate) { this.jobAiCoverageRate = jobAiCoverageRate; }

    @Override
    public String toString() {
        return "RecruiterApplicantReadiness{readinessAvailable=" + readinessDataAvailable
                + ", aiAvailable=" + aiAnalysisAvailable
                + ", uniqueApplicants=" + uniqueApplicants
                + ", appsWithAi=" + applicationsWithJobAiAnalysis
                + ", avgJobAiScore=" + avgJobAiScore + "}";
    }
}

