package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * Application — represents a student's job application stored in the applications table.
 */
public class Application {

    public enum Status {
        PENDING, SHORTLISTED, REJECTED, SELECTED;

        public String label() {
            return switch (this) {
                case PENDING     -> "Pending";
                case SHORTLISTED -> "Shortlisted";
                case REJECTED    -> "Rejected";
                case SELECTED    -> "Selected";
            };
        }

        public static Status fromString(String s) {
            if (s == null) return PENDING;
            return switch (s.toUpperCase()) {
                case "SHORTLISTED" -> SHORTLISTED;
                case "REJECTED"    -> REJECTED;
                case "SELECTED"    -> SELECTED;
                default            -> PENDING;
            };
        }
    }

    private int           id;
    private int           jobId;
    private int           studentId;
    private Integer       resumeId;
    private String        coverLetter;
    private Status        status;
    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;

    // ── Joined fields (not in applications table — loaded via JOIN) ──────────
    private String jobTitle;
    private String companyName;
    private String jobType;
    private String location;
    private String studentName;
    private String studentEmail;
    private String studentCollege;
    private String studentDegree;
    private int    studentGradYear;
    private String studentSkills;

    public Application() {
        this.status = Status.PENDING;
    }

    public Application(int jobId, int studentId, String coverLetter) {
        this(jobId, studentId, null, coverLetter);
    }

    public Application(int jobId, int studentId, Integer resumeId, String coverLetter) {
        this.jobId = jobId;
        this.studentId = studentId;
        this.resumeId = resumeId;
        this.coverLetter = coverLetter;
        this.status = Status.PENDING;
    }

    public Application(int id, int jobId, int studentId, String coverLetter, Status status) {
        this(id, jobId, studentId, null, coverLetter, status);
    }

    public Application(int id, int jobId, int studentId, Integer resumeId, String coverLetter, Status status) {
        this.id = id;
        this.jobId = jobId;
        this.studentId = studentId;
        this.resumeId = resumeId;
        this.coverLetter = coverLetter;
        this.status = status != null ? status : Status.PENDING;
    }

    // Getters & Setters
    public int           getId()                        { return id; }
    public void          setId(int id)                  { this.id = id; }

    public int           getJobId()                     { return jobId; }
    public void          setJobId(int jobId)            { this.jobId = jobId; }

    public int           getStudentId()                 { return studentId; }
    public void          setStudentId(int sid)          { this.studentId = sid; }

    public Integer       getResumeId()                  { return resumeId; }
    public void          setResumeId(Integer rid)       { this.resumeId = rid; }

    public String        getCoverLetter()               { return coverLetter; }
    public void          setCoverLetter(String cl)      { this.coverLetter = cl; }

    public Status        getStatus()                    { return status; }
    public void          setStatus(Status s)            { this.status = s; }

    public LocalDateTime getAppliedAt()                 { return appliedAt; }
    public void          setAppliedAt(LocalDateTime dt) { this.appliedAt = dt; }

    public LocalDateTime getUpdatedAt()                 { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime dt) { this.updatedAt = dt; }

    public String        getJobTitle()                  { return jobTitle; }
    public void          setJobTitle(String t)          { this.jobTitle = t; }

    public String        getCompanyName()               { return companyName; }
    public void          setCompanyName(String c)       { this.companyName = c; }

    public String        getJobType()                   { return jobType; }
    public void          setJobType(String jt)          { this.jobType = jt; }

    public String        getLocation()                  { return location; }
    public void          setLocation(String loc)        { this.location = loc; }

    public String        getStudentName()               { return studentName; }
    public void          setStudentName(String n)       { this.studentName = n; }

    public String        getStudentEmail()              { return studentEmail; }
    public void          setStudentEmail(String e)      { this.studentEmail = e; }

    public String        getStudentCollege()            { return studentCollege; }
    public void          setStudentCollege(String c)    { this.studentCollege = c; }

    public String        getStudentDegree()             { return studentDegree; }
    public void          setStudentDegree(String d)     { this.studentDegree = d; }

    public int           getStudentGradYear()           { return studentGradYear; }
    public void          setStudentGradYear(int y)      { this.studentGradYear = y; }

    public String        getStudentSkills()             { return studentSkills; }
    public void          setStudentSkills(String s)     { this.studentSkills = s; }
}
