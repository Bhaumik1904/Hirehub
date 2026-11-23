package com.hirehub.service;

import com.google.gson.Gson;
import com.hirehub.dao.ResumeDAO;
import com.hirehub.dao.StudentDAO;
import com.hirehub.model.ParsedResume;
import com.hirehub.model.Resume;
import com.hirehub.model.StudentProfile;
import com.hirehub.model.User;
import com.hirehub.util.SessionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ResumeService — Manages resume document storage, validation, parsing orchestration,
 * database persistence, and profile synchronization.
 */
public class ResumeService {

    private static final String STORAGE_DIR_NAME = "data/resumes";

    private final ResumeDAO           resumeDAO           = new ResumeDAO();
    private final StudentDAO          studentDAO          = new StudentDAO();
    private final ResumeParserService resumeParserService = new ResumeParserService();
    private final Gson                gson                = new Gson();

    public static class UploadResult {
        private final boolean      success;
        private final String       message;
        private final Resume       resume;
        private final ParsedResume parsedResume;

        public UploadResult(boolean success, String message, Resume resume, ParsedResume parsedResume) {
            this.success      = success;
            this.message      = message;
            this.resume       = resume;
            this.parsedResume = parsedResume;
        }

        public static UploadResult ok(String msg, Resume r, ParsedResume pr) {
            return new UploadResult(true, msg, r, pr);
        }

        public static UploadResult fail(String msg) {
            return new UploadResult(false, msg, null, null);
        }

        public boolean isSuccess()          { return success; }
        public String getMessage()          { return message; }
        public Resume getResume()           { return resume; }
        public ParsedResume getParsedResume(){ return parsedResume; }
    }

    private boolean isAuthorizedStudent(int studentId) {
        if (studentId <= 0) return false;
        User current = SessionManager.getInstance().getCurrentUser();
        return current != null
                && current.getRole() == User.Role.STUDENT
                && current.getId() == studentId;
    }

    // ── File Storage Directory Resolution ───────────────────────────────────

