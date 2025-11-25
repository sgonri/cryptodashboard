package com.mycompany.app.services;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for cryptocurrency data to avoid excessive API calls
 * Stores top cryptos list and historical data for each crypto and time interval
 * Thread-safe implementation using volatile and synchronized blocks for topCryptos,
 * and ConcurrentHashMap for historical data.
 */
public class CryptoCache {
    private volatile List<Crypto> topCryptos;
    private final Map<String, Map<String, HistoricalData>> historicalDataCache;
    private final Object lock = new Object();

    public CryptoCache() {
        this.historicalDataCache = new ConcurrentHashMap<>();
    }

    /**
     * Store the list of top cryptocurrencies
     */
    public void setTopCryptos(List<Crypto> cryptos) {
        synchronized (lock) {
            this.topCryptos = cryptos != null ? new ArrayList<>(cryptos) : new ArrayList<>();
        }
    }

    /**
     * Get the cached list of top cryptocurrencies
     */
    public List<Crypto> getTopCryptos() {
        synchronized (lock) {
            return topCryptos != null ? new ArrayList<>(topCryptos) : new ArrayList<>();
        }
    }

    /**
     * Check if top cryptos are cached
     */
    public boolean hasTopCryptos() {
        synchronized (lock) {
            return topCryptos != null && !topCryptos.isEmpty();
        }
    }

    /**
     * Store historical data for a specific crypto and time interval
     */
    public void putHistoricalData(String cryptoId, String days, HistoricalData data) {
        if (cryptoId == null || days == null || data == null) {
            return;
        }
        historicalDataCache.computeIfAbsent(cryptoId, k -> new ConcurrentHashMap<>())
                           .put(days, data);
    }

    /**
     * Get cached historical data for a specific crypto and time interval
     */
    public HistoricalData getHistoricalData(String cryptoId, String days) {
        if (cryptoId == null || days == null) {
            return null;
        }
        Map<String, HistoricalData> cryptoData = historicalDataCache.get(cryptoId);
        if (cryptoData == null) {
            return null;
        }
        return cryptoData.get(days);
    }

    /**
     * Check if historical data is cached for a specific crypto and time interval
     */
    public boolean hasHistoricalData(String cryptoId, String days) {
        if (cryptoId == null || days == null) {
            return false;
        }
        Map<String, HistoricalData> cryptoData = historicalDataCache.get(cryptoId);
        return cryptoData != null && cryptoData.containsKey(days);
    }

    /**
     * Clear all cached data
     */
    public void clear() {
        synchronized (lock) {
            topCryptos = null;
        }
        // ConcurrentHashMap handles its own synchronization
        historicalDataCache.clear();
    }

    /**
     * Get the number of cached historical data entries
     * Useful for testing and monitoring
     */
    public int getHistoricalDataCount() {
        return historicalDataCache.values().stream()
                .mapToInt(Map::size)
                .sum();
    }
}

