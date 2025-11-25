package com.mycompany.app.controllers;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;
import com.mycompany.app.services.ICryptoService;
import com.mycompany.app.views.CryptoDetailView;

/**
 * Controller for the crypto detail view
 * Manages historical data fetching and chart updates
 */
public class CryptoDetailController {
    private final ICryptoService cryptoService;
    private CryptoDetailView view;
    private Crypto currentCrypto;

    public CryptoDetailController(ICryptoService cryptoService) {
        if (cryptoService == null) {
            throw new IllegalArgumentException("cryptoService cannot be null");
        }
        this.cryptoService = cryptoService;
    }

    /**
     * Set the view that this controller manages
     */
    public void setView(CryptoDetailView view) {
        this.view = view;
    }

    /**
     * Display a cryptocurrency in the detail view
     */
    public void showCrypto(Crypto crypto) {
        this.currentCrypto = crypto;
        if (view != null) {
            view.displayCrypto(crypto);
        }
    }

    /**
     * Load historical data for a specific time interval
     */
    public HistoricalData loadHistoricalData(String cryptoId, String days) {
        return cryptoService.getHistoricalDataForCrypto(cryptoId, days);
    }

    /**
     * Handle time interval selection
     * Checks if data is available, falls back to 1D if not
     */
    public void selectTimeInterval(String interval) {
        if (currentCrypto == null || view == null) {
            return;
        }

        // Convert interval to days parameter
        String days = convertIntervalToDays(interval);
        
        // Check if data is available in cache
        if (!cryptoService.hasHistoricalData(currentCrypto.getId(), days)) {
            // Data not available - fall back to 1D
            System.out.println("Data for " + interval + " not available for " + currentCrypto.getName() + ", falling back to 1D");
            if (view != null) {
                view.selectIntervalIfEnabled("1D");
            }
            return;
        }
        
        // Fetch historical data
        HistoricalData data = loadHistoricalData(currentCrypto.getId(), days);
        
        // Update view with data
        if (data != null && data.getPoints() != null && !data.getPoints().isEmpty()) {
            view.updateChartData(data);
        } else {
            // Data fetch failed - fall back to 1D
            System.out.println("Failed to load " + interval + " data for " + currentCrypto.getName() + ", falling back to 1D");
            if (view != null) {
                view.selectIntervalIfEnabled("1D");
            }
        }
    }

    /**
     * Convert interval string (1D, 1W, etc.) to days parameter for API
     */
    private String convertIntervalToDays(String interval) {
        return switch (interval) {
            case "1D" -> "1";
            case "1W" -> "7";
            case "1M" -> "30";
            case "3M" -> "90";
            case "1Y" -> "365";
            case "All" -> "365";
            default -> "1";
        };
    }

    /**
     * Get the currently displayed crypto
     */
    public Crypto getCurrentCrypto() {
        return currentCrypto;
    }
}
