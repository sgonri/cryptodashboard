package com.mycompany.app.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.app.models.Crypto;

/**
 * Live-ish crypto service using CoinGecko's public API.
 * This class replaces the old static mock data with a call to
 * the CoinGecko /coins/markets endpoint to fetch the top N by market cap.
 *
 * If the HTTP call or parsing fails, this falls back to the original static list.
 */
public class MockCryptoService implements CryptoService {
    private static final String DEFAULT_API_URL = "https://api.coingecko.com/api/v3";
    private static final int TOP_N = 5;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Properties props = loadProperties();

    @Override
    public List<Crypto> getTopCryptos() {
        try {
            String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
            String url = String.format("%s/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=%d&page=1&sparkline=false&price_change_percentage=24h", baseUrl, TOP_N);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();

            // Add API key header if provided (harmless if API doesn't require it)
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
                return fallbackList();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to fetch CoinGecko data: " + e.getMessage());
            return fallbackList();
        }
    }

    private List<Crypto> parseCoinsJson(String json) throws IOException {
        List<Crypto> list = new ArrayList<>();
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray()) return fallbackList();

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

    private Properties loadProperties() {
        Properties p = new Properties();
        // Try application.properties first, then fallback to template
        String[] candidates = {"/application.properties", "/application.properties.template"};
        for (String c : candidates) {
            try (InputStream is = getClass().getResourceAsStream(c)) {
                if (is != null) {
                    p.load(is);
                    break;
                }
            } catch (IOException e) {
                // continue
            }
        }
        return p;
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

    private List<Crypto> fallbackList() {
        List<Crypto> list = new ArrayList<>();
        list.add(new Crypto("xrp", "XRP", "XRP", 6.00, 100, "$33.0B", "$800M", "50.0B"));
        list.add(new Crypto("btc", "Bitcoin", "BTC", 63421.00, 3.5, "$1.2T", "$32B", "19.0M"));
        list.add(new Crypto("eth", "Ethereum", "ETH", 3456.78, 2.37, "$415.6B", "$20B", "118.0M"));
        list.add(new Crypto("sol", "Solana", "SOL", 178.90, 3.58, "$35.8B", "$2.5B", "300.0M"));
        list.add(new Crypto("ada", "Cardano", "ADA", 2.34, 1.96, "$45.6B", "$1.8B", "32.0B"));
        list.add(new Crypto("bnb", "Binance Coin", "BNB", 456.78, 0.91, "$75.3B", "$1.5B", "168.0M"));
        return list;
    }
}
