package com.hirehub;

import com.hirehub.database.DatabaseConnection;
import com.hirehub.util.NavigationManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Objects;

/**
 * HireHub — Main Application Entry Point.
 * Launches the JavaFX application and loads the login screen.
 */
public class Main extends Application {

    /** Minimum window dimensions (sensible default: 1200 × 750) */
    private static final double MIN_WIDTH  = 1200;
    private static final double MIN_HEIGHT = 750;

    @Override
    public void start(Stage primaryStage) throws Exception {
        NavigationManager.setPrimaryStage(primaryStage);

        // Load the login screen FXML
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/login.fxml")
        );
        Parent root = loader.load();

        Scene scene = new Scene(root, MIN_WIDTH, MIN_HEIGHT);

        // Apply global stylesheet
        scene.getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/css/style.css")
                ).toExternalForm()
        );

        primaryStage.setTitle("HireHub — Career Portal");
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setResizable(true);
        primaryStage.setMaximized(true);
        primaryStage.setScene(scene);

        // Optionally set app icon if present
        InputStream iconStream = getClass().getResourceAsStream("/images/logo.png");
        if (iconStream != null) {
            primaryStage.getIcons().add(new Image(iconStream));
        }

        primaryStage.show();
    }

    /**
     * Called when the JavaFX application stops (e.g. window closed).
     * Shuts down the HikariCP connection pool cleanly.
     */
    @Override
    public void stop() throws Exception {
        DatabaseConnection.shutdown();
        super.stop();
    }

    /**
     * Application entry point.
     * JavaFX applications must call Application.launch().
     */
    public static void main(String[] args) {
        launch(args);
    }
}
