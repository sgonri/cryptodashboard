package com.mycompany.app.services;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;

import java.util.List;

/**
 * Interface for cryptocurrency data services
 * Allows for different implementations and easier testing via mocking
 */
public interface ICryptoService {
    
    /**
     * Get the top cryptocurrencies by market cap
     */
    List<Crypto> getTopCryptos();
    
    /**
     * Check if historical data is available in cache for a specific crypto and interval
     */
    boolean hasHistoricalData(String id, String days);
    
    /**
     * Fetch historical market data for a cryptocurrency
     * @param id The cryptocurrency ID (e.g., "bitcoin")
     * @param days The number of days of history (e.g., "1", "7", "30")
     */
    HistoricalData getHistoricalDataForCrypto(String id, String days);
    
    /**
     * Set callback to be notified when crypto data is loaded
     */
    void setDataLoadedCallback(DataLoadedCallback callback);
    
    /**
     * Preload all cryptocurrency data
     */
    void preloadAllData();
    
    /**
     * Get count of failed data loads
     */
    int getFailedLoadsCount();
    
    /**
     * Clear cache to allow refreshing all data
     */
    void clearCache();
    
    /**
     * Callback interface for notifying when a crypto's data is loaded
     */
    interface DataLoadedCallback {
        void onDataLoaded(String cryptoId, boolean success);
        void onIntervalDataLoaded(String cryptoId, String interval, boolean success);
    }
}
