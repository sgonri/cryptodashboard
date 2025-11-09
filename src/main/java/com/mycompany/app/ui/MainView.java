package com.mycompany.app.ui;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.services.CryptoService;
import com.mycompany.app.services.NewsService;
import com.mycompany.app.services.MockNewsService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import java.util.List;

public class MainView extends BorderPane {
    private final CryptoService cryptoService;
    private final NewsService newsService;
    private final CryptoDetailView detailView;
    private final NewsView newsView;
    private final VBox cryptoListBox = new VBox(8);
    private Crypto selectedCrypto = null;
    private HBox selectedSidebarItem = null;

    public MainView() {
        this.cryptoService = new CryptoService();
        this.newsService = new MockNewsService();
        this.detailView = new CryptoDetailView();
        this.newsView = new NewsView(this::updateNewsFeed);

        setLeft(createSidebar());
        setCenter(detailView);
        setRight(newsView);

        getStyleClass().add("main-view");
        loadData();
    }

    private void loadData() {
        List<Crypto> cryptos = cryptoService.getTopCryptos();
        for (Crypto crypto : cryptos) {
            cryptoListBox.getChildren().add(createSidebarItem(crypto));
        }

        if (!cryptos.isEmpty()) {
            selectCrypto(cryptos.get(0), (HBox) cryptoListBox.getChildren().get(0));
        }
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(25));
        sidebar.setPrefWidth(300);
        sidebar.getStyleClass().add("sidebar");

        Label logo = new Label("CryptoDesk");
        logo.getStyleClass().add("logo");

        TextField searchField = new TextField();
        searchField.setPromptText("Search");
        searchField.getStyleClass().add("search-field");

        Label watchlistHeader = new Label("Watchlist");
        watchlistHeader.getStyleClass().add("watchlist-header");

        ScrollPane scrollPane = new ScrollPane(cryptoListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("sidebar-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        sidebar.getChildren().addAll(logo, searchField, watchlistHeader, scrollPane);
        return sidebar;
    }

    private HBox createSidebarItem(Crypto crypto) {
        HBox item = new HBox(10);
        item.setPadding(new Insets(12));
        item.getStyleClass().add("sidebar-item");

        VBox nameAndSymbol = new VBox(2);
        Label name = new Label(crypto.getName());
        name.getStyleClass().add("crypto-name-sidebar");
        Label symbol = new Label(crypto.getSymbol());
        symbol.getStyleClass().add("crypto-symbol-sidebar");
        nameAndSymbol.getChildren().addAll(name, symbol);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label priceLabel = new Label(crypto.getPriceFormatted());
        priceLabel.getStyleClass().add("sidebar-price");

        Label change = new Label(crypto.getChangeFormatted());
        change.getStyleClass().add(crypto.getChangePercent() >= 0 ? "positive-change" : "negative-change");

        VBox priceAndChange = new VBox(4);
        priceAndChange.setAlignment(Pos.CENTER_RIGHT);
        priceAndChange.getChildren().addAll(priceLabel, change);

        item.getChildren().addAll(nameAndSymbol, spacer, priceAndChange);
        item.setOnMouseClicked(e -> selectCrypto(crypto, item));
        return item;
    }

    private void selectCrypto(Crypto crypto, HBox item) {
        if (selectedSidebarItem != null) {
            selectedSidebarItem.getStyleClass().remove("sidebar-item-selected");
        }
        selectedSidebarItem = item;
        selectedSidebarItem.getStyleClass().add("sidebar-item-selected");

        selectedCrypto = crypto;
        detailView.setCrypto(crypto);
        updateNewsFeed();
    }

    private void updateNewsFeed() {
        if (newsView.isShowAllSelected()) {
            newsView.updateNews(newsService.getGeneralNews());
        } else if (selectedCrypto != null) {
            newsView.updateNews(newsService.getNewsForCrypto(selectedCrypto.getId()));
        } else {
            newsView.updateNews(List.of());
        }
    }
}