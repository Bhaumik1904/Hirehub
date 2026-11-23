package com.hirehub.model;

/**
 * Data Transfer Object representing aggregate live metrics for job moderation and lifecycle.
 */
public class AdminJobModerationKPI {

    private int totalJobs;
    private int pendingCount;
    private int approvedCount;
    private int rejectedCount;
    private int suspendedCount;
    private int activeLifecycleCount;

    public AdminJobModerationKPI() {}

    public AdminJobModerationKPI(int totalJobs, int pendingCount, int approvedCount,
                                 int rejectedCount, int suspendedCount, int activeLifecycleCount) {
        this.totalJobs            = totalJobs;
        this.pendingCount         = pendingCount;
        this.approvedCount        = approvedCount;
        this.rejectedCount        = rejectedCount;
        this.suspendedCount       = suspendedCount;
        this.activeLifecycleCount = activeLifecycleCount;
    }

    public int getTotalJobs() { return totalJobs; }
    public void setTotalJobs(int totalJobs) { this.totalJobs = totalJobs; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getApprovedCount() { return approvedCount; }
    public void setApprovedCount(int approvedCount) { this.approvedCount = approvedCount; }

    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    public int getSuspendedCount() { return suspendedCount; }
    public void setSuspendedCount(int suspendedCount) { this.suspendedCount = suspendedCount; }

    public int getActiveLifecycleCount() { return activeLifecycleCount; }
    public void setActiveLifecycleCount(int activeLifecycleCount) { this.activeLifecycleCount = activeLifecycleCount; }

    @Override
    public String toString() {
        return "AdminJobModerationKPI{" +
                "totalJobs=" + totalJobs +
                ", pendingCount=" + pendingCount +
                ", approvedCount=" + approvedCount +
                ", rejectedCount=" + rejectedCount +
                ", suspendedCount=" + suspendedCount +
                ", activeLifecycleCount=" + activeLifecycleCount +
                '}';
    }
}
