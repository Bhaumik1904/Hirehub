package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * ResumeAnalysis — Stores the calculated compatibility score and AI evaluation breakdown.
 * Maps to the `resume_analyses` table.
 */
public class ResumeAnalysis {

    private int           id;
    private Integer       resumeId;
    private Integer       jobId;
    private int           studentId;
    private int           overallScore;
    private int           skillsScore;
    private int           experienceScore;
    private int           educationScore;
    private int           completenessScore;
    private int           certificationsScore;
    private String        matchingSkills;
    private String        missingSkills;
    private String        analysisSummary;
    private String        qualityFeedback;
    private String        improvementPlan;
    private LocalDateTime createdAt;

    // ── Joined display fields ───────────────────────────────────────────────
    private String jobTitle;
    private String companyName;
    private String fileName;

    public ResumeAnalysis() {}

    public ResumeAnalysis(Integer resumeId, Integer jobId, int studentId,
                          int overallScore, int skillsScore, int experienceScore,
                          int educationScore, int completenessScore, int certificationsScore,
                          String matchingSkills, String missingSkills,
                          String analysisSummary, String qualityFeedback, String improvementPlan) {
        this.resumeId            = resumeId;
        this.jobId               = jobId;
        this.studentId           = studentId;
        this.overallScore        = overallScore;
        this.skillsScore         = skillsScore;
        this.experienceScore     = experienceScore;
        this.educationScore      = educationScore;
        this.completenessScore   = completenessScore;
        this.certificationsScore = certificationsScore;
        this.matchingSkills      = matchingSkills;
        this.missingSkills       = missingSkills;
        this.analysisSummary     = analysisSummary;
        this.qualityFeedback     = qualityFeedback;
        this.improvementPlan     = improvementPlan;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int getId()                                        { return id; }
    public void setId(int id)                                 { this.id = id; }

    public Integer getResumeId()                              { return resumeId; }
    public void setResumeId(Integer resumeId)                 { this.resumeId = resumeId; }

    public Integer getJobId()                                 { return jobId; }
    public void setJobId(Integer jobId)                       { this.jobId = jobId; }

    public boolean isGeneralAnalysis()                        { return jobId == null || jobId <= 0; }

    public int getStudentId()                                 { return studentId; }
    public void setStudentId(int studentId)                   { this.studentId = studentId; }

    public int getOverallScore()                              { return overallScore; }
    public void setOverallScore(int score)                    { this.overallScore = score; }

    public int getSkillsScore()                               { return skillsScore; }
    public void setSkillsScore(int score)                     { this.skillsScore = score; }

    public int getExperienceScore()                           { return experienceScore; }
    public void setExperienceScore(int score)                 { this.experienceScore = score; }

    public int getEducationScore()                            { return educationScore; }
    public void setEducationScore(int score)                  { this.educationScore = score; }

    public int getCompletenessScore()                         { return completenessScore; }
    public void setCompletenessScore(int score)               { this.completenessScore = score; }

    public int getCertificationsScore()                       { return certificationsScore; }
    public void setCertificationsScore(int score)             { this.certificationsScore = score; }

    public String getMatchingSkills()                         { return matchingSkills; }
    public void setMatchingSkills(String skills)              { this.matchingSkills = skills; }

    public String getMissingSkills()                          { return missingSkills; }
    public void setMissingSkills(String skills)               { this.missingSkills = skills; }

    public String getAnalysisSummary()                        { return analysisSummary; }
    public void setAnalysisSummary(String summary)            { this.analysisSummary = summary; }

    public String getQualityFeedback()                        { return qualityFeedback; }
    public void setQualityFeedback(String feedback)           { this.qualityFeedback = feedback; }

    public String getImprovementPlan()                        { return improvementPlan; }
    public void setImprovementPlan(String plan)               { this.improvementPlan = plan; }

    public LocalDateTime getCreatedAt()                       { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)         { this.createdAt = createdAt; }

    public String getJobTitle() {
        if (jobTitle != null && !jobTitle.isBlank()) return jobTitle;
        return isGeneralAnalysis() ? "General Resume Review" : "Target Job";
    }
    public void setJobTitle(String jobTitle)                  { this.jobTitle = jobTitle; }

    public String getCompanyName() {
        if (companyName != null && !companyName.isBlank()) return companyName;
        return isGeneralAnalysis() ? "— (General)" : "—";
    }
    public void setCompanyName(String companyName)            { this.companyName = companyName; }

    public String getFileName()                               { return fileName; }
    public void setFileName(String fileName)                  { this.fileName = fileName; }

    public String getScoreBadgeClass() {
        if (overallScore >= 80) return "score-badge-high";
        if (overallScore >= 60) return "score-badge-mid";
        return "score-badge-low";
    }
}

