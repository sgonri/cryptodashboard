package com.mycompany.app.views;

import com.mycompany.app.models.Crypto;
import javafx.application.HostServices;
import javafx.scene.layout.BorderPane;
import java.util.function.Consumer;

/**
 * Main application layout, holding the crypto list sidebar, detail view, and news view.
 */
public class MainView extends BorderPane {
    private final NewsView newsView;
    private final CryptoListView cryptoListView;

    public MainView(CryptoListView cryptoListView, CryptoDetailView detailView, NewsView newsView) {
        this.newsView = newsView;
        this.cryptoListView = cryptoListView;

        setLeft(cryptoListView);
        setCenter(detailView);
        setRight(newsView);

        getStyleClass().add("main-view");
    }

    /**
     * Set callback for when a crypto is selected (delegates to crypto list view)
     */
    public void setOnCryptoSelected(Consumer<Crypto> callback) {
        if (cryptoListView != null) {
            cryptoListView.setOnCryptoSelected(callback);
        }
    }

    /**
     * Set HostServices for opening URLs in browser
     * This should be called from the Application class
     */
    public void setHostServices(HostServices hostServices) {
        this.newsView.setHostServices(hostServices);
    }
}