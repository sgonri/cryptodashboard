package com.mycompany.app.controllers;

import com.mycompany.app.models.News;
import com.mycompany.app.services.INewsService;
import com.mycompany.app.views.NewsView;
import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the news view
 * Manages news fetching and filtering asynchronously to avoid blocking the UI
 */
public class NewsController {
    private final INewsService newsService;
    private NewsView view;

    public NewsController(INewsService newsService) {
        if (newsService == null) {
            throw new IllegalArgumentException("newsService cannot be null");
        }
        this.newsService = newsService;
    }

    /**
     * Set the view that this controller manages
     */
    public void setView(NewsView view) {
        this.view = view;
    }

    /**
     * Load general cryptocurrency news asynchronously
     */
    public void loadGeneralNews() {
        CompletableFuture.supplyAsync(() -> newsService.getGeneralNews())
            .thenAccept(news -> {
                if (view != null) {
                    Platform.runLater(() -> view.updateNews(news));
                }
            })
            .exceptionally(ex -> {
                System.err.println("Failed to load general news: " + ex.getMessage());
                return null;
            });
    }

    /**
     * Load news for a specific cryptocurrency asynchronously
     */
    public void loadNewsForCrypto(String cryptoId) {
        CompletableFuture.supplyAsync(() -> newsService.getNewsForCrypto(cryptoId))
            .thenAccept(news -> {
                if (view != null) {
                    Platform.runLater(() -> view.updateNews(news));
                }
            })
            .exceptionally(ex -> {
                System.err.println("Failed to load news for " + cryptoId + ": " + ex.getMessage());
                return null;
            });
    }

    /**
     * Synchronous version for testing - loads general news and returns it
     */
    List<News> loadGeneralNewsSync() {
        return newsService.getGeneralNews();
    }

    /**
     * Synchronous version for testing - loads news for crypto and returns it
     */
    List<News> loadNewsForCryptoSync(String cryptoId) {
        return newsService.getNewsForCrypto(cryptoId);
    }
}
