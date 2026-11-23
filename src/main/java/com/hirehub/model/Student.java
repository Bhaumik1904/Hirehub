package com.hirehub.model;

/**
 * Represents a Student user profile.
 * Extends the base User entity with student-specific metadata.
 * Future phases will add college, CGPA, skills, etc.
 */
public class Student extends User {

    private String college;
    private String degree;
    private String branch;
    private int    graduationYear;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public Student() {
        super();
        setRole(Role.STUDENT);
    }

    public Student(String fullName, String email, String passwordHash) {
        super(fullName, email, passwordHash, Role.STUDENT);
    }

    // -----------------------------------------------------------------------
    // Getters and Setters
    // -----------------------------------------------------------------------

    public String getCollege()                      { return college; }
    public void   setCollege(String college)        { this.college = college; }

    public String getDegree()                       { return degree; }
    public void   setDegree(String degree)          { this.degree = degree; }

    public String getBranch()                       { return branch; }
    public void   setBranch(String branch)          { this.branch = branch; }

    public int  getGraduationYear()                 { return graduationYear; }
    public void setGraduationYear(int year)         { this.graduationYear = year; }
}
