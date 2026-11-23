package com.hirehub.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StudentResumeReadiness — Readiness report and evaluation signals for the student's active resume.
 *
 * <p>Exposes live rubric scores, parsed structural metrics, and previous persisted evaluations.
 * Internal file paths and raw document texts are never exposed. A prominent non-prediction
 * disclaimer is included.</p>
 */
public class StudentResumeReadiness {

    public static final String DEFAULT_DISCLAIMER =
            "Deterministic resume quality indicator — not a hiring probability";

    private boolean hasActiveResume;
    private boolean hasHistoricalResumes;
    private int historicalResumeCount;

    private String activeResumeFileName;
    private LocalDateTime activeResumeUploadedAt;
    private long activeResumeFileSizeBytes;

    // Parsed signal counts
    private int parsedSkillCount;
    private int parsedExperienceCount;
    private int parsedProjectCount;
    private int parsedCertificationCount;
    private int parsedMetricsCount;

    // Live deterministic breakdown
    private Integer overallScore;
    private Integer skillsScore;
    private Integer experienceScore;
    private Integer educationScore;
    private Integer completenessScore;
    private Integer certificationsScore;
    private String evaluationConfidence;
    private List<String> unevaluatedCriteria = new ArrayList<>();

    // Latest persisted analysis
    private Integer persistedOverallScore;
    private String persistedMatchingSkills;
    private String persistedMissingSkills;
    private LocalDateTime persistedCreatedAt;

    private String disclaimerText = DEFAULT_DISCLAIMER;

    public StudentResumeReadiness() {}

    // Getters and Setters

    public boolean isHasActiveResume() { return hasActiveResume; }
    public void setHasActiveResume(boolean hasActiveResume) { this.hasActiveResume = hasActiveResume; }

    public boolean isHasHistoricalResumes() { return hasHistoricalResumes; }
    public void setHasHistoricalResumes(boolean hasHistoricalResumes) { this.hasHistoricalResumes = hasHistoricalResumes; }

    public int getHistoricalResumeCount() { return historicalResumeCount; }
    public void setHistoricalResumeCount(int historicalResumeCount) { this.historicalResumeCount = historicalResumeCount; }

    public String getActiveResumeFileName() { return activeResumeFileName; }
    public void setActiveResumeFileName(String activeResumeFileName) { this.activeResumeFileName = activeResumeFileName; }

    public LocalDateTime getActiveResumeUploadedAt() { return activeResumeUploadedAt; }
    public void setActiveResumeUploadedAt(LocalDateTime activeResumeUploadedAt) { this.activeResumeUploadedAt = activeResumeUploadedAt; }

    public long getActiveResumeFileSizeBytes() { return activeResumeFileSizeBytes; }
    public void setActiveResumeFileSizeBytes(long activeResumeFileSizeBytes) { this.activeResumeFileSizeBytes = activeResumeFileSizeBytes; }

    public int getParsedSkillCount() { return parsedSkillCount; }
    public void setParsedSkillCount(int parsedSkillCount) { this.parsedSkillCount = parsedSkillCount; }

    public int getParsedExperienceCount() { return parsedExperienceCount; }
    public void setParsedExperienceCount(int parsedExperienceCount) { this.parsedExperienceCount = parsedExperienceCount; }

    public int getParsedProjectCount() { return parsedProjectCount; }
    public void setParsedProjectCount(int parsedProjectCount) { this.parsedProjectCount = parsedProjectCount; }

    public int getParsedCertificationCount() { return parsedCertificationCount; }
    public void setParsedCertificationCount(int parsedCertificationCount) { this.parsedCertificationCount = parsedCertificationCount; }

    public int getParsedMetricsCount() { return parsedMetricsCount; }
    public void setParsedMetricsCount(int parsedMetricsCount) { this.parsedMetricsCount = parsedMetricsCount; }

    public Integer getOverallScore() { return overallScore; }
    public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }

    public Integer getSkillsScore() { return skillsScore; }
    public void setSkillsScore(Integer skillsScore) { this.skillsScore = skillsScore; }

    public Integer getExperienceScore() { return experienceScore; }
    public void setExperienceScore(Integer experienceScore) { this.experienceScore = experienceScore; }

    public Integer getEducationScore() { return educationScore; }
    public void setEducationScore(Integer educationScore) { this.educationScore = educationScore; }

    public Integer getCompletenessScore() { return completenessScore; }
    public void setCompletenessScore(Integer completenessScore) { this.completenessScore = completenessScore; }

    public Integer getCertificationsScore() { return certificationsScore; }
    public void setCertificationsScore(Integer certificationsScore) { this.certificationsScore = certificationsScore; }

    public String getEvaluationConfidence() { return evaluationConfidence; }
    public void setEvaluationConfidence(String evaluationConfidence) { this.evaluationConfidence = evaluationConfidence; }

    public List<String> getUnevaluatedCriteria() {
        return unevaluatedCriteria != null ? Collections.unmodifiableList(unevaluatedCriteria) : Collections.emptyList();
    }
    public void setUnevaluatedCriteria(List<String> unevaluatedCriteria) {
        this.unevaluatedCriteria = unevaluatedCriteria != null ? new ArrayList<>(unevaluatedCriteria) : new ArrayList<>();
    }

    public Integer getPersistedOverallScore() { return persistedOverallScore; }
    public void setPersistedOverallScore(Integer persistedOverallScore) { this.persistedOverallScore = persistedOverallScore; }

    public String getPersistedMatchingSkills() { return persistedMatchingSkills; }
    public void setPersistedMatchingSkills(String persistedMatchingSkills) { this.persistedMatchingSkills = persistedMatchingSkills; }

    public String getPersistedMissingSkills() { return persistedMissingSkills; }
    public void setPersistedMissingSkills(String persistedMissingSkills) { this.persistedMissingSkills = persistedMissingSkills; }

    public LocalDateTime getPersistedCreatedAt() { return persistedCreatedAt; }
    public void setPersistedCreatedAt(LocalDateTime persistedCreatedAt) { this.persistedCreatedAt = persistedCreatedAt; }

    public String getDisclaimerText() { return disclaimerText; }
    public void setDisclaimerText(String disclaimerText) { this.disclaimerText = disclaimerText; }
}

