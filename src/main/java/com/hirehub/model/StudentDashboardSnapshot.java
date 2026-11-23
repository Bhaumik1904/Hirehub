package com.hirehub.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StudentDashboardSnapshot — Aggregate immutable snapshot delivered to the student dashboard.
 *
 * <p>Consolidates all student overview KPIs, application rows, resume readiness metrics, skill gap insights,
 * recommendations, and time-series trends in a single payload.</p>
 */
public class StudentDashboardSnapshot {

    private int studentId;
    private StudentOverviewKPI kpi = new StudentOverviewKPI();
    private StudentStatusDistribution statusDistribution = new StudentStatusDistribution();
    private List<StudentApplicationRow> recentApplications = new ArrayList<>();
    private StudentResumeReadiness resumeReadiness = new StudentResumeReadiness();
    private StudentSkillInsight skillInsight = new StudentSkillInsight();
    private List<StudentJobRecommendation> recommendations = new ArrayList<>();
    private List<StudentTrendPoint> applicationTrends = new ArrayList<>();
    private List<StudentActivityEvent> recentActivities = new ArrayList<>();
    private LocalDateTime generatedAt = LocalDateTime.now();
    private String errorMessage;

    public StudentDashboardSnapshot() {}

    public static StudentDashboardSnapshot empty(int studentId, String error) {
        StudentDashboardSnapshot s = new StudentDashboardSnapshot();
        s.setStudentId(studentId);
        s.setErrorMessage(error);
        s.setKpi(new StudentOverviewKPI());
        s.setStatusDistribution(new StudentStatusDistribution(0, 0, 0, 0));
        s.setResumeReadiness(new StudentResumeReadiness());
        s.setSkillInsight(new StudentSkillInsight());
        return s;
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    // Getters and Setters

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public StudentOverviewKPI getKpi() { return kpi; }
    public void setKpi(StudentOverviewKPI kpi) { this.kpi = kpi != null ? kpi : new StudentOverviewKPI(); }

    public StudentStatusDistribution getStatusDistribution() { return statusDistribution; }
    public void setStatusDistribution(StudentStatusDistribution statusDistribution) {
        this.statusDistribution = statusDistribution != null ? statusDistribution : new StudentStatusDistribution();
    }

    public List<StudentApplicationRow> getRecentApplications() {
        return recentApplications != null ? Collections.unmodifiableList(recentApplications) : Collections.emptyList();
    }
    public void setRecentApplications(List<StudentApplicationRow> recentApplications) {
        this.recentApplications = recentApplications != null ? new ArrayList<>(recentApplications) : new ArrayList<>();
    }

    public StudentResumeReadiness getResumeReadiness() { return resumeReadiness; }
    public void setResumeReadiness(StudentResumeReadiness resumeReadiness) {
        this.resumeReadiness = resumeReadiness != null ? resumeReadiness : new StudentResumeReadiness();
    }

    public StudentSkillInsight getSkillInsight() { return skillInsight; }
    public void setSkillInsight(StudentSkillInsight skillInsight) {
        this.skillInsight = skillInsight != null ? skillInsight : new StudentSkillInsight();
    }

    public List<StudentJobRecommendation> getRecommendations() {
        return recommendations != null ? Collections.unmodifiableList(recommendations) : Collections.emptyList();
    }
    public void setRecommendations(List<StudentJobRecommendation> recommendations) {
        this.recommendations = recommendations != null ? new ArrayList<>(recommendations) : new ArrayList<>();
    }

    public List<StudentTrendPoint> getApplicationTrends() {
        return applicationTrends != null ? Collections.unmodifiableList(applicationTrends) : Collections.emptyList();
    }
    public void setApplicationTrends(List<StudentTrendPoint> applicationTrends) {
        this.applicationTrends = applicationTrends != null ? new ArrayList<>(applicationTrends) : new ArrayList<>();
    }

    public List<StudentActivityEvent> getRecentActivities() {
        return recentActivities != null ? Collections.unmodifiableList(recentActivities) : Collections.emptyList();
    }
    public void setRecentActivities(List<StudentActivityEvent> recentActivities) {
        this.recentActivities = recentActivities != null ? new ArrayList<>(recentActivities) : new ArrayList<>();
    }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

