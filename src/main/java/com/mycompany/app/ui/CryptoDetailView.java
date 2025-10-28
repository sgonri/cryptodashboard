package com.mycompany.app.ui;

import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.GridPane;
import java.util.Random;

import com.mycompany.app.models.Crypto;

public class CryptoDetailView extends VBox {
    private Label titleLabel;
    private Label priceLabel;
    private Label changeLabel;
    private LineChart<Number, Number> priceChart;
    private final String[] timeIntervals = {"1D", "1W", "1M", "3M", "1Y", "All"};
    private final java.util.List<Button> intervalButtons = new java.util.ArrayList<>();
    private Button selectedIntervalButton = null;
    private double currentBasePrice = 63421;

    private final Label marketCapValue = new Label();
    private final Label volumeValue = new Label();
    private final Label circulatingSupplyValue = new Label();

    public CryptoDetailView() {
        super(20);
        setPadding(new Insets(25));
        getStyleClass().add("detail-pane");

        HBox header = new HBox(10);
        titleLabel = new Label("Bitcoin (BTC)");
        titleLabel.getStyleClass().add("detail-header");
        header.getChildren().add(titleLabel);

        VBox priceInfo = new VBox(4);
        priceLabel = new Label("");
        priceLabel.getStyleClass().add("detail-price");
        changeLabel = new Label("");
        priceInfo.getChildren().addAll(priceLabel, changeLabel);

        priceChart = createChart();
        HBox intervals = createTimeIntervalButtons();
        GridPane infoGrid = createInfoGrid();

        getChildren().addAll(header, priceInfo, priceChart, intervals, infoGrid);
        VBox.setVgrow(priceChart, Priority.ALWAYS);
    }

    private LineChart<Number, Number> createChart() {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setVisible(false);
        yAxis.setVisible(false);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.getStyleClass().add("crypto-chart");
        chart.setPrefHeight(300);
        chart.setLegendVisible(false);
        return chart;
    }

    private HBox createTimeIntervalButtons() {
        HBox buttonBox = new HBox(10);
        for (String interval : timeIntervals) {
            Button button = new Button(interval);
            button.getStyleClass().add("time-interval-button");
            button.setOnAction(e -> selectInterval(button, interval));
            intervalButtons.add(button);
            buttonBox.getChildren().add(button);
        }

        if (!intervalButtons.isEmpty()) {
            selectInterval(intervalButtons.get(0), timeIntervals[0]);
        }
        return buttonBox;
    }

    private GridPane createInfoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(40);
        grid.setVgap(15);
        grid.getStyleClass().add("info-grid");

        grid.add(new Label("Market Cap"), 0, 0);
        grid.add(marketCapValue, 1, 0);
        grid.add(new Label("Volume"), 0, 1);
        grid.add(volumeValue, 1, 1);
        grid.add(new Label("Circulating Supply"), 0, 2);
        grid.add(circulatingSupplyValue, 1, 2);

        grid.getChildren().forEach(node -> {
            if (node instanceof Label) {
                if (GridPane.getColumnIndex(node) == 0) {
                    node.getStyleClass().add("info-label");
                } else {
                    node.getStyleClass().add("info-value");
                }
            }
        });
        return grid;
    }

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

        marketCapValue.setText(crypto.getMarketCap());
        volumeValue.setText(crypto.getVolume());
        circulatingSupplyValue.setText(crypto.getCirculatingSupply());

        currentBasePrice = crypto.getPrice();
        if (selectedIntervalButton != null) {
            selectInterval(selectedIntervalButton, selectedIntervalButton.getText());
        } else {
            updateChartData("1D");
        }
    }

    private void updateChartData(String interval) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        Random random = new Random();
        double basePrice = Math.max(1, currentBasePrice);
        int dataPoints = interval.equals("1D") ? 48 : (interval.equals("1W") ? 7 * 24 : 100);

        for (int i = 0; i < dataPoints; i++) {
            double fluctuation = basePrice * (random.nextDouble() - 0.5) * 0.05;
            series.getData().add(new XYChart.Data<>(i, basePrice + fluctuation));
        }

        priceChart.getData().clear();
        priceChart.getData().add(series);
    }

    private void selectInterval(Button button, String interval) {
        if (selectedIntervalButton != null) {
            selectedIntervalButton.getStyleClass().remove("time-interval-selected");
        }
        selectedIntervalButton = button;
        if (selectedIntervalButton != null) {
            selectedIntervalButton.getStyleClass().add("time-interval-selected");
        }
        updateChartData(interval);
    }

    private void clear() {
        titleLabel.setText("Select a crypto");
        priceLabel.setText("");
        changeLabel.setText("");
        priceChart.getData().clear();
        marketCapValue.setText("");
        volumeValue.setText("");
        circulatingSupplyValue.setText("");
    }
}
