package com.mycompany.app.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.app.models.ChartPoint;
import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;


public class CryptoService {
    private static final String DEFAULT_API_URL = "https://api.coingecko.com/api/v3";
    private static final int TOP_N = 5;
    private static final String path = "/application.properties";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Properties props = loadProperties();

    public List<Crypto> getTopCryptos() {
        try {
            String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
            String url = String.format("%s/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=%d&page=1&sparkline=false&price_change_percentage=24h", baseUrl, TOP_N);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();

            // Add API key header
            String apiKey = props.getProperty("coingecko.api.key");
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("X-CG-API-KEY", apiKey);
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest request = reqBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseCoinsJson(response.body());
            } else {
                System.err.println("CoinGecko API returned non-2xx: " + response.statusCode());
                return new ArrayList<>();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to fetch CoinGecko data: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Crypto> parseCoinsJson(String json) throws IOException {
        List<Crypto> list = new ArrayList<>();
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray()) return new ArrayList<>();

        for (JsonNode node : arr) {
            String id = node.path("id").asText("");
            String name = node.path("name").asText("");
            String symbol = node.path("symbol").asText("").toUpperCase();
            double price = node.path("current_price").asDouble(0.0);
            // CoinGecko returns price change percentage in requested field
            double changePct = node.path("price_change_percentage_24h").asDouble(0.0);
            double marketCapNum = node.path("market_cap").asDouble(0.0);
            double volumeNum = node.path("total_volume").asDouble(0.0);
            double circulating = node.path("circulating_supply").asDouble(0.0);

            String marketCap = formatMoneyShort(marketCapNum);
            String volume = formatMoneyShort(volumeNum);
            String circulatingStr = formatNumberShort(circulating);

            list.add(new Crypto(id, name, symbol, price, changePct, marketCap, volume, circulatingStr));
        }

        return list;
    }

    /**
     * fetch market_chart data for a single crypto id.
     * days accepts numeric day strings. Null/blank => "1".
     */
    public HistoricalData getHistoricalDataForCrypto(String id, String days) {
        if (id == null || id.isBlank()) return new HistoricalData(null);
        if (days == null || days.isBlank()) days = "1";
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        try {
            String url = String.format("%s/coins/%s/market_chart?vs_currency=usd&days=%s", baseUrl, id, days);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();

            
            String apiKey = props.getProperty("coingecko.api.key");
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("X-CG-API-KEY", apiKey);
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest request = reqBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseMarketChartJson(response.body());
            } else {
                System.err.println("CoinGecko market_chart returned non-2xx for " + id + ": " + response.statusCode());
                return new HistoricalData(null);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to fetch market_chart for " + id + ": " + e.getMessage());
            return new HistoricalData(null);
        }
    }

    private Properties loadProperties() {
        Properties p = new Properties();
        
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                p.load(is);
            }
        } catch (IOException e) {
            System.err.println("Failed to load properties from " + path + ": " + e.getMessage());
        }
        return p;
    }

    /**
     * Parse a market_chart JSON response into HistoricalData using prices and total_volumes.
     * Expected arrays of [ [timestampMs, value], ... ]. If volumes array is missing, volume will be null.
     */
    private HistoricalData parseMarketChartJson(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode prices = root.path("prices");
        JsonNode volumes = root.path("total_volumes");

        if (!prices.isArray()) {
            return new HistoricalData(null);
        }

        List<ChartPoint> points = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            JsonNode pNode = prices.get(i);
            if (!pNode.isArray() || pNode.size() < 2) continue;

            long ts = pNode.get(0).asLong(0L);
            Double price = pNode.get(1).isNull() ? null : pNode.get(1).asDouble();

            Double vol = null;
            if (volumes.isArray() && i < volumes.size()) {
                JsonNode vNode = volumes.get(i);
                if (vNode.isArray() && vNode.size() >= 2) {
                    vol = vNode.get(1).isNull() ? null : vNode.get(1).asDouble();
                }
            }

            try {
                ChartPoint cp = new ChartPoint(Instant.ofEpochMilli(ts), price, vol);
                points.add(cp);
            } catch (Exception ex) {
                // skip malformed timestamps
            }
        }

        return new HistoricalData(points);
    }

    private static String formatMoneyShort(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) return "$0";
        double abs = Math.abs(value);
        DecimalFormat df = new DecimalFormat("#,##0.##");
        if (abs >= 1_000_000_000_000.0) {
            return "$" + df.format(value / 1_000_000_000_000.0) + "T";
        } else if (abs >= 1_000_000_000.0) {
            return "$" + df.format(value / 1_000_000_000.0) + "B";
        } else if (abs >= 1_000_000.0) {
            return "$" + df.format(value / 1_000_000.0) + "M";
        } else if (abs >= 1_000.0) {
            return "$" + df.format(value / 1_000.0) + "K";
        } else {
            return "$" + df.format(value);
        }
    }

    private static String formatNumberShort(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) return "0";
        double abs = Math.abs(value);
        DecimalFormat df = new DecimalFormat("#,##0.##");
        if (abs >= 1_000_000_000_000.0) {
            return df.format(value / 1_000_000_000_000.0) + "T";
        } else if (abs >= 1_000_000_000.0) {
            return df.format(value / 1_000_000_000.0) + "B";
        } else if (abs >= 1_000_000.0) {
            return df.format(value / 1_000_000.0) + "M";
        } else if (abs >= 1_000.0) {
            return df.format(value / 1_000.0) + "K";
        } else {
            return df.format(value);
        }
    }
}
