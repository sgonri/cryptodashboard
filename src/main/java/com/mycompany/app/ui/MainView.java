package com.mycompany.app.ui;

import com.mycompany.app.model.Crypto;
import com.mycompany.app.service.CryptoService;
import com.mycompany.app.service.MockCryptoService;
import com.mycompany.app.service.NewsService;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.util.Random;

/**
 * Main application view. Left: crypto list, Center: detail pane, Right: news.
 * Uses a service interface so data source can be swapped later.
 */
public class MainView extends BorderPane {
    private VBox cryptoList;
    private VBox newsFeed;
    private final CryptoDetailView detailView;
    private final CryptoService cryptoService;
    private final NewsService newsService;
    private javafx.scene.Node selectedCard = null;

    public MainView() {
        this.cryptoService = new MockCryptoService();
        this.newsService = new com.mycompany.app.service.MockNewsService();
        this.detailView = new CryptoDetailView();

        setupLeftSection();
        setupRightSection();
        setCenter(detailView); // embed detail view in the center

        // Add padding around the main view
        setPadding(new Insets(20));
    }

    private void selectCard(javafx.scene.Node node) {
        if (node == null) return;
        // remove previous selection style
        if (selectedCard != null) {
            selectedCard.getStyleClass().remove("crypto-card-selected");
        }
        // apply new selection
        selectedCard = node;
        selectedCard.getStyleClass().add("crypto-card-selected");

        // populate details
        Object ud = node.getUserData();
        if (ud instanceof Crypto crypto) {
            detailView.setCrypto(crypto);
            detailView.setNews(newsService.getNewsForCrypto(crypto.getId()));
        }
    }

    private void setupLeftSection() {
        // Create the crypto list section
        cryptoList = new VBox(10); // 10px spacing between elements
        cryptoList.setPadding(new Insets(10));
    cryptoList.setPrefWidth(360);

        // Add header
        Text header = new Text("Top Cryptocurrencies");
        header.getStyleClass().add("section-header");

        // Use service for data and create ranked list (1..5)
        int rank = 1;
        for (Crypto crypto : cryptoService.getTopCryptos()) {
            cryptoList.getChildren().add(createCryptoCard(crypto, rank));
            rank++;
        }

        // select first crypto by default
        if (!cryptoList.getChildren().isEmpty()) {
            javafx.scene.Node first = cryptoList.getChildren().get(0);
            selectCard(first);
        }

        // Create a scroll pane for the crypto list
        ScrollPane scrollPane = new ScrollPane(cryptoList);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("crypto-scroll-pane");

        // Add header and scroll pane to a VBox
        VBox leftSection = new VBox(10, header, scrollPane);
        setLeft(leftSection);
    }

    private void setupRightSection() {
        // Create the news feed section
        newsFeed = new VBox(10);
        newsFeed.setPadding(new Insets(10));
        newsFeed.setPrefWidth(320);

        // Add header
        Text header = new Text("Latest News");
        header.getStyleClass().add("section-header");

        // Add mock news data
        String[][] mockNews = {
            {"Bitcoin Hits New All-Time High", "CryptoNews", "2 hours ago"},
            {"Ethereum 2.0 Update: What You Need to Know", "BlockchainToday", "5 hours ago"},
            {"Regulatory Changes Impact Crypto Market", "CoinDesk", "1 day ago"},
            {"New DeFi Protocol Launches", "CryptoDaily", "2 days ago"}
        };

        for (String[] news : mockNews) {
            newsFeed.getChildren().add(createNewsCard(news));
        }

        // Create a scroll pane for the news feed
        ScrollPane scrollPane = new ScrollPane(newsFeed);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("news-scroll-pane");

        // Add header and scroll pane to a VBox
        VBox rightSection = new VBox(10, header, scrollPane);
        setRight(rightSection);
    }

    private VBox createCryptoCard(Crypto data, int rank) {
        VBox card = new VBox(10);
        card.getStyleClass().add("crypto-card");
        card.setPadding(new Insets(15));

        // store model on the node for easy retrieval
        card.setUserData(data);

    HBox titleRow = new HBox(10);
    Label rankBadge = new Label(String.valueOf(rank));
    rankBadge.getStyleClass().add("rank-badge");
    Label name = new Label(data.getName() + " (" + data.getSymbol() + ")");
    name.getStyleClass().add("crypto-name");
    titleRow.getChildren().addAll(rankBadge, name);
        name.getStyleClass().add("crypto-name");

        HBox priceRow = new HBox(10);
        Label price = new Label(data.getPriceFormatted());
        Label change = new Label(data.getChangeFormatted());
        change.getStyleClass().add(data.getChangePercent() >= 0 ? "positive-change" : "negative-change");
        priceRow.getChildren().addAll(price, change);

        Label marketCap = new Label("Market Cap: " + data.getMarketCap());
        marketCap.getStyleClass().add("market-cap");

        // Create mini chart
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        yAxis.setTickLabelsVisible(false);
        yAxis.setTickMarkVisible(false);

        LineChart<Number, Number> miniChart = new LineChart<>(xAxis, yAxis);
        miniChart.setLegendVisible(false);
        miniChart.setCreateSymbols(false);
        miniChart.setPrefHeight(80);
        miniChart.getStyleClass().add("mini-chart");

        // Add mock data to chart
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        Random random = new Random();
        double basePrice = Math.max(10, data.getPrice());

        for (int i = 0; i < 24; i++) {
            double price_point = basePrice + (random.nextGaussian() * Math.max(1, basePrice * 0.02));
            series.getData().add(new XYChart.Data<>(i, price_point));
        }

        miniChart.getData().add(series);

    card.getChildren().addAll(titleRow, priceRow, marketCap, miniChart);

        // Add hover effect
        card.setOnMouseEntered(e -> card.getStyleClass().add("crypto-card-hover"));
        card.setOnMouseExited(e -> card.getStyleClass().remove("crypto-card-hover"));

        // Make card clickable -> populate embedded detail pane
        card.setOnMouseClicked(e -> selectCard(card));

        return card;
    }

    

    private VBox createNewsCard(String[] data) {
        VBox card = new VBox(5);
        card.getStyleClass().add("news-card");
        card.setPadding(new Insets(15));

        Label title = new Label(data[0]);
        title.getStyleClass().add("news-title");
        title.setWrapText(true);

        Label source = new Label(data[1]);
        source.getStyleClass().add("news-source");

        Label time = new Label(data[2]);
        time.getStyleClass().add("news-time");

        card.getChildren().addAll(title, source, time);

        // Add hover effect and click handler
        card.setOnMouseEntered(e -> card.getStyleClass().add("news-card-hover"));
        card.setOnMouseExited(e -> card.getStyleClass().remove("news-card-hover"));
        card.setOnMouseClicked(e -> openNewsInBrowser(data[0]));

        return card;
    }

    private void openNewsInBrowser(String title) {
        // Mock implementation - would normally open in default browser
        System.out.println("Opening news article: " + title);
    }
}