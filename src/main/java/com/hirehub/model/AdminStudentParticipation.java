package com.hirehub.model;

/**
 * Summary of student engagement with the platform (applications + AI resume analysis).
 *
 * Date filter semantics:
 *   - totalStudents: users WHERE role='STUDENT' AND created_at in period
 *   - studentsWhoApplied: students (registered in period) with >= 1 application
 *   - Resume analysis metrics: resume_analyses.created_at in period
 *
 * Row-multiplication fix (Q7):
 *   Applications and resume_analyses are pre-aggregated per student_id in
 *   independent subqueries before joining to the users table, preventing
 *   Cartesian multiplication when a student has both apps and analyses.
 */
public class AdminStudentParticipation {

    // Student counts (filtered by user registration date)
    private int totalStudents;
    private int studentsWhoApplied;         // at least 1 application

    // Resume analysis counts (filtered by resume_analyses.created_at)
    private int studentsWithAiAnalysis;     // distinct students with >= 1 analysis in period
    private int totalResumeAnalyses;        // total rows in period
    private Double avgResumeScore;          // null when totalResumeAnalyses == 0
    private int jobSpecificAnalyses;        // analyses where job_id IS NOT NULL
    private int generalAnalyses;            // analyses where job_id IS NULL

    public AdminStudentParticipation() {}

    // --- Getters ---

    public int getTotalStudents() { return totalStudents; }
    public int getStudentsWhoApplied() { return studentsWhoApplied; }
    public int getStudentsWithAiAnalysis() { return studentsWithAiAnalysis; }
    public int getTotalResumeAnalyses() { return totalResumeAnalyses; }
    /** Returns null when no analyses exist in the period (never NaN or Infinity). */
    public Double getAvgResumeScore() { return avgResumeScore; }
    public int getJobSpecificAnalyses() { return jobSpecificAnalyses; }
    public int getGeneralAnalyses() { return generalAnalyses; }

    // --- Setters ---

    public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }
    public void setStudentsWhoApplied(int studentsWhoApplied) { this.studentsWhoApplied = studentsWhoApplied; }
    public void setStudentsWithAiAnalysis(int studentsWithAiAnalysis) { this.studentsWithAiAnalysis = studentsWithAiAnalysis; }
    public void setTotalResumeAnalyses(int totalResumeAnalyses) { this.totalResumeAnalyses = totalResumeAnalyses; }
    public void setAvgResumeScore(Double avgResumeScore) { this.avgResumeScore = avgResumeScore; }
    public void setJobSpecificAnalyses(int jobSpecificAnalyses) { this.jobSpecificAnalyses = jobSpecificAnalyses; }
    public void setGeneralAnalyses(int generalAnalyses) { this.generalAnalyses = generalAnalyses; }

    @Override
    public String toString() {
        return "AdminStudentParticipation{total=" + totalStudents
                + ", applied=" + studentsWhoApplied
                + ", analysed=" + studentsWithAiAnalysis
                + ", analyses=" + totalResumeAnalyses
                + ", avgScore=" + avgResumeScore + "}";
    }
}

