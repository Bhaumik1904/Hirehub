package com.hirehub.model;

import java.time.LocalDateTime;

/**
 * Resume — Represents an uploaded student resume document.
 * Maps to the `resumes` table.
 */
public class Resume {

    private int           id;
    private int           studentId;
    private String        fileName;
    private String        storedPath;
    private long          fileSizeBytes;
    private String        fileHash;
    private String        parsedText;
    private String        parsedJson;
    private boolean       isActive;
    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;

    public Resume() {
        this.isActive = true;
    }

    public Resume(int studentId, String fileName, String storedPath, long fileSizeBytes,
                  String fileHash, String parsedText, String parsedJson) {
        this.studentId     = studentId;
        this.fileName      = fileName;
        this.storedPath    = storedPath;
        this.fileSizeBytes = fileSizeBytes;
        this.fileHash      = fileHash;
        this.parsedText    = parsedText;
        this.parsedJson    = parsedJson;
        this.isActive      = true;
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public int getId()                         { return id; }
    public void setId(int id)                  { this.id = id; }

    public int getStudentId()                  { return studentId; }
    public void setStudentId(int studentId)    { this.studentId = studentId; }

    public String getFileName()                { return fileName; }
    public void setFileName(String fileName)   { this.fileName = fileName; }

    public String getStoredPath()              { return storedPath; }
    public void setStoredPath(String path)     { this.storedPath = path; }

    public long getFileSizeBytes()             { return fileSizeBytes; }
    public void setFileSizeBytes(long size)    { this.fileSizeBytes = size; }

    public String getFileHash()                { return fileHash; }
    public void setFileHash(String fileHash)   { this.fileHash = fileHash; }

    public String getParsedText()              { return parsedText; }
    public void setParsedText(String text)     { this.parsedText = text; }

    public String getParsedJson()              { return parsedJson; }
    public void setParsedJson(String json)     { this.parsedJson = json; }

    public boolean isActive()                  { return isActive; }
    public void setActive(boolean active)      { isActive = active; }

    public LocalDateTime getUploadedAt()          { return uploadedAt; }
    public void setUploadedAt(LocalDateTime dt)   { this.uploadedAt = dt; }

    public LocalDateTime getUpdatedAt()          { return updatedAt; }
    public void setUpdatedAt(LocalDateTime dt)   { this.updatedAt = dt; }

    public String getFormattedSize() {
        if (fileSizeBytes < 1024) return fileSizeBytes + " B";
        if (fileSizeBytes < 1024 * 1024) return String.format("%.1f KB", fileSizeBytes / 1024.0);
        return String.format("%.2f MB", fileSizeBytes / (1024.0 * 1024.0));
    }

    public String getShortHash() {
        if (fileHash == null || fileHash.length() < 8) return "—";
        return fileHash.substring(0, 8);
    }
}

