package com.hirehub.model;

/**
 * Represents a Recruiter user profile.
 * Extends the base User entity with recruiter/company-specific metadata.
 * Future phases will add posted jobs, hiring pipeline, etc.
 */
public class Recruiter extends User {

    private String companyName;
    private String companyWebsite;
    private String designation;
    private String industry;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public Recruiter() {
        super();
        setRole(Role.RECRUITER);
    }

    public Recruiter(String fullName, String email, String passwordHash) {
        super(fullName, email, passwordHash, Role.RECRUITER);
    }

    // -----------------------------------------------------------------------
    // Getters and Setters
    // -----------------------------------------------------------------------

    public String getCompanyName()                      { return companyName; }
    public void   setCompanyName(String name)           { this.companyName = name; }

    public String getCompanyWebsite()                   { return companyWebsite; }
    public void   setCompanyWebsite(String website)     { this.companyWebsite = website; }

    public String getDesignation()                      { return designation; }
    public void   setDesignation(String designation)    { this.designation = designation; }

    public String getIndustry()                         { return industry; }
    public void   setIndustry(String industry)          { this.industry = industry; }
}
