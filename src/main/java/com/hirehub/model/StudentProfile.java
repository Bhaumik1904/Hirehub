package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * StudentProfile — represents the extended profile stored in student_profiles table.
 *
 * <p>Has a one-to-one relationship with {@link User} (role=STUDENT) via user_id.
 * The User object (name, email) is managed separately in the users table.</p>
 */
public class StudentProfile {

    private int           id;
    private int           userId;
    private String        phone;
    private String        college;
    private String        degree;
    private int           graduationYear;
    private String        skills;        // comma-separated
    private String        resumePath;
    private LocalDateTime updatedAt;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public StudentProfile() {}

    public StudentProfile(int userId) {
        this.userId = userId;
    }

    public StudentProfile(int userId, String phone, String college, String degree,
                          int graduationYear, String skills, String resumePath) {
        this.userId         = userId;
        this.phone          = phone;
        this.college        = college;
        this.degree         = degree;
        this.graduationYear = graduationYear;
        this.skills         = skills;
        this.resumePath     = resumePath;
    }

    // -----------------------------------------------------------------------
    // Getters & Setters
    // -----------------------------------------------------------------------

    public int    getId()                           { return id; }
    public void   setId(int id)                     { this.id = id; }

    public int    getUserId()                       { return userId; }
    public void   setUserId(int userId)             { this.userId = userId; }

    public String getPhone()                        { return phone; }
    public void   setPhone(String phone)            { this.phone = phone; }

    public String getCollege()                      { return college; }
    public void   setCollege(String college)        { this.college = college; }

    public String getDegree()                       { return degree; }
    public void   setDegree(String degree)          { this.degree = degree; }

    public int    getGraduationYear()               { return graduationYear; }
    public void   setGraduationYear(int year)       { this.graduationYear = year; }

    public String getSkills()                       { return skills; }
    public void   setSkills(String skills)          { this.skills = skills; }

    public String getResumePath()                   { return resumePath; }
    public void   setResumePath(String path)        { this.resumePath = path; }

    public LocalDateTime getUpdatedAt()             { return updatedAt; }
    public void   setUpdatedAt(LocalDateTime dt)    { this.updatedAt = dt; }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /** Returns true if at least the required profile fields are filled. */
    public boolean isComplete() {
        return college != null && !college.isBlank()
            && degree  != null && !degree.isBlank()
            && graduationYear > 2000;
    }
}
