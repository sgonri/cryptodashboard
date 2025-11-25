package com.mycompany.app.services;

import com.mycompany.app.models.News;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for news services
 * Allows for different implementations and easier testing via mocking
 */
public interface INewsService {
    
    /**
     * Get news for a specific cryptocurrency
     * @param cryptoId The cryptocurrency identifier
     */
    List<News> getNewsForCrypto(String cryptoId);
    
    /**
     * Get general cryptocurrency news
     */
    List<News> getGeneralNews();
    
    /**
     * Get news for all top cryptocurrencies and general crypto news
     */
    List<News> getAllNews();
    
    /**
     * Async version of news search for non-blocking operations
     */
    CompletableFuture<List<News>> searchNewsAsync(String query);
}
