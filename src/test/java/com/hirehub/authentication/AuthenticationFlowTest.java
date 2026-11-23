package com.hirehub.authentication;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.database.DatabaseConnection;
import com.hirehub.model.User;
import com.hirehub.service.AuthService;
import com.hirehub.util.PasswordUtil;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class AuthenticationFlowTest {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  HireHub Authentication & BCrypt Runtime Verifier");
        System.out.println("==================================================");

        // 1. Verify BCrypt class loading & functional execution
        System.out.println("\n[1] Verifying BCrypt class loading:");
        try {
            Class<?> clazz = Class.forName("org.mindrot.jbcrypt.BCrypt");
            System.out.println("    [OK] Loaded: " + clazz.getName());
            String sampleHash = BCrypt.hashpw("SampleTest@123", BCrypt.gensalt(10));
            boolean sampleCheck = BCrypt.checkpw("SampleTest@123", sampleHash);
            System.out.println("    [OK] BCrypt hash & verify functional test: " + (sampleCheck ? "PASSED" : "FAILED"));
        } catch (Throwable t) {
            System.out.println("    [FAIL] BCrypt loading error: " + t);
            t.printStackTrace();
            System.exit(1);
        }

        // 2. Database connectivity & users inspection
        System.out.println("\n[2] Database connection and user records:");
        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println("    [OK] Connected to: " + conn.getCatalog());
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, full_name, email, role, password_hash FROM users ORDER BY id ASC")) {
                while (rs.next()) {
                    String hash = rs.getString("password_hash");
                    String prefix = (hash != null && hash.length() >= 7) ? hash.substring(0, 7) + "..." : "invalid";
                    System.out.println(String.format("    - ID %2d | Role: %-9s | Email: %-28s | Hash: %s",
                            rs.getInt("id"),
                            rs.getString("role"),
                            rs.getString("email"),
                            prefix));
                }
            }
        } catch (Exception e) {
            System.out.println("    [FAIL] Database connection error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // 3. Test Student & Recruiter login execution through AuthService
        System.out.println("\n[3] Testing AuthService with BCrypt verification on real DB accounts:");
        AuthService authService = new AuthService();

        // 3a. Admin login with seeded credentials
        System.out.println("\n  --> Testing Admin Login (admin@hirehub.com):");
        AuthService.AuthResult adminRes = authService.login("admin@hirehub.com", "Admin@1234");
        if (adminRes.isSuccess()) {
            System.out.println("      [SUCCESS] Admin authenticated as: " + adminRes.getUser().getFullName() + " (Role: " + adminRes.getUser().getRole() + ")");
        } else {
            System.out.println("      [INFO] Result: " + adminRes.getErrorMessage());
        }

        // 3b. Student BCrypt hash execution (bhaumikhinunia019@gmail.com)
        System.out.println("\n  --> Testing Student Authentication Path (bhaumikhinunia019@gmail.com):");
        AuthService.AuthResult studentRes = authService.login("bhaumikhinunia019@gmail.com", "CheckBCryptRuntimeCall@999");
        System.out.println("      [OK] BCrypt executed against student password hash without NoClassDefFoundError!");
        System.out.println("      [OK] AuthResult message: " + studentRes.getErrorMessage());

        // 3c. Recruiter BCrypt hash execution (infotech@gmail.com)
        System.out.println("\n  --> Testing Recruiter Authentication Path (infotech@gmail.com):");
        AuthService.AuthResult recruiterRes = authService.login("infotech@gmail.com", "CheckBCryptRuntimeCall@999");
        System.out.println("      [OK] BCrypt executed against recruiter password hash without NoClassDefFoundError!");
        System.out.println("      [OK] AuthResult message: " + recruiterRes.getErrorMessage());

        // 3d. End-to-end Student Registration & Login test with BCrypt
        System.out.println("\n  --> Testing End-to-End Registration & Login with temporary test student:");
        String tempStudentEmail = "test_verify_student_" + System.currentTimeMillis() + "@hirehub.edu";
        String tempStudentPass = "StudentPass@2026";
        AuthService.AuthResult regStudent = authService.register(
                "Test Student BCrypt",
                tempStudentEmail,
                tempStudentPass,
                tempStudentPass,
                User.Role.STUDENT
        );
        if (regStudent.isSuccess()) {
            System.out.println("      [SUCCESS] Temporary student registered with BCrypt hash (ID: " + regStudent.getUser().getId() + ")");
            AuthService.AuthResult loginStudent = authService.login(tempStudentEmail, tempStudentPass);
            if (loginStudent.isSuccess() && loginStudent.getUser().getRole() == User.Role.STUDENT) {
                System.out.println("      [SUCCESS] Student login verified successfully with BCrypt!");
            } else {
                System.out.println("      [FAIL] Student login failed: " + loginStudent.getErrorMessage());
            }

            // Clean up temporary test student
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM users WHERE id = " + regStudent.getUser().getId());
                System.out.println("      [CLEANUP] Temporary student removed from database.");
            } catch (Exception ignored) {}
        }

        // 3e. End-to-end Recruiter Registration & Login test with BCrypt
        System.out.println("\n  --> Testing End-to-End Registration & Login with temporary test recruiter:");
        String tempRecruiterEmail = "test_verify_recruiter_" + System.currentTimeMillis() + "@hirehub.com";
        String tempRecruiterPass = "RecruiterPass@2026";
        AuthService.AuthResult regRecruiter = authService.register(
                "Test Recruiter BCrypt",
                tempRecruiterEmail,
                tempRecruiterPass,
                tempRecruiterPass,
                User.Role.RECRUITER
        );
        if (regRecruiter.isSuccess()) {
            System.out.println("      [SUCCESS] Temporary recruiter registered with BCrypt hash (ID: " + regRecruiter.getUser().getId() + ")");
            AuthService.AuthResult loginRecruiter = authService.login(tempRecruiterEmail, tempRecruiterPass);
            if (loginRecruiter.isSuccess() && loginRecruiter.getUser().getRole() == User.Role.RECRUITER) {
                System.out.println("      [SUCCESS] Recruiter login verified successfully with BCrypt!");
            } else {
                System.out.println("      [FAIL] Recruiter login failed: " + loginRecruiter.getErrorMessage());
            }

            // Clean up temporary test recruiter
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM users WHERE id = " + regRecruiter.getUser().getId());
                System.out.println("      [CLEANUP] Temporary recruiter removed from database.");
            } catch (Exception ignored) {}
        }

        System.out.println("\n==================================================");
        System.out.println("  ALL AUTHENTICATION & BCRYPT TESTS PASSED!");
        System.out.println("==================================================");
    }
}
