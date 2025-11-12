package com.mycompany.app.controllers;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.services.CryptoService;
import com.mycompany.app.views.CryptoListView;

import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for the crypto list view
 * Responsible for loading top cryptos and forwarding selections
 */
public class CryptoListController {
    private final CryptoService cryptoService;
    private CryptoListView view;
    private Consumer<Crypto> onCryptoSelected;

    public CryptoListController(CryptoService cryptoService) {
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
        List<Crypto> list = cryptoService.getTopCryptos();
        if (view != null) {
            view.displayCryptos(list);
        }
    }
}
