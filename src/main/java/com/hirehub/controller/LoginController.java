package com.hirehub.controller;

import com.hirehub.model.User;
import com.hirehub.service.AuthService;
import com.hirehub.util.NavigationManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * LoginController — handles the login screen UI logic.
 *
 * <p>Delegates all business logic to {@link AuthService}.
 * Database calls run on a background thread to keep the UI responsive.</p>
 */
public class LoginController {

    // -----------------------------------------------------------------------
    // FXML-injected controls
    // -----------------------------------------------------------------------

    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;
    @FXML private Hyperlink     registerLink;
    @FXML private ProgressIndicator loadingIndicator;

    // -----------------------------------------------------------------------
    // Service
    // -----------------------------------------------------------------------

    private final AuthService authService = new AuthService();

    // -----------------------------------------------------------------------
    // FXML lifecycle
    // -----------------------------------------------------------------------

    @FXML
    public void initialize() {
        // Hide error and loading indicator on startup
        errorLabel.setVisible(false);
        loadingIndicator.setVisible(false);

        // Allow pressing Enter in password field to trigger login
        passwordField.setOnAction(this::handleLogin);
    }

    // -----------------------------------------------------------------------
    // Event handlers
    // -----------------------------------------------------------------------

    /**
     * Called when the Login button is clicked or Enter is pressed.
     * Runs the authentication on a background thread.
     */
    @FXML
    public void handleLogin(ActionEvent event) {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();

        // Basic front-end guard — service also validates
        if (email.isBlank() || password.isBlank()) {
            showError("Please enter your email and password.");
            return;
        }

        setLoading(true);

        // Background task — prevents UI freeze during DB call
        Task<AuthService.AuthResult> loginTask = new Task<>() {
            @Override
            protected AuthService.AuthResult call() {
                return authService.login(email, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            setLoading(false);
            AuthService.AuthResult result = loginTask.getValue();
            if (result.isSuccess()) {
                navigateToDashboard(result.getUser());
            } else {
                showError(result.getErrorMessage());
            }
        });

        loginTask.setOnFailed(e -> {
            setLoading(false);
            showError("Unexpected error. Please try again.");
            loginTask.getException().printStackTrace();
        });

        new Thread(loginTask, "login-thread").start();
    }

    /**
     * Navigate to the registration screen.
     */
    @FXML
    public void handleRegisterLink(ActionEvent event) {
        NavigationManager.navigateTo(registerLink, "/fxml/register.fxml");
    }

    // -----------------------------------------------------------------------
    // Navigation helper
    // -----------------------------------------------------------------------

    /**
     * After a successful login, navigate to the role-appropriate dashboard.
     */
    private void navigateToDashboard(User user) {
        Platform.runLater(() -> {
            String fxmlPath = switch (user.getRole()) {
                case STUDENT   -> "/fxml/student_dashboard.fxml";
                case RECRUITER -> "/fxml/recruiter_dashboard.fxml";
                case ADMIN     -> "/fxml/admin_dashboard.fxml";
            };
            NavigationManager.navigateTo(loginButton, fxmlPath);
        });
    }

    // -----------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------

    private void showError(String message) {
        Platform.runLater(() -> {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        });
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            loginButton.setDisable(loading);
            loadingIndicator.setVisible(loading);
            if (!loading) {
                errorLabel.setVisible(false);
            }
        });
    }
}
