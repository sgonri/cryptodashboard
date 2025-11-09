package com.mycompany.app.ui;

import javafx.application.Platform;
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
import com.mycompany.app.models.Crypto;
import com.mycompany.app.services.CryptoService;
import com.mycompany.app.models.HistoricalData;

import com.mycompany.app.models.ChartPoint;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.util.StringConverter;

public class CryptoDetailView extends VBox {
    private Label titleLabel;
    private Label priceLabel;
    private Label changeLabel;
    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();
    private LineChart<Number, Number> priceChart;
    private final String[] timeIntervals = {"1D", "1W", "1M", "3M", "1Y", "All"};
    private final java.util.List<Button> intervalButtons = new java.util.ArrayList<>();
    private Button selectedIntervalButton = null;

    private final CryptoService cryptoService = new CryptoService();
    private Crypto currentCrypto = null;

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
        xAxis.setVisible(false);
        yAxis.setVisible(false);
        yAxis.setForceZeroInRange(false);

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

        this.currentCrypto = crypto;

        if (selectedIntervalButton != null) {
            selectInterval(selectedIntervalButton, selectedIntervalButton.getText());
        } else {
            updateChartData("1D");
        }
    }

    private void updateChartData(String interval) {
        // Determine days based on the interval
        final String days;
        switch (interval) {
            case "1D": days = "1"; break;
            case "1W": days = "7"; break;
            case "1M": days = "30"; break;
            case "3M": days = "90"; break;
            case "1Y": days = "365"; break;
            case "All": default: days = "365"; break;
        }

        final Crypto selected = this.currentCrypto;
        if (selected == null) {
            return;
        }

        new Thread(() -> {
            try {
                // fetch selected crypto
                HistoricalData hd = cryptoService.getHistoricalDataForCrypto(selected.getId(), days);

                if (hd == null || hd.getPoints().isEmpty()) {
                    return;
                }

                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                long minX = Long.MAX_VALUE, maxX = Long.MIN_VALUE;
                double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
                for (ChartPoint p : hd.getPoints()) {
                    Double price = p.getPrice();
                    if (price == null) continue;
                    long x = p.getEpochMilli();
                    series.getData().add(new XYChart.Data<>(x, price));
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (price < minY) minY = price;
                    if (price > maxY) maxY = price;
                }

                final long fMinX = minX, fMaxX = maxX;
                final double fMinY = minY, fMaxY = maxY;
                Platform.runLater(() -> {
                    priceChart.getData().clear();
                    priceChart.getData().add(series);

                    final DateTimeFormatter formatter = chooseFormatter(days);

                    xAxis.setTickLabelFormatter(new StringConverter<Number>() {
                        @Override
                        public String toString(Number object) {
                            if (object == null) return "";
                            try {
                                return formatter.format(Instant.ofEpochMilli(object.longValue()));
                            } catch (Exception e) {
                                return Long.toString(object.longValue());
                            }
                        }

                        @Override
                        public Number fromString(String string) {
                            return null;
                        }
                    });

                    // adjust X axis to the data range
                    if (fMinX != Long.MAX_VALUE && fMaxX != Long.MIN_VALUE) {
                        double lowerX = fMinX;
                        double upperX = fMaxX;
                        if (lowerX == upperX) {
                            // single point — give a 1-hour window
                            lowerX = lowerX - 3_600_000;
                            upperX = upperX + 3_600_000;
                        }
                        xAxis.setAutoRanging(false);
                        xAxis.setLowerBound(lowerX);
                        xAxis.setUpperBound(upperX);
                        double tick = Math.max(1.0, (upperX - lowerX) / 6.0);
                        xAxis.setTickUnit(tick);
                    } else {
                        xAxis.setAutoRanging(true);
                    }

                    // adjust Y axis to min/max with padding
                    if (!Double.isInfinite(fMinY) && !Double.isInfinite(fMaxY)) {
                        double lowerY = fMinY;
                        double upperY = fMaxY;
                        double padding = (upperY - lowerY) * 0.10;
                        if (padding == 0) padding = Math.max(1.0, upperY * 0.05);
                        lowerY = lowerY - padding;
                        upperY = upperY + padding;
                        yAxis.setAutoRanging(false);
                        yAxis.setLowerBound(lowerY);
                        yAxis.setUpperBound(upperY);
                        yAxis.setTickUnit(Math.max(1.0, (upperY - lowerY) / 8.0));
                        yAxis.setForceZeroInRange(false);
                    } else {
                        yAxis.setAutoRanging(true);
                    }
                });
             } catch (Exception ex) {

             }
         }, "hist-fetch-thread").start();
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

    private static DateTimeFormatter chooseFormatter(String days) {
        try {
            if ("max".equals(days)) {
                return DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault());
            } else {
                int d = Integer.parseInt(days);
                if (d <= 1) {
                    return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
                } else if (d <= 7) {
                    return DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault());
                } else if (d <= 90) {
                    return DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());
                } else if (d <= 365) {
                    return DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault());
                } else {
                    return DateTimeFormatter.ofPattern("yyyy").withZone(ZoneId.systemDefault());
                }
            }
        } catch (Exception ex) {
            return DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault());
        }
    }
}
