package com.mycompany.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.mycompany.app.views.MainView;
import com.mycompany.app.views.CryptoDetailView;
import com.mycompany.app.views.NewsView;
import com.mycompany.app.views.CryptoListView;
import com.mycompany.app.controllers.MainController;
import com.mycompany.app.controllers.CryptoDetailController;
import com.mycompany.app.controllers.NewsController;
import com.mycompany.app.controllers.CryptoListController;
import com.mycompany.app.services.CryptoService;
import com.mycompany.app.services.SerpAPINewsService;
import com.mycompany.app.models.Crypto;

/**
 * Crypto Dashboard Application
 */
public class App extends Application {
    @Override
    public void start(Stage primaryStage) {
        // Initialize services
        CryptoService cryptoService = new CryptoService();
        SerpAPINewsService newsService = new SerpAPINewsService();
        
        // Initialize controllers
        CryptoListController cryptoListController = new CryptoListController(cryptoService);
        CryptoDetailController detailController = new CryptoDetailController(cryptoService);
        NewsController newsController = new NewsController(newsService);
        
        // MainController orchestrates everything
        MainController mainController = new MainController(
            cryptoListController,
            detailController,
            newsController
        );

        // Initialize views
        CryptoDetailView detailView = new CryptoDetailView();
        NewsView newsView = new NewsView(mainController::handleNewsToggleChange);
        CryptoListView cryptoListView = new CryptoListView();

        MainView mainView = new MainView(cryptoListView, detailView, newsView);

        // Wire controllers to views
        cryptoListController.setView(cryptoListView);
        detailController.setView(detailView);
        newsController.setView(newsView);

        // Set up detail view interval callback
        detailView.setOnIntervalSelected(interval -> {
            Crypto current = mainController.getSelectedCrypto();
            if (current != null) {
                detailController.selectTimeInterval(interval);
            }
        });

        // Set host services for opening URLs
        mainView.setHostServices(getHostServices());

        // Load initial data immediately so UI shows something
        mainController.loadInitialData();

        // Set up callback to enable crypto buttons and interval buttons when their data is loaded
        cryptoService.setDataLoadedCallback(new com.mycompany.app.services.ICryptoService.DataLoadedCallback() {
            @Override
            public void onDataLoaded(String cryptoId, boolean success) {
                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        cryptoListView.setCryptoEnabled(cryptoId, true);
                    }
                });
            }
            
            @Override
            public void onIntervalDataLoaded(String cryptoId, String interval, boolean success) {
                javafx.application.Platform.runLater(() -> {
                    // Enable interval button only when all cryptos have loaded this interval
                    // cryptoId is null when all cryptos have loaded the interval
                    if (success && cryptoId == null) {
                        detailView.setIntervalEnabled(interval, true);
                    }
                });
            }
        });
        
        // Set up refresh button callback
        detailView.setOnRefreshRequested(() -> {
            // Hide refresh button while refreshing
            detailView.setRefreshButtonVisible(false);
            new Thread(() -> {
                try {
                    // Refresh all data - clear cache and reload
                    System.out.println("Refreshing all cryptocurrency data...");
                    cryptoService.clearCache();
                    cryptoService.preloadAllData();
                    // Refresh button will be shown again when preloading completes (monitored below)
                } catch (Exception e) {
                    System.err.println("Error during data refresh: " + e.getMessage());
                }
            }).start();
        });

        // Preload all historical data in background thread to avoid blocking UI
        // This will cache all data so subsequent user interactions don't need API calls
        Thread preloadThread = new Thread(() -> {
            try {
                cryptoService.preloadAllData();
            } catch (Exception e) {
                System.err.println("Error during data preloading: " + e.getMessage());
            }
        });
        preloadThread.start();
        
        // Monitor preloading completion to show refresh button
        // Refresh button should be visible only when all data is loaded (no failed loads)
        // This allows user to refresh to get latest data
        new Thread(() -> {
            try {
                // Wait for preloading to complete
                preloadThread.join();
                
                // Wait a bit more to ensure all callbacks have been processed
                Thread.sleep(2000);
                
                // Check if there are any failed loads
                int failedCount = cryptoService.getFailedLoadsCount();
                
                // Show refresh button only when all data is loaded (no failed loads)
                javafx.application.Platform.runLater(() -> {
                    detailView.setRefreshButtonVisible(failedCount == 0);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        // Create the main layout
        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
        root.getChildren().add(mainView);

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