    public Path getStorageDirectory() {
        Path path = Paths.get(STORAGE_DIR_NAME).toAbsolutePath().normalize();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            System.err.println("[ResumeService] Could not create storage directory: " + e.getMessage());
        }
        return path;
    }

    // ── Resume Upload & Processing ──────────────────────────────────────────

    /**
     * Uploads, validates, stores, and parses a PDF resume for a student.
     */
    public UploadResult uploadAndProcessResume(int studentId, File sourceFile) {
        if (studentId <= 0) {
            return UploadResult.fail("Invalid student ID.");
        }
        if (!isAuthorizedStudent(studentId)) {
            return UploadResult.fail("Unauthorized: Session does not match student ID " + studentId);
        }
        // 1. Validation
        ResumeParserService.ValidationResult val = resumeParserService.validatePdfFile(sourceFile);
        if (!val.isValid()) {
            return UploadResult.fail(val.getErrorMessage());
        }

        try {
            // 2. Compute SHA-256 Hash
            String fileHash = computeSha256(sourceFile);

            // 3. Prepare target filename and destination path
            Path storageDir = getStorageDirectory();
            String originalName = sourceFile.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            String storedFileName = "student_" + studentId + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + originalName;
            Path destPath = storageDir.resolve(storedFileName);

            // Copy file to persistent local app storage
            Files.copy(sourceFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);

            // 4. Parse text & structured metadata with PDFBox
            ParsedResume parsedResume = resumeParserService.parseResume(destPath.toFile());
            String rawText = parsedResume.getRawText();
            String jsonMetadata = parsedResume.toJson(gson);

            // 5. Database persistence
            resumeDAO.deactivateAllForStudent(studentId);

            Resume resume = new Resume(
                    studentId,
                    sourceFile.getName(),
                    destPath.toString(),
                    destPath.toFile().length(),
                    fileHash,
                    rawText,
                    jsonMetadata
            );

            resumeDAO.insert(resume);

            // Also update student_profiles table resume_path column
            updateStudentProfileResumePath(studentId, destPath.toString());

            return UploadResult.ok("Resume uploaded and parsed successfully.", resume, parsedResume);

        } catch (IOException e) {
            System.err.println("[ResumeService] File I/O error during resume upload: " + e.getMessage());
            return UploadResult.fail("Could not process resume file: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("[ResumeService] Database error during resume upload: " + e.getMessage());
            return UploadResult.fail("Database error saving resume record: " + e.getMessage());
        }
    }

    // ── Queries & Retrievals ────────────────────────────────────────────────

    /**
     * Retrieves the current active resume for the student.
     */
    public Optional<Resume> getActiveResume(int studentId) {
        if (studentId <= 0 || !isAuthorizedStudent(studentId)) {
            return Optional.empty();
        }
        try {
            return resumeDAO.findActiveByStudentId(studentId);
        } catch (SQLException e) {
            System.err.println("[ResumeService] getActiveResume error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Admin-only retrieval of the current active resume for any student.
     */
    public Optional<Resume> getActiveResumeForAdmin(int studentId) {
        if (studentId <= 0 || !SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return Optional.empty();
        }
        try {
            return resumeDAO.findActiveByStudentId(studentId);
        } catch (SQLException e) {
            System.err.println("[ResumeService] getActiveResumeForAdmin error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lists all resumes for the student.
     */
    public List<Resume> getAllResumes(int studentId) {
        if (studentId <= 0 || !isAuthorizedStudent(studentId)) {
            return List.of();
        }
        try {
            return resumeDAO.findAllByStudentId(studentId);
        } catch (SQLException e) {
            System.err.println("[ResumeService] getAllResumes error: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Admin-only listing of all resumes for any student.
     */
    public List<Resume> getAllResumesForAdmin(int studentId) {
        if (studentId <= 0 || !SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return List.of();
        }
        try {
            return resumeDAO.findAllByStudentId(studentId);
        } catch (SQLException e) {
            System.err.println("[ResumeService] getAllResumesForAdmin error: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Converts a Resume model's JSON string to a ParsedResume object.
     */
    public ParsedResume parseJsonMetadata(Resume resume) {
        if (resume == null || resume.getParsedJson() == null || resume.getParsedJson().isBlank()) {
            // Fall back to parsing raw text if available
            if (resume != null && resume.getParsedText() != null && !resume.getParsedText().isBlank()) {
                return resumeParserService.parseExtractedText(resume.getParsedText());
            }
            return new ParsedResume();
        }
        return ParsedResume.fromJson(resume.getParsedJson(), gson);
    }

    // ── Profile Synchronization ─────────────────────────────────────────────

    /**
     * Syncs extracted resume data (skills, college, degree, graduation year)
     * back into the student's profile in the `student_profiles` table.
     */
    public boolean syncWithStudentProfile(int studentId, ParsedResume parsed) {
        if (studentId <= 0 || parsed == null || !isAuthorizedStudent(studentId)) return false;

        try {
            Optional<StudentProfile> existingOpt = studentDAO.findByUserId(studentId);
            StudentProfile profile = existingOpt.orElse(new StudentProfile(studentId));

            if (parsed.getCollege() != null && !parsed.getCollege().isBlank()) {
                profile.setCollege(parsed.getCollege());
            }
            if (parsed.getDegree() != null && !parsed.getDegree().isBlank()) {
                profile.setDegree(parsed.getDegree());
            }
            if (parsed.getGraduationYear() > 0) {
                profile.setGraduationYear(parsed.getGraduationYear());
            }
            if (parsed.getPhone() != null && !parsed.getPhone().isBlank() && (profile.getPhone() == null || profile.getPhone().isBlank())) {
                profile.setPhone(parsed.getPhone());
            }

            // Merge skills
            Set<String> allSkills = new LinkedHashSet<>();
            if (profile.getSkills() != null && !profile.getSkills().isBlank()) {
                for (String s : profile.getSkills().split("[,;]+")) {
                    String tr = s.trim();
                    if (!tr.isEmpty()) allSkills.add(tr);
                }
            }
            if (parsed.getSkills() != null) {
                allSkills.addAll(parsed.getSkills());
            }
            profile.setSkills(String.join(", ", allSkills));

            studentDAO.saveOrUpdate(profile);
            return true;

        } catch (SQLException e) {
            System.err.println("[ResumeService] syncWithStudentProfile error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resolves and validates a resume file from its stored path.
     * Enforces that the file resides strictly within the authorized storage directory
     * (prevents path traversal like `..`), exists, and is a readable regular file.
     */
    public Optional<File> resolveAndValidateResumeFile(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return Optional.empty();
        }
        // Fast reject path traversal attempts
        if (storedPath.contains("..")) {
            System.err.println("[ResumeService] Path traversal rejected: " + storedPath);
            return Optional.empty();
        }
        try {
            Path storageDir = getStorageDirectory().toRealPath();
            Path rawPath = Paths.get(storedPath);
            Path targetPath;
            if (rawPath.isAbsolute()) {
                targetPath = rawPath.normalize();
            } else {
                targetPath = storageDir.resolve(rawPath).normalize();
            }

            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath) || !Files.isReadable(targetPath)) {
                return Optional.empty();
            }

            Path realTarget = targetPath.toRealPath();
            if (!realTarget.startsWith(storageDir)) {
                System.err.println("[ResumeService] Target file is outside storage directory: " + realTarget);
                return Optional.empty();
            }

            return Optional.of(realTarget.toFile());
        } catch (IOException e) {
            System.err.println("[ResumeService] resolveAndValidateResumeFile error: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Checks if a resume is currently referenced by any submitted application.
     */
    public boolean isResumeReferencedByApplication(int resumeId) {
        if (resumeId <= 0) return false;
        try {
            return resumeDAO.isReferencedByAnyApplication(resumeId);
        } catch (SQLException e) {
            System.err.println("[ResumeService] isResumeReferencedByApplication error: " + e.getMessage());
            return true; // Fail safe
        }
    }

    // ── Resume Deletion ─────────────────────────────────────────────────────

    /**
     * Deletes a resume from disk and database atomically verifying student ownership.
     * Rejects deletion if the resume is referenced by any job application (snapshot preservation).
     */
    public boolean deleteResume(int resumeId, int studentId) {
        if (resumeId <= 0 || studentId <= 0 || !isAuthorizedStudent(studentId)) {
            return false;
        }
        if (isResumeReferencedByApplication(resumeId)) {
            System.err.println("[ResumeService] Cannot delete resume #" + resumeId + ": Referenced by an application.");
            return false;
        }
        try {
            Optional<Resume> opt = resumeDAO.findByIdForStudent(resumeId, studentId);
            if (opt.isPresent()) {
                Resume r = opt.get();
                // Try to delete physical file
                if (r.getStoredPath() != null) {
                    try {
                        Files.deleteIfExists(Paths.get(r.getStoredPath()));
                    } catch (IOException ignored) {}
                }
                boolean deleted = resumeDAO.deleteForStudent(resumeId, studentId);
                if (deleted) {
                    clearStudentProfileResumePath(studentId, r.getStoredPath());
                }
                return deleted;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("[ResumeService] deleteResume error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Admin-only resume deletion verifying student ownership atomically.
     * Rejects deletion if the resume is referenced by any job application (snapshot preservation).
     */
    public boolean deleteResumeForAdmin(int resumeId, int studentId) {
        if (resumeId <= 0 || studentId <= 0 || !SessionManager.getInstance().hasRole(User.Role.ADMIN)) {
            return false;
        }
        if (isResumeReferencedByApplication(resumeId)) {
            System.err.println("[ResumeService] Admin cannot delete resume #" + resumeId + ": Referenced by an application.");
            return false;
        }
        try {
            Optional<Resume> opt = resumeDAO.findByIdForStudent(resumeId, studentId);
            if (opt.isPresent()) {
                Resume r = opt.get();
                if (r.getStoredPath() != null) {
                    try {
                        Files.deleteIfExists(Paths.get(r.getStoredPath()));
                    } catch (IOException ignored) {}
                }
                boolean deleted = resumeDAO.deleteForStudent(resumeId, studentId);
                if (deleted) {
                    clearStudentProfileResumePath(studentId, r.getStoredPath());
                }
                return deleted;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("[ResumeService] deleteResumeForAdmin error: " + e.getMessage());
            return false;
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private void clearStudentProfileResumePath(int studentId, String path) {
        try {
            Optional<StudentProfile> opt = studentDAO.findByUserId(studentId);
            if (opt.isPresent()) {
                StudentProfile p = opt.get();
                if (path != null && path.equals(p.getResumePath())) {
                    p.setResumePath(null);
                    studentDAO.update(p);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ResumeService] Warning: Could not clear resume_path in student_profiles: " + e.getMessage());
        }
    }

    private void updateStudentProfileResumePath(int studentId, String path) {
        try {
            Optional<StudentProfile> opt = studentDAO.findByUserId(studentId);
            if (opt.isPresent()) {
                StudentProfile p = opt.get();
                p.setResumePath(path);
                studentDAO.update(p);
            }
        } catch (SQLException e) {
            System.err.println("[ResumeService] Warning: Could not update resume_path in student_profiles: " + e.getMessage());
        }
    }

    private String computeSha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] byteArray = new byte[8192];
                int bytesCount;
                while ((bytesCount = fis.read(byteArray)) != -1) {
                    digest.update(byteArray, 0, bytesCount);
                }
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hash_unavailable";
        }
    }
}
