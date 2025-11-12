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
     * Load initial data (top cryptocurrencies)
     */
    public void loadInitialData() {
        listController.loadTopCryptos();
    }

    /**
     * Handle crypto selection from the sidebar
     */
    public void selectCrypto(Crypto crypto) {
        this.selectedCrypto = crypto;

        // Update detail view with selected crypto
        detailController.showCrypto(crypto);

        // Update news feed for selected crypto
        newsController.loadNewsForCrypto(crypto.getId());
    }

    /**
     * Handle news toggle change (All vs Selected)
     */
    public void handleNewsToggleChange(boolean showAll) {
        if (showAll) {
            newsController.loadGeneralNews();
        } else if (selectedCrypto != null) {
            newsController.loadNewsForCrypto(selectedCrypto.getId());
        }
    }

    /**
     * Get currently selected crypto
     */
    public Crypto getSelectedCrypto() {
        return selectedCrypto;
    }
}
