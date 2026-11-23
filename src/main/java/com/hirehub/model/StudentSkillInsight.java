package com.hirehub.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StudentSkillInsight — Skill overlap, union, and demand gap insights for a student.
 */
public class StudentSkillInsight {

    private List<String> profileSkills = new ArrayList<>();
    private List<String> resumeSkills = new ArrayList<>();
    private List<String> combinedSkills = new ArrayList<>();
    private List<String> persistedMissingSkills = new ArrayList<>();
    private List<String> inDemandMissingSkills = new ArrayList<>();

    public StudentSkillInsight() {}

    public StudentSkillInsight(List<String> profileSkills, List<String> resumeSkills,
                               List<String> combinedSkills, List<String> persistedMissingSkills,
                               List<String> inDemandMissingSkills) {
        this.profileSkills = profileSkills != null ? new ArrayList<>(profileSkills) : new ArrayList<>();
        this.resumeSkills = resumeSkills != null ? new ArrayList<>(resumeSkills) : new ArrayList<>();
        this.combinedSkills = combinedSkills != null ? new ArrayList<>(combinedSkills) : new ArrayList<>();
        this.persistedMissingSkills = persistedMissingSkills != null ? new ArrayList<>(persistedMissingSkills) : new ArrayList<>();
        this.inDemandMissingSkills = inDemandMissingSkills != null ? new ArrayList<>(inDemandMissingSkills) : new ArrayList<>();
    }

    // Getters and Setters

    public List<String> getProfileSkills() {
        return profileSkills != null ? Collections.unmodifiableList(profileSkills) : Collections.emptyList();
    }
    public void setProfileSkills(List<String> profileSkills) {
        this.profileSkills = profileSkills != null ? new ArrayList<>(profileSkills) : new ArrayList<>();
    }

    public List<String> getResumeSkills() {
        return resumeSkills != null ? Collections.unmodifiableList(resumeSkills) : Collections.emptyList();
    }
    public void setResumeSkills(List<String> resumeSkills) {
        this.resumeSkills = resumeSkills != null ? new ArrayList<>(resumeSkills) : new ArrayList<>();
    }

    public List<String> getCombinedSkills() {
        return combinedSkills != null ? Collections.unmodifiableList(combinedSkills) : Collections.emptyList();
    }
    public void setCombinedSkills(List<String> combinedSkills) {
        this.combinedSkills = combinedSkills != null ? new ArrayList<>(combinedSkills) : new ArrayList<>();
    }

    public List<String> getPersistedMissingSkills() {
        return persistedMissingSkills != null ? Collections.unmodifiableList(persistedMissingSkills) : Collections.emptyList();
    }
    public void setPersistedMissingSkills(List<String> persistedMissingSkills) {
        this.persistedMissingSkills = persistedMissingSkills != null ? new ArrayList<>(persistedMissingSkills) : new ArrayList<>();
    }

    public List<String> getInDemandMissingSkills() {
        return inDemandMissingSkills != null ? Collections.unmodifiableList(inDemandMissingSkills) : Collections.emptyList();
    }
    public void setInDemandMissingSkills(List<String> inDemandMissingSkills) {
        this.inDemandMissingSkills = inDemandMissingSkills != null ? new ArrayList<>(inDemandMissingSkills) : new ArrayList<>();
    }
}

