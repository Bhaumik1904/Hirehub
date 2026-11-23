module com.hirehub {
    // JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // MySQL JDBC driver
    requires java.sql;

    // BCrypt
    requires jbcrypt;

    // Gson
    requires com.google.gson;

    // Java Net (for GroqCloud HTTP API calls)
    requires java.net.http;

    // Apache PDFBox (Phase 4 Resume Analyzer)
    requires org.apache.pdfbox;

    // Desktop integration (AWT Desktop file opening)
    requires java.desktop;

    // HikariCP Connection Pool (GAP-02)
    requires com.zaxxer.hikari;
    requires org.slf4j;

    // Open packages to JavaFX for FXML reflection
    opens com.hirehub to javafx.fxml;
    opens com.hirehub.controller to javafx.fxml;
    opens com.hirehub.model to javafx.base, com.google.gson;

    // Export all packages
    exports com.hirehub;
    exports com.hirehub.controller;
    exports com.hirehub.model;
    exports com.hirehub.service;
    exports com.hirehub.dao;
    exports com.hirehub.database;
    exports com.hirehub.util;
}
