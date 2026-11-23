package com.hirehub.controller;

import com.hirehub.model.User;
import com.hirehub.service.AuthService;
import com.hirehub.util.NavigationManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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
 * RegisterController — handles the registration screen UI logic.
 *
 * <p>Collects user input, delegates registration to {@link AuthService},
 * and navigates back to login on success.</p>
 */
public class RegisterController {

    // -----------------------------------------------------------------------
    // FXML-injected controls
    // -----------------------------------------------------------------------

    @FXML private TextField      fullNameField;
    @FXML private TextField      emailField;
    @FXML private PasswordField  passwordField;
    @FXML private PasswordField  confirmPasswordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Button         registerButton;
    @FXML private Label          errorLabel;
    @FXML private Label          successLabel;
    @FXML private Hyperlink      loginLink;
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
        // Populate role choices — Admin is intentionally excluded
        roleComboBox.setItems(FXCollections.observableArrayList("Student", "Recruiter"));
        roleComboBox.getSelectionModel().selectFirst();

        errorLabel.setVisible(false);
        successLabel.setVisible(false);
        loadingIndicator.setVisible(false);
    }

    // -----------------------------------------------------------------------
    // Event handlers
    // -----------------------------------------------------------------------

    /**
     * Called when Register button is clicked.
     */
    @FXML
    public void handleRegister(ActionEvent event) {
        String fullName    = fullNameField.getText().trim();
        String email       = emailField.getText().trim();
        String password    = passwordField.getText();
        String confirmPass = confirmPasswordField.getText();
        String roleStr     = roleComboBox.getValue();

        if (fullName.isBlank() || email.isBlank() || password.isBlank() || confirmPass.isBlank()) {
            showError("Please fill in all required fields.");
            return;
        }

        User.Role role = "Recruiter".equals(roleStr) ? User.Role.RECRUITER : User.Role.STUDENT;

        setLoading(true);

        // Background task — DB call must NOT run on the JavaFX Application Thread
        Task<AuthService.AuthResult> registerTask = new Task<>() {
            @Override
            protected AuthService.AuthResult call() {
                return authService.register(fullName, email, password, confirmPass, role);
            }
        };

        registerTask.setOnSucceeded(e -> {
            setLoading(false);
            AuthService.AuthResult result = registerTask.getValue();
            if (result.isSuccess()) {
                showSuccess("Account created successfully! Redirecting to login...");
                // Brief pause then navigate to login
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    Platform.runLater(this::navigateToLogin);
                }).start();
            } else {
                showError(result.getErrorMessage());
            }
        });

        registerTask.setOnFailed(e -> {
            setLoading(false);
            showError("Unexpected error. Please try again.");
            registerTask.getException().printStackTrace();
        });

        new Thread(registerTask, "register-thread").start();
    }

    /**
     * Navigate back to the login screen.
     */
    @FXML
    public void handleLoginLink(ActionEvent event) {
        navigateToLogin();
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    private void navigateToLogin() {
        NavigationManager.navigateTo(loginLink, "/fxml/login.fxml");
    }

    // -----------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------

    private void showError(String message) {
        Platform.runLater(() -> {
            successLabel.setVisible(false);
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        });
    }

    private void showSuccess(String message) {
        Platform.runLater(() -> {
            errorLabel.setVisible(false);
            successLabel.setText(message);
            successLabel.setVisible(true);
        });
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            registerButton.setDisable(loading);
            loadingIndicator.setVisible(loading);
            if (!loading) {
                errorLabel.setVisible(false);
                successLabel.setVisible(false);
            }
        });
    }
}
