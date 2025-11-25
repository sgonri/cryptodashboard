package com.mycompany.app.controllers;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.services.ICryptoService;
import com.mycompany.app.views.CryptoListView;

import java.util.function.Consumer;

/**
 * Controller for the crypto list view
 * Responsible for loading top cryptos and forwarding selections
 */
public class CryptoListController {
    private final ICryptoService cryptoService;
    private CryptoListView view;
    private Consumer<Crypto> onCryptoSelected;

    public CryptoListController(ICryptoService cryptoService) {
        if (cryptoService == null) {
            throw new IllegalArgumentException("cryptoService cannot be null");
        }
        this.cryptoService = cryptoService;
    }

    public void setView(CryptoListView view) {
        this.view = view;
        // Wire view selection to controller callback if set
        if (this.view != null && onCryptoSelected != null) {
            this.view.setOnCryptoSelected(onCryptoSelected);
        }
    }

    public void setOnCryptoSelected(Consumer<Crypto> callback) {
        this.onCryptoSelected = callback;
        if (this.view != null) {
            this.view.setOnCryptoSelected(callback);
        }
    }

    /**
     * Load top cryptocurrencies and display them in the view
     */
    public void loadTopCryptos() {
        java.util.concurrent.CompletableFuture.supplyAsync(() -> cryptoService.getTopCryptos())
                .thenAccept(list -> {
                    System.out.println(
                            "CryptoListController received " + (list == null ? "null" : list.size()) + " cryptos");
                    if (view != null) {
                        javafx.application.Platform.runLater(() -> view.displayCryptos(list));
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("Failed to load top cryptos: " + ex.getMessage());
                    return null;
                });
    }
}
