package com.hirehub.integration;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
public class ClasspathDiagnosticTest {
    public static void main(String[] args) {
        System.out.println("=== HireHub Runtime Classpath Verifier ===");
        testClass("org.mindrot.jbcrypt.BCrypt");
        testClass("com.mysql.cj.jdbc.Driver");
        testClass("com.google.gson.Gson");
        testClass("javafx.application.Application");

        try {
            String hash = org.mindrot.jbcrypt.BCrypt.hashpw("Admin@1234", org.mindrot.jbcrypt.BCrypt.gensalt(12));
            boolean ok = org.mindrot.jbcrypt.BCrypt.checkpw("Admin@1234", hash);
            System.out.println("BCrypt live hash/check test: " + (ok ? "PASSED" : "FAILED"));
        } catch (Throwable t) {
            System.out.println("BCrypt functional test failed: " + t.getMessage());
        }
    }

    private static void testClass(String className) {
        try {
            Class<?> c = Class.forName(className);
            System.out.println("[OK] Loaded: " + className + " (" + c.getProtectionDomain().getCodeSource().getLocation() + ")");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            System.out.println("[FAILED] " + className + ": " + e.getMessage());
        }
    }
}

