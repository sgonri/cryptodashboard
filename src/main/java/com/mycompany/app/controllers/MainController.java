package com.mycompany.app.controllers;

import com.mycompany.app.models.Crypto;

/**
 * Controller for the main view
 * Orchestrates app coordination and wires all the controllers together
 */
public class MainController {
    private final CryptoListController listController;
    private final CryptoDetailController detailController;
    private final NewsController newsController;
    private Crypto selectedCrypto;
    private boolean showAllNews = true;

    public MainController(CryptoListController listController,
            CryptoDetailController detailController,
            NewsController newsController) {
        this.listController = listController;
        this.detailController = detailController;
        this.newsController = newsController;

        // Wire up the crypto selection callback
        this.listController.setOnCryptoSelected(this::selectCrypto);
    }

    /**
     * Load initial data (top cryptocurrencies and general news)
     * Bitcoin will be auto-selected once its data is loaded via the DataLoadedCallback in App.java
     */
    public void loadInitialData() {
        listController.loadTopCryptos();
        newsController.loadGeneralNews();
    }

    /**
     * Handle crypto selection from the sidebar
     */
    public void selectCrypto(Crypto crypto) {
        this.selectedCrypto = crypto;

        // Update detail view with selected crypto
        detailController.showCrypto(crypto);

        // Only update news feed if "Selected" toggle is active
        // If "All" is selected, general news should remain unchanged
        if (!showAllNews) {
            newsController.loadNewsForCrypto(crypto.getName());
        }
    }

    /**
     * Handle news toggle change (All vs Selected)
     */
    public void handleNewsToggleChange(boolean showAll) {
        this.showAllNews = showAll;
        if (showAll) {
            newsController.loadGeneralNews();
        } else if (selectedCrypto != null) {
            newsController.loadNewsForCrypto(selectedCrypto.getName());
        }
    }

    /**
     * Get currently selected crypto
     */
    public Crypto getSelectedCrypto() {
        return selectedCrypto;
    }
}
