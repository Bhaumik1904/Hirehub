package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * RecruiterProfile — extended company profile stored in recruiter_profiles table.
 *
 * <p>One-to-one with {@link User} (role=RECRUITER) via user_id.</p>
 * <p>Includes Phase 6 Step 4.2 account verification governance fields.</p>
 */
public class RecruiterProfile {

    private int                         id;
    private int                         userId;
    private String                      companyName;
    private String                      companyWebsite;
    private String                      companyDescription;
    private String                      industry;
    private RecruiterVerificationStatus verificationStatus;
    private String                      verificationReason;
    private LocalDateTime               verifiedAt;
    private Integer                     verifiedBy;
    private LocalDateTime               updatedAt;

    // Optional user metadata populated during administrative joins
    private String                      recruiterName;
    private String                      recruiterEmail;
    private Boolean                     userActive;
    private LocalDateTime               userCreatedAt;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public RecruiterProfile() {}

    public RecruiterProfile(int userId) {
        this.userId = userId;
    }

    public RecruiterProfile(int userId, String companyName, String companyWebsite,
                            String companyDescription, String industry) {
        this.userId             = userId;
        this.companyName        = companyName;
        this.companyWebsite     = companyWebsite;
        this.companyDescription = companyDescription;
        this.industry           = industry;
    }

    /**
     * Full constructor including Step 4.2 verification governance attributes.
     */
    public RecruiterProfile(int id, int userId, String companyName, String companyWebsite,
                            String companyDescription, String industry,
                            RecruiterVerificationStatus verificationStatus, String verificationReason,
                            LocalDateTime verifiedAt, Integer verifiedBy, LocalDateTime updatedAt) {
        this.id                  = id;
        this.userId              = userId;
        this.companyName         = companyName;
        this.companyWebsite      = companyWebsite;
        this.companyDescription  = companyDescription;
        this.industry            = industry;
        this.verificationStatus  = verificationStatus;
        this.verificationReason  = verificationReason;
        this.verifiedAt          = verifiedAt;
        this.verifiedBy          = verifiedBy;
        this.updatedAt           = updatedAt;
    }

    // -----------------------------------------------------------------------
    // Getters & Setters
    // -----------------------------------------------------------------------

    public int    getId()                                   { return id; }
    public void   setId(int id)                             { this.id = id; }

    public int    getUserId()                               { return userId; }
    public void   setUserId(int userId)                     { this.userId = userId; }

    public String getCompanyName()                          { return companyName; }
    public void   setCompanyName(String name)               { this.companyName = name; }

    public String getCompanyWebsite()                       { return companyWebsite; }
    public void   setCompanyWebsite(String website)         { this.companyWebsite = website; }

    public String getCompanyDescription()                   { return companyDescription; }
    public void   setCompanyDescription(String desc)        { this.companyDescription = desc; }

    public String getIndustry()                             { return industry; }
    public void   setIndustry(String industry)              { this.industry = industry; }

    public RecruiterVerificationStatus getVerificationStatus() { return verificationStatus; }
    public void   setVerificationStatus(RecruiterVerificationStatus status) { this.verificationStatus = status; }

    public String getVerificationReason()                   { return verificationReason; }
    public void   setVerificationReason(String reason)      { this.verificationReason = reason; }

    public LocalDateTime getVerifiedAt()                     { return verifiedAt; }
    public void   setVerifiedAt(LocalDateTime verifiedAt)   { this.verifiedAt = verifiedAt; }

    public Integer getVerifiedBy()                          { return verifiedBy; }
    public void   setVerifiedBy(Integer verifiedBy)         { this.verifiedBy = verifiedBy; }

    public LocalDateTime getUpdatedAt()                     { return updatedAt; }
    public void   setUpdatedAt(LocalDateTime dt)            { this.updatedAt = dt; }

    public String getRecruiterName()                        { return recruiterName; }
    public void   setRecruiterName(String name)             { this.recruiterName = name; }

    public String getRecruiterEmail()                       { return recruiterEmail; }
    public void   setRecruiterEmail(String email)           { this.recruiterEmail = email; }

    public Boolean isUserActive()                           { return userActive; }
    public void   setUserActive(Boolean active)             { this.userActive = active; }

    public LocalDateTime getUserCreatedAt()                  { return userCreatedAt; }
    public void   setUserCreatedAt(LocalDateTime dt)        { this.userCreatedAt = dt; }

    // -----------------------------------------------------------------------
    // Utility & Status Helpers
    // -----------------------------------------------------------------------

    /** True if at least company name is set. */
    public boolean isComplete() {
        return companyName != null && !companyName.isBlank();
    }

    /**
     * Returns true only if the recruiter profile has been verified by an administrator.
     * Null verification status evaluates safely to false.
     */
    public boolean isVerified() {
        return verificationStatus == RecruiterVerificationStatus.VERIFIED;
    }

    @Override
    public String toString() {
        return "RecruiterProfile{" +
                "id=" + id +
                ", userId=" + userId +
                ", companyName='" + companyName + '\'' +
                ", verificationStatus=" + verificationStatus +
                '}';
    }
}
