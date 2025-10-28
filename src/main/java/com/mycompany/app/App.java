package com.mycompany.app;

import javafx.application.Application;
import javafx.scene.Scene;
 
import javafx.stage.Stage;
import com.mycompany.app.ui.MainView;

/**
 * Crypto Dashboard Application
 */
public class App extends Application {
    @Override
    public void start(Stage primaryStage) {
    // Create the main layout inside a StackPane so we can show in-window modals
    javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
    root.getChildren().add(new MainView());

    // Create the scene with dark theme
    Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        
        // Set up the primary stage
        primaryStage.setTitle("Crypto Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
