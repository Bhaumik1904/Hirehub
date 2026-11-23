package com.hirehub.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StudentJobRecommendation — Skill-matched job opportunity recommendation for students.
 */
public class StudentJobRecommendation {

    private int jobId;
    private String title;
    private String companyName;
    private String jobType;
    private String location;
    private LocalDate deadline;
    private int matchingSkillCount;
    private int totalRequiredSkillCount;
    private double matchPercentage;
    private List<String> matchedSkills = new ArrayList<>();
    private List<String> missingSkills = new ArrayList<>();
    private boolean alreadyApplied;

    public StudentJobRecommendation() {}

    public StudentJobRecommendation(int jobId, String title, String companyName,
                                    String jobType, String location, LocalDate deadline,
                                    int matchingSkillCount, int totalRequiredSkillCount,
                                    double matchPercentage, List<String> matchedSkills,
                                    List<String> missingSkills, boolean alreadyApplied) {
        this.jobId = jobId;
        this.title = title;
        this.companyName = companyName;
        this.jobType = jobType;
        this.location = location;
        this.deadline = deadline;
        this.matchingSkillCount = matchingSkillCount;
        this.totalRequiredSkillCount = totalRequiredSkillCount;
        this.matchPercentage = matchPercentage;
        this.matchedSkills = matchedSkills != null ? new ArrayList<>(matchedSkills) : new ArrayList<>();
        this.missingSkills = missingSkills != null ? new ArrayList<>(missingSkills) : new ArrayList<>();
        this.alreadyApplied = alreadyApplied;
    }

    // Getters and Setters

    public int getJobId() { return jobId; }
    public void setJobId(int jobId) { this.jobId = jobId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public int getMatchingSkillCount() { return matchingSkillCount; }
    public void setMatchingSkillCount(int matchingSkillCount) { this.matchingSkillCount = matchingSkillCount; }

    public int getTotalRequiredSkillCount() { return totalRequiredSkillCount; }
    public void setTotalRequiredSkillCount(int totalRequiredSkillCount) { this.totalRequiredSkillCount = totalRequiredSkillCount; }

    public double getMatchPercentage() { return matchPercentage; }
    public void setMatchPercentage(double matchPercentage) { this.matchPercentage = matchPercentage; }

    public List<String> getMatchedSkills() {
        return matchedSkills != null ? Collections.unmodifiableList(matchedSkills) : Collections.emptyList();
    }
    public void setMatchedSkills(List<String> matchedSkills) {
        this.matchedSkills = matchedSkills != null ? new ArrayList<>(matchedSkills) : new ArrayList<>();
    }

    public List<String> getMissingSkills() {
        return missingSkills != null ? Collections.unmodifiableList(missingSkills) : Collections.emptyList();
    }
    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills != null ? new ArrayList<>(missingSkills) : new ArrayList<>();
    }

    public boolean isAlreadyApplied() { return alreadyApplied; }
    public void setAlreadyApplied(boolean alreadyApplied) { this.alreadyApplied = alreadyApplied; }
}

