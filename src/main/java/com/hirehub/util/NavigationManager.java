package com.hirehub.util;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * NavigationManager — Centralized, stage-preserving navigation service for HireHub.
 *
 * <p>Solves the JavaFX window shrinkage / restore-down bug on screen navigation by:
 * <ol>
 *   <li>Reusing the primary {@link Stage} across the entire application lifecycle.</li>
 *   <li>Reusing the existing {@link Scene} and swapping only its {@code root} node
 *       via {@link Scene#setRoot(Parent)}, rather than creating a new Scene.
 *       Recreating scenes on Windows causes the native window peer to restore-down.</li>
 *   <li>Capturing and restoring the window's exact state (maximized, width, height, position).
 *       If the user was maximized, the window stays maximized; if the user intentionally
 *       restored down, their custom dimensions and coordinates are preserved.</li>
 *   <li>Enforcing consistent minimum bounds (1200 × 750) and resizability.</li>
 * </ol>
 */
public final class NavigationManager {

    public static final double MIN_WIDTH  = 1200.0;
    public static final double MIN_HEIGHT = 750.0;

    private static final String STYLESHEET_PATH = "/css/style.css";

    private static Stage primaryStage;

    private NavigationManager() {}

    /**
     * Initializes and registers the primary Stage with standard properties.
     * Called once during {@code Main.start()}.
     *
     * @param stage the primary application stage
     */
    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
        if (stage != null) {
            stage.setMinWidth(MIN_WIDTH);
            stage.setMinHeight(MIN_HEIGHT);
            stage.setResizable(true);
        }
    }

    /**
     * Returns the registered primary Stage.
     */
    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // =========================================================================
    // Public Navigation Methods
    // =========================================================================

    /**
     * Navigates to an FXML view using the registered primary Stage.
     *
     * @param fxmlPath path to the FXML resource (e.g. "/fxml/login.fxml")
     */
    public static void navigateTo(String fxmlPath) {
        navigateTo((Node) null, fxmlPath);
    }

    /**
     * Navigates to an FXML view, resolving the Stage from the provided source Node.
     *
     * @param sourceNode any node currently attached to the scene, or null
     * @param fxmlPath   path to the FXML resource
     */
    public static void navigateTo(Node sourceNode, String fxmlPath) {
        navigate(sourceNode, fxmlPath, null);
    }

    /**
     * Navigates to an FXML view with a controller initializer callback before showing.
     * Useful for views that require initialization parameters (e.g. JobForm, ApplicantManagement).
     *
     * @param sourceNode  any node in the current scene, or null
     * @param fxmlPath    path to the FXML resource
     * @param initializer callback receiving the loaded controller, or null
     * @param <T>         type of the controller
     * @return the loaded controller instance
     */
    public static <T> T navigate(Node sourceNode, String fxmlPath, Consumer<T> initializer) {
        Stage stage = resolveStage(sourceNode);
        if (stage == null) {
            System.err.println("[NavigationManager] Error: No Stage available for navigation to " + fxmlPath);
            return null;
        }

        // 1. Capture exact window state prior to navigation
        final boolean wasMaximized = stage.isMaximized();
        final boolean wasIconified = stage.isIconified();
        final double prevWidth     = stage.getWidth();
        final double prevHeight    = stage.getHeight();
        final double prevX         = stage.getX();
        final double prevY         = stage.getY();

        try {
            // 2. Load the FXML view
            var resourceUrl = NavigationManager.class.getResource(fxmlPath);
            if (resourceUrl == null) {
                throw new IOException("FXML resource not found on classpath: " + fxmlPath);
            }

            FXMLLoader loader = new FXMLLoader(resourceUrl);
            Parent newRoot = loader.load();

            // 3. Initialize controller if requested
            T controller = loader.getController();
            if (initializer != null && controller != null) {
                initializer.accept(controller);
            }

            // 4. Update the Scene:
            // REUSING the existing Scene and calling setRoot() avoids destroying
            // and recreating the native Win32 window peer, preventing unwanted restore-down.
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(newRoot, MIN_WIDTH, MIN_HEIGHT);
                ensureStylesheet(scene);
                stage.setScene(scene);
            } else {
                ensureStylesheet(scene);
                scene.setRoot(newRoot);
            }

            // 5. Restore window state safely
            applyWindowState(stage, wasMaximized, wasIconified, prevWidth, prevHeight, prevX, prevY);

            return controller;

        } catch (Throwable e) {
            System.err.println("[NavigationManager] Failed to navigate to FXML: " + fxmlPath);
            System.err.println("[NavigationManager] Underlying error: " + e.getClass().getName() + " -> " + e.getMessage());
            e.printStackTrace();

            // Display user-friendly JavaFX alert (keeps current page usable)
            Platform.runLater(() -> {
                try {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    if (stage.isShowing()) {
                        alert.initOwner(stage);
                    }
                    alert.setTitle("Navigation Error");
                    alert.setHeaderText("Unable to Load Requested Screen");
                    String errSummary = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    alert.setContentText("Could not navigate to: " + fxmlPath + "\n\nError: " + errSummary +
                            "\n\nThe current page remains usable. Please check the logs or contact support.");
                    alert.showAndWait();
                } catch (Throwable alertEx) {
                    System.err.println("[NavigationManager] Alert display failed: " + alertEx.getMessage());
                }
            });

            return null;
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Resolves the target Stage from a Node, falling back to the primary Stage.
     */
    public static Stage resolveStage(Node node) {
        if (node != null && node.getScene() != null && node.getScene().getWindow() instanceof Stage s) {
            if (primaryStage == null) {
                primaryStage = s;
            }
            return s;
        }
        return primaryStage;
    }

    /**
     * Applies stylesheet if not already registered on the scene.
     */
    private static void ensureStylesheet(Scene scene) {
        try {
            String cssUrl = Objects.requireNonNull(
                    NavigationManager.class.getResource(STYLESHEET_PATH)
            ).toExternalForm();
            if (!scene.getStylesheets().contains(cssUrl)) {
                scene.getStylesheets().add(cssUrl);
            }
        } catch (Exception e) {
            System.err.println("[NavigationManager] Warning: Could not attach stylesheet: " + e.getMessage());
        }
    }

    /**
     * Restores window properties while respecting whether the user had maximized or restored.
     */
    private static void applyWindowState(Stage stage, boolean wasMaximized, boolean wasIconified,
                                         double prevWidth, double prevHeight, double prevX, double prevY) {
        // Enforce constraints
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setResizable(true);

        // Never let ordinary navigation minimize/iconify the window
        if (stage.isIconified() && !wasIconified) {
            stage.setIconified(false);
        }

        // Restore maximized or restored-down state
        if (wasMaximized) {
            if (!stage.isMaximized()) {
                stage.setMaximized(true);
            }
        } else {
            // User had intentionally restored-down: preserve their exact dimensions and position
            if (prevWidth >= MIN_WIDTH) {
                stage.setWidth(prevWidth);
            }
            if (prevHeight >= MIN_HEIGHT) {
                stage.setHeight(prevHeight);
            }
            if (!Double.isNaN(prevX) && prevX > 0) {
                stage.setX(prevX);
            }
            if (!Double.isNaN(prevY) && prevY > 0) {
                stage.setY(prevY);
            }
        }

        // Guard against asynchronous native window events un-maximizing on Windows
        Platform.runLater(() -> {
            if (wasMaximized && !stage.isMaximized()) {
                stage.setMaximized(true);
            }
        });
    }
}

