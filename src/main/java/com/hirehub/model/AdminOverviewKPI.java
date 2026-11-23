package com.hirehub.model;

/**
 * KPI summary for user overview in the Admin Analytics Dashboard.
 * Covers total registered users and profile completion rates.
 *
 * Date filter applies to users.created_at (users who registered in the period).
 * Profile-completion and resume metrics are counts within those registered users.
 */
public class AdminOverviewKPI {

    // --- Raw counts ---
    private int totalStudents;
    private int totalRecruiters;
    private int totalAdmins;
    private int totalUsers;

    // Student profile completeness
    private int studentsCompletedProfile;    // college, degree, graduationYear all present
    private int studentsWithAnyResume;       // ever uploaded at least one resume (is_active OR inactive)
    private int studentsEverUploadedResume;  // alias for clarity — same metric
    private int studentsWithActivePdfResume; // current is_active = TRUE

    // Recruiter profile completeness
    private int recruitersCompletedProfile;  // recruiter_profiles row exists

    // --- Computed rates (0.0–100.0, 0.0 when denominator is zero) ---
    private double studentProfileCompletionRate;    // studentsCompletedProfile / totalStudents * 100
    private double studentResumeUploadRate;         // studentsWithAnyResume / totalStudents * 100
    private double recruiterProfileCompletionRate;  // recruitersCompletedProfile / totalRecruiters * 100

    public AdminOverviewKPI() {}

    // --- Getters ---

    public int getTotalStudents() { return totalStudents; }
    public int getTotalRecruiters() { return totalRecruiters; }
    public int getTotalAdmins() { return totalAdmins; }
    public int getTotalUsers() { return totalUsers; }
    public int getStudentsCompletedProfile() { return studentsCompletedProfile; }
    public int getStudentsWithAnyResume() { return studentsWithAnyResume; }
    public int getStudentsEverUploadedResume() { return studentsEverUploadedResume; }
    public int getStudentsWithActivePdfResume() { return studentsWithActivePdfResume; }
    public int getRecruitersCompletedProfile() { return recruitersCompletedProfile; }
    public double getStudentProfileCompletionRate() { return studentProfileCompletionRate; }
    public double getStudentResumeUploadRate() { return studentResumeUploadRate; }
    public double getRecruiterProfileCompletionRate() { return recruiterProfileCompletionRate; }

    // --- Setters ---

    public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }
    public void setTotalRecruiters(int totalRecruiters) { this.totalRecruiters = totalRecruiters; }
    public void setTotalAdmins(int totalAdmins) { this.totalAdmins = totalAdmins; }
    public void setTotalUsers(int totalUsers) { this.totalUsers = totalUsers; }
    public void setStudentsCompletedProfile(int studentsCompletedProfile) { this.studentsCompletedProfile = studentsCompletedProfile; }
    public void setStudentsWithAnyResume(int studentsWithAnyResume) {
        this.studentsWithAnyResume = studentsWithAnyResume;
    }
    public void setStudentsEverUploadedResume(int studentsEverUploadedResume) { this.studentsEverUploadedResume = studentsEverUploadedResume; }
    public void setStudentsWithActivePdfResume(int studentsWithActivePdfResume) { this.studentsWithActivePdfResume = studentsWithActivePdfResume; }
    public void setRecruitersCompletedProfile(int recruitersCompletedProfile) { this.recruitersCompletedProfile = recruitersCompletedProfile; }
    public void setStudentProfileCompletionRate(double studentProfileCompletionRate) { this.studentProfileCompletionRate = studentProfileCompletionRate; }
    public void setStudentResumeUploadRate(double studentResumeUploadRate) { this.studentResumeUploadRate = studentResumeUploadRate; }
    public void setRecruiterProfileCompletionRate(double recruiterProfileCompletionRate) { this.recruiterProfileCompletionRate = recruiterProfileCompletionRate; }

    @Override
    public String toString() {
        return "AdminOverviewKPI{students=" + totalStudents + ", recruiters=" + totalRecruiters
                + ", admins=" + totalAdmins + ", profileComplete=" + studentsCompletedProfile
                + ", activeResume=" + studentsWithActivePdfResume + "}";
    }
}

