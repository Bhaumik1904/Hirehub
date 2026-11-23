package com.hirehub.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Job — represents a job listing stored in the jobs table.
 *
 * <p>Created by a Recruiter. Students browse and apply to jobs.</p>
 * <p>Includes Phase 6 Step 4.2 administrative moderation governance attributes.</p>
 */
public class Job {

    /** Mirrors the ENUM in the jobs.job_type column. */
    public enum JobType {
        INTERNSHIP, FULL_TIME, PART_TIME;

        /** Returns a human-readable display label. */
        public String label() {
            return switch (this) {
                case INTERNSHIP -> "Internship";
                case FULL_TIME  -> "Full-Time";
                case PART_TIME  -> "Part-Time";
            };
        }

        /** Parse a display label back to enum (null-safe). */
        public static JobType fromLabel(String label) {
            if (label == null) return FULL_TIME;
            return switch (label.trim()) {
                case "Internship" -> INTERNSHIP;
                case "Part-Time"  -> PART_TIME;
                default           -> FULL_TIME;
            };
        }
    }

    /** Mirrors the ENUM in the jobs.status column representing the recruiter lifecycle. */
    public enum Status {
        ACTIVE, DRAFT, CLOSED;

        public String label() {
            return switch (this) {
                case ACTIVE -> "Active";
                case DRAFT  -> "Draft";
                case CLOSED -> "Closed";
            };
        }
    }

    private int                 id;
    private int                 recruiterId;      // FK → users.id
    private String              title;
    private String              companyName;
    private String              description;
    private String              requiredSkills;   // comma-separated
    private String              location;
    private JobType             jobType;
    private String              salaryRange;      // optional free-text
    private String              eligibility;
    private LocalDate           deadline;
    private Status              status;           // Recruiter lifecycle status
    private JobModerationStatus moderationStatus; // Admin moderation status (independent of status)
    private String              moderationReason;
    private LocalDateTime       moderatedAt;
    private Integer             moderatedBy;      // Admin user id
    private LocalDateTime       createdAt;

    // Optional recruiter metadata populated during administrative joins
    private String              recruiterName;
    private String              recruiterEmail;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public Job() {}

    /** Convenience constructor for creating a new job before DB insertion. */
    public Job(int recruiterId, String title, String companyName,
               String description, String requiredSkills, String location,
               JobType jobType, String salaryRange, String eligibility,
               LocalDate deadline, Status status) {
        this.recruiterId    = recruiterId;
        this.title          = title;
        this.companyName    = companyName;
        this.description    = description;
        this.requiredSkills = requiredSkills;
        this.location       = location;
        this.jobType        = jobType;
        this.salaryRange    = salaryRange;
        this.eligibility    = eligibility;
        this.deadline       = deadline;
        this.status         = status;
    }

    /**
     * Overloaded constructor including moderation status.
     */
    public Job(int recruiterId, String title, String companyName,
               String description, String requiredSkills, String location,
               JobType jobType, String salaryRange, String eligibility,
               LocalDate deadline, Status status, JobModerationStatus moderationStatus) {
        this(recruiterId, title, companyName, description, requiredSkills, location,
             jobType, salaryRange, eligibility, deadline, status);
        this.moderationStatus = moderationStatus;
    }

    /**
     * Full constructor including all database columns and moderation attributes.
     */
    public Job(int id, int recruiterId, String title, String companyName,
               String description, String requiredSkills, String location,
               JobType jobType, String salaryRange, String eligibility,
               LocalDate deadline, Status status, JobModerationStatus moderationStatus,
               String moderationReason, LocalDateTime moderatedAt, Integer moderatedBy,
               LocalDateTime createdAt) {
        this(recruiterId, title, companyName, description, requiredSkills, location,
             jobType, salaryRange, eligibility, deadline, status);
        this.id               = id;
        this.moderationStatus = moderationStatus;
        this.moderationReason = moderationReason;
        this.moderatedAt      = moderatedAt;
        this.moderatedBy      = moderatedBy;
        this.createdAt        = createdAt;
    }

    // -----------------------------------------------------------------------
    // Getters & Setters
    // -----------------------------------------------------------------------

    public int                 getId()                              { return id; }
    public void                setId(int id)                        { this.id = id; }

    public int                 getRecruiterId()                     { return recruiterId; }
    public void                setRecruiterId(int id)               { this.recruiterId = id; }

    public String              getTitle()                           { return title; }
    public void                setTitle(String title)               { this.title = title; }

    public String              getCompanyName()                     { return companyName; }
    public void                setCompanyName(String name)          { this.companyName = name; }

    public String              getDescription()                     { return description; }
    public void                setDescription(String desc)          { this.description = desc; }

    public String              getRequiredSkills()                  { return requiredSkills; }
    public void                setRequiredSkills(String s)          { this.requiredSkills = s; }

    public String              getLocation()                        { return location; }
    public void                setLocation(String loc)              { this.location = loc; }

    public JobType             getJobType()                         { return jobType; }
    public void                setJobType(JobType t)                { this.jobType = t; }

    public String              getSalaryRange()                     { return salaryRange; }
    public void                setSalaryRange(String s)             { this.salaryRange = s; }

    public String              getEligibility()                     { return eligibility; }
    public void                setEligibility(String e)             { this.eligibility = e; }

    public LocalDate           getDeadline()                        { return deadline; }
    public void                setDeadline(LocalDate d)             { this.deadline = d; }

    public Status              getStatus()                          { return status; }
    public void                setStatus(Status s)                  { this.status = s; }

    public JobModerationStatus getModerationStatus()                { return moderationStatus; }
    public void                setModerationStatus(JobModerationStatus moderationStatus) { this.moderationStatus = moderationStatus; }

    public String              getModerationReason()                { return moderationReason; }
    public void                setModerationReason(String reason)   { this.moderationReason = reason; }

    public LocalDateTime       getModeratedAt()                     { return moderatedAt; }
    public void                setModeratedAt(LocalDateTime dt)     { this.moderatedAt = dt; }

    public Integer             getModeratedBy()                     { return moderatedBy; }
    public void                setModeratedBy(Integer adminId)      { this.moderatedBy = adminId; }

    public LocalDateTime       getCreatedAt()                       { return createdAt; }
    public void                setCreatedAt(LocalDateTime dt)       { this.createdAt = dt; }

    public String              getRecruiterName()                   { return recruiterName; }
    public void                setRecruiterName(String name)        { this.recruiterName = name; }

    public String              getRecruiterEmail()                  { return recruiterEmail; }
    public void                setRecruiterEmail(String email)      { this.recruiterEmail = email; }

    // -----------------------------------------------------------------------
    // Helper Methods
    // -----------------------------------------------------------------------

    /**
     * Returns true if the job posting has received administrative approval.
     * Null moderation status evaluates safely to false.
     */
    public boolean isApproved() {
        return moderationStatus == JobModerationStatus.APPROVED;
    }

    @Override
    public String toString() {
        return "Job{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", moderationStatus=" + moderationStatus +
                '}';
    }
}
