package com.mycompany.app.ui;

import com.mycompany.app.model.Crypto;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import java.util.Random;

/**
 * Embeddable crypto detail pane. Designed to be placed inside the main application layout.
 * This no longer creates a separate window; it exposes a {@code setCrypto} method
 * so mock or real data can be injected easily.
 */
public class CryptoDetailView extends VBox {
    private Label titleLabel;
    private Label priceLabel;
    private Label changeLabel;
    private VBox newsFeed;
    private LineChart<Number, Number> priceChart;
    private final String[] timeIntervals = {"1 Day", "1 Week", "1 Month", "1 Year", "All Time"};
    private final java.util.List<Button> intervalButtons = new java.util.ArrayList<>();
    private Button selectedIntervalButton = null;
    private double currentBasePrice = 50000; // default mock base

    public CryptoDetailView() {
        super(16);
        setPadding(new Insets(20));
        getStyleClass().add("detail-pane");

        HBox header = new HBox(10);
        Button backButton = new Button("←");
        backButton.getStyleClass().add("back-button");
        backButton.setOnAction(e -> clear());

        titleLabel = new Label("Select a crypto to see details");
        titleLabel.getStyleClass().add("detail-header");

        header.getChildren().addAll(backButton, titleLabel);

        VBox priceInfo = new VBox(6);
        priceLabel = new Label("");
        priceLabel.getStyleClass().add("detail-price");
        changeLabel = new Label("");
        priceInfo.getChildren().addAll(priceLabel, changeLabel);

        priceChart = createChart();

        HBox intervals = createTimeIntervalButtons();

    VBox newsSection = createNewsSection();

        getChildren().addAll(header, priceInfo, priceChart, intervals, newsSection);
        VBox.setVgrow(priceChart, Priority.ALWAYS);
    }

    private LineChart<Number, Number> createChart() {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time");
        yAxis.setLabel("Price (USD)");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Price History");
        chart.setCreateSymbols(false);
        chart.getStyleClass().add("crypto-chart");
        chart.setPrefHeight(240);
        return chart;
    }

    private HBox createTimeIntervalButtons() {
        HBox buttonBox = new HBox(8);
        for (String interval : timeIntervals) {
            Button button = new Button(interval);
            button.getStyleClass().add("time-interval-button");
            button.setOnAction(e -> selectInterval(button, interval));
            intervalButtons.add(button);
            buttonBox.getChildren().add(button);
        }

        // select first interval by default
        if (!intervalButtons.isEmpty()) {
            selectInterval(intervalButtons.get(0), timeIntervals[0]);
        }
        return buttonBox;
    }

    private VBox createNewsSection() {
        VBox newsSection = new VBox(10);
        Label newsHeader = new Label("Related News");
        newsHeader.getStyleClass().add("section-header");

        newsFeed = new VBox(10);
        ScrollPane scrollPane = new ScrollPane(newsFeed);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("news-scroll-pane");

        newsSection.getChildren().addAll(newsHeader, scrollPane);
        return newsSection;
    }

    /**
     * Populate the detail pane with a crypto model. This method is the integration point
     * for mock or real data.
     */
    public void setCrypto(Crypto crypto) {
        if (crypto == null) {
            clear();
            return;
        }

        titleLabel.setText(crypto.getName() + " (" + crypto.getSymbol() + ")");
        priceLabel.setText(crypto.getPriceFormatted());
        changeLabel.setText(crypto.getChangeFormatted());
        changeLabel.getStyleClass().removeAll("positive-change", "negative-change");
        changeLabel.getStyleClass().add(crypto.getChangePercent() >= 0 ? "positive-change" : "negative-change");

        // use crypto price as base for generated mock series
        currentBasePrice = crypto.getPrice();
        // ensure interval button selection remains (default to first)
        if (selectedIntervalButton != null) {
            // trigger update using currently selected interval
            selectInterval(selectedIntervalButton, selectedIntervalButton.getText());
        } else {
            updateChartData("1 Day");
        }

        // newsFeed will be populated by the caller via setNews(List<News>)
        newsFeed.getChildren().clear();
    }

    /**
     * Populate related news for the crypto. Caller provides the list (from service).
     */
    public void setNews(java.util.List<com.mycompany.app.model.News> news) {
        newsFeed.getChildren().clear();
        if (news == null) return;
        for (com.mycompany.app.model.News n : news) {
            newsFeed.getChildren().add(createNewsCard(n.getTitle(), n.getSource(), n.getTimeAgo()));
        }
    }

    private VBox createNewsCard(String title, String source, String time) {
        VBox card = new VBox(5);
        card.getStyleClass().add("news-card");
        card.setPadding(new Insets(12));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("news-title");
        titleLabel.setWrapText(true);

        Label sourceLabel = new Label(source);
        sourceLabel.getStyleClass().add("news-source");

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("news-time");

        card.getChildren().addAll(titleLabel, sourceLabel, timeLabel);
        card.setOnMouseClicked(e -> System.out.println("Open: " + title));
        return card;
    }

    private void updateChartData(String interval) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Price");

        Random random = new Random();
        double basePrice = Math.max(1, currentBasePrice); // use current crypto price as base
        int dataPoints;

        switch (interval) {
            case "1 Day": dataPoints = 24; break;
            case "1 Week": dataPoints = 7; break;
            case "1 Month": dataPoints = 30; break;
            case "1 Year": dataPoints = 365; break;
            default: dataPoints = 100;
        }

        for (int i = 0; i < dataPoints; i++) {
            double price = basePrice + (random.nextGaussian() * 1000);
            series.getData().add(new XYChart.Data<>(i, price));
        }

        priceChart.getData().clear();
        priceChart.getData().add(series);
    }

    private void selectInterval(Button button, String interval) {
        // toggle selected style
        if (selectedIntervalButton != null) {
            selectedIntervalButton.getStyleClass().remove("time-interval-selected");
        }
        selectedIntervalButton = button;
        if (selectedIntervalButton != null) {
            selectedIntervalButton.getStyleClass().add("time-interval-selected");
        }

        // update chart with chosen interval
        updateChartData(interval);
    }

    private void clear() {
        titleLabel.setText("Select a crypto to see details");
        priceLabel.setText("");
        changeLabel.setText("");
        priceChart.getData().clear();
        newsFeed.getChildren().clear();
    }

}
