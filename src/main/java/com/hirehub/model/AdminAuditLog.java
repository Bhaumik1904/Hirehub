package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * Represents an audit log entry recorded when an administrator performs
 * governance, account moderation, recruiter verification, or job moderation actions.
 *
 * <p>Includes Phase 6 Step 4.2 entity typing and target job reference.</p>
 */
public class AdminAuditLog {

    private int                  id;
    private AdminAuditEntityType entityType = AdminAuditEntityType.USER;
    private int                  adminId;
    private String               adminName;
    private String               adminEmail;
    private Integer              targetUserId;
    private String               targetUserName;
    private String               targetUserEmail;
    private Integer              targetJobId;
    private String               action;
    private String               reason;
    private LocalDateTime        createdAt;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public AdminAuditLog() {
        this.entityType = AdminAuditEntityType.USER;
    }

    /**
     * Backward-compatible convenience constructor for user-focused audit logs.
     */
    public AdminAuditLog(int adminId, int targetUserId, String action, String reason) {
        this.entityType   = AdminAuditEntityType.USER;
        this.adminId      = adminId;
        this.targetUserId = targetUserId;
        this.action       = action;
        this.reason       = reason;
        this.createdAt    = LocalDateTime.now();
    }

    /**
     * Backward-compatible full constructor for user-focused audit logs.
     */
    public AdminAuditLog(int id, int adminId, String adminName, String adminEmail,
                         int targetUserId, String targetUserName, String targetUserEmail,
                         String action, String reason, LocalDateTime createdAt) {
        this.id              = id;
        this.entityType      = AdminAuditEntityType.USER;
        this.adminId         = adminId;
        this.adminName       = adminName;
        this.adminEmail      = adminEmail;
        this.targetUserId    = targetUserId;
        this.targetUserName  = targetUserName;
        this.targetUserEmail = targetUserEmail;
        this.action          = action;
        this.reason          = reason;
        this.createdAt       = createdAt;
    }

    /**
     * Overloaded constructor supporting multi-entity audit actions (USER, RECRUITER_PROFILE, JOB).
     */
    public AdminAuditLog(AdminAuditEntityType entityType, int adminId, Integer targetUserId,
                         Integer targetJobId, String action, String reason) {
        this.entityType   = entityType != null ? entityType : AdminAuditEntityType.USER;
        this.adminId      = adminId;
        this.targetUserId = targetUserId;
        this.targetJobId  = targetJobId;
        this.action       = action;
        this.reason       = reason;
        this.createdAt    = LocalDateTime.now();
    }

    /**
     * Full constructor including entityType and targetJobId.
     */
    public AdminAuditLog(int id, AdminAuditEntityType entityType, int adminId, String adminName,
                         String adminEmail, Integer targetUserId, String targetUserName,
                         String targetUserEmail, Integer targetJobId, String action,
                         String reason, LocalDateTime createdAt) {
        this.id              = id;
        this.entityType      = entityType != null ? entityType : AdminAuditEntityType.USER;
        this.adminId         = adminId;
        this.adminName       = adminName;
        this.adminEmail      = adminEmail;
        this.targetUserId    = targetUserId;
        this.targetUserName  = targetUserName;
        this.targetUserEmail = targetUserEmail;
        this.targetJobId     = targetJobId;
        this.action          = action;
        this.reason          = reason;
        this.createdAt       = createdAt;
    }

    // -----------------------------------------------------------------------
    // Getters & Setters
    // -----------------------------------------------------------------------

    public int getId()                                      { return id; }
    public void setId(int id)                               { this.id = id; }

    public AdminAuditEntityType getEntityType()             { return entityType; }
    public void setEntityType(AdminAuditEntityType type)    { this.entityType = type; }

    public int getAdminId()                                 { return adminId; }
    public void setAdminId(int adminId)                     { this.adminId = adminId; }

    public String getAdminName()                            { return adminName; }
    public void setAdminName(String adminName)              { this.adminName = adminName; }

    public String getAdminEmail()                           { return adminEmail; }
    public void setAdminEmail(String adminEmail)            { this.adminEmail = adminEmail; }

    public Integer getTargetUserId()                        { return targetUserId; }
    public void setTargetUserId(Integer targetUserId)       { this.targetUserId = targetUserId; }
    public void setTargetUserId(int targetUserId)           { this.targetUserId = targetUserId; }

    public String getTargetUserName()                       { return targetUserName; }
    public void setTargetUserName(String targetUserName)    { this.targetUserName = targetUserName; }

    public String getTargetUserEmail()                      { return targetUserEmail; }
    public void setTargetUserEmail(String targetUserEmail)  { this.targetUserEmail = targetUserEmail; }

    public Integer getTargetJobId()                         { return targetJobId; }
    public void setTargetJobId(Integer targetJobId)         { this.targetJobId = targetJobId; }

    public String getAction()                               { return action; }
    public void setAction(String action)                    { this.action = action; }

    public String getReason()                               { return reason; }
    public void setReason(String reason)                    { this.reason = reason; }

    public LocalDateTime getCreatedAt()                     { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)       { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "AdminAuditLog{" +
                "id=" + id +
                ", entityType=" + entityType +
                ", adminId=" + adminId +
                ", targetUserId=" + targetUserId +
                ", targetJobId=" + targetJobId +
                ", action='" + action + '\'' +
                ", reason='" + reason + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
