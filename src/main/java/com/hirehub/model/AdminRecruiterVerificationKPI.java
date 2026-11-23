package com.hirehub.model;

/**
 * Data Transfer Object representing aggregate live metrics for recruiter profile verification.
 */
public class AdminRecruiterVerificationKPI {

    private int totalRecruiters;
    private int pendingCount;
    private int verifiedCount;
    private int rejectedCount;
    private int suspendedCount;
    private int unverifiedCount;

    public AdminRecruiterVerificationKPI() {}

    public AdminRecruiterVerificationKPI(int totalRecruiters, int pendingCount, int verifiedCount,
                                         int rejectedCount, int suspendedCount, int unverifiedCount) {
        this.totalRecruiters = totalRecruiters;
        this.pendingCount    = pendingCount;
        this.verifiedCount   = verifiedCount;
        this.rejectedCount   = rejectedCount;
        this.suspendedCount  = suspendedCount;
        this.unverifiedCount = unverifiedCount;
    }

    public int getTotalRecruiters() { return totalRecruiters; }
    public void setTotalRecruiters(int totalRecruiters) { this.totalRecruiters = totalRecruiters; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getVerifiedCount() { return verifiedCount; }
    public void setVerifiedCount(int verifiedCount) { this.verifiedCount = verifiedCount; }

    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    public int getSuspendedCount() { return suspendedCount; }
    public void setSuspendedCount(int suspendedCount) { this.suspendedCount = suspendedCount; }

    public int getUnverifiedCount() { return unverifiedCount; }
    public void setUnverifiedCount(int unverifiedCount) { this.unverifiedCount = unverifiedCount; }

    @Override
    public String toString() {
        return "AdminRecruiterVerificationKPI{" +
                "totalRecruiters=" + totalRecruiters +
                ", pendingCount=" + pendingCount +
                ", verifiedCount=" + verifiedCount +
                ", rejectedCount=" + rejectedCount +
                ", suspendedCount=" + suspendedCount +
                ", unverifiedCount=" + unverifiedCount +
                '}';
    }
}
