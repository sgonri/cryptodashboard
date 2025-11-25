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

public class CryptoService implements ICryptoService {
    private static final String DEFAULT_API_URL = "https://api.coingecko.com/api/v3";
    private static final int TOP_N = 5;
    private static final String PROPERTIES_PATH = "/application.properties";
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Properties props;
    private final CryptoCache cache;
    
    // Configurable retry delays for testability (in milliseconds)
    private int[] retryDelays = { 10000, 20000, 30000, 30000 };
    private int delayBetweenCalls = 5000;

    public CryptoService() {
        this(HttpClient.newHttpClient(), new CryptoCache(), loadDefaultProperties());
    }

    public CryptoService(CryptoCache cache) {
        this(HttpClient.newHttpClient(), cache, loadDefaultProperties());
    }

    public CryptoService(HttpClient httpClient, CryptoCache cache) {
        this(httpClient, cache, loadDefaultProperties());
    }
    
    /**
     * Full constructor for maximum testability
     * @param httpClient HTTP client for API calls (null uses default)
     * @param cache Cache instance (null creates new)
     * @param props Properties for configuration (null loads defaults)
     */
    public CryptoService(HttpClient httpClient, CryptoCache cache, Properties props) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient cannot be null");
        }
        if (cache == null) {
            throw new IllegalArgumentException("cache cannot be null");
        }
        this.httpClient = httpClient;
        this.cache = cache;
        this.props = props != null ? props : loadDefaultProperties();
    }
    
    /**
     * Set retry delays for testing (to avoid long waits in tests)
     */
    void setRetryDelays(int[] delays) {
        if (delays != null && delays.length > 0) {
            this.retryDelays = delays;
        }
    }
    
    /**
     * Set delay between API calls for testing
     */
    void setDelayBetweenCalls(int delay) {
        this.delayBetweenCalls = Math.max(0, delay);
    }
    
    private static Properties loadDefaultProperties() {
        Properties p = new Properties();
        try (InputStream is = CryptoService.class.getResourceAsStream(PROPERTIES_PATH)) {
            if (is != null) {
                p.load(is);
            }
        } catch (IOException e) {
            System.err.println("Failed to load properties: " + e.getMessage());
        }
        return p;
    }

    @Override
    public List<Crypto> getTopCryptos() {
        // Check cache first (fast path)
        if (cache.hasTopCryptos()) {
            System.out.println("Returning top cryptos from cache");
            return cache.getTopCryptos();
        }

        synchronized (this) {
            // Check cache again inside lock (double-checked locking)
            if (cache.hasTopCryptos()) {
                System.out.println("Returning top cryptos from cache (synced)");
                return cache.getTopCryptos();
            }

            // Fetch from API
            System.out.println("Fetching top cryptos from API...");
            List<Crypto> cryptos = fetchTopCryptosFromAPI();
            System.out.println("Fetched " + (cryptos == null ? "null" : cryptos.size()) + " cryptos from API");

            // Store in cache
            if (cryptos != null && !cryptos.isEmpty()) {
                cache.setTopCryptos(cryptos);
            }

            return cryptos;
        }
    }

    private List<Crypto> fetchTopCryptosFromAPI() {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        int maxRetries = retryDelays.length + 1; // Initial attempt + retries

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String url = String.format(
                        "%s/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=%d&page=1&sparkline=false&price_change_percentage=24h",
                        baseUrl, TOP_N);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET();

                // Add API key header
                /*
                 * String apiKey = props.getProperty("coingecko.api.key");
                 * if (apiKey != null && !apiKey.isBlank()) {
                 * reqBuilder.header("X-CG-API-KEY", apiKey);
                 * reqBuilder.header("Authorization", "Bearer " + apiKey);
                 * }
                 */

                HttpRequest request = reqBuilder.build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseCoinsJson(response.body());
                } else {
                    // Any non-2xx status code (including 429 rate limit) - retry with increasing
                    // delay
                    if (attempt < maxRetries - 1) {
                        int delay = retryDelays[attempt];
                        String statusMsg = response.statusCode() == 429 ? "Rate limit exceeded"
                                : "API returned status " + response.statusCode();
                        System.out.println(statusMsg + " for top cryptos. Waiting " + (delay / 1000)
                                + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return new ArrayList<>();
                        }
                        continue; // Retry the request
                    } else {
                        System.err.println("CoinGecko API returned non-2xx: " + response.statusCode());
                        return new ArrayList<>();
                    }
                }
            } catch (IOException | InterruptedException e) {
                // Network errors or interruptions - retry with increasing delay
                if (attempt < maxRetries - 1) {
                    int delay = retryDelays[attempt];
                    System.out.println("Network error for top cryptos (" + e.getMessage() + "). Waiting "
                            + (delay / 1000) + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new ArrayList<>();
                    }
                    continue; // Retry the request
                } else {
                    System.err.println("Failed to fetch CoinGecko data: " + e.getMessage());
                    return new ArrayList<>();
                }
            }
        }
        return new ArrayList<>();
    }

    private List<Crypto> parseCoinsJson(String json) throws IOException {
        List<Crypto> list = new ArrayList<>();
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray()) {
            System.err.println("parseCoinsJson: Root node is not an array");
            return new ArrayList<>();
        }

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
        System.out.println("Parsed " + list.size() + " coins from JSON");
        return list;
    }

    /**
     * Check if historical data is available in cache for a specific crypto and
     * interval
     */
    @Override
    public boolean hasHistoricalData(String id, String days) {
        if (id == null || id.isBlank() || days == null || days.isBlank()) {
            return false;
        }
        return cache.hasHistoricalData(id, days);
    }

    /**
     * fetch market_chart data for a single crypto id.
     * days accepts numeric day strings. Null/blank => "1".
     * Checks cache first before making API call.
     * Implements lazy loading: only fetches from API if not in cache.
     */
    @Override
    public HistoricalData getHistoricalDataForCrypto(String id, String days) {
        if (id == null || id.isBlank())
            return new HistoricalData(null);
        if (days == null || days.isBlank())
            days = "1";

        // Check cache first - if found, return immediately (no API call)
        if (cache.hasHistoricalData(id, days)) {
            return cache.getHistoricalData(id, days);
        }

        // Not in cache - fetch from API (lazy loading)
        // This happens when user selects a time interval that wasn't preloaded
        System.out.println("Loading historical data for " + id + " (days=" + days + ") from API...");
        HistoricalData data = fetchHistoricalDataFromAPI(id, days);

        // Store in cache for future use
        if (data != null && data.getPoints() != null && !data.getPoints().isEmpty()) {
            cache.putHistoricalData(id, days, data);
            System.out.println("Successfully loaded and cached data for " + id + " (days=" + days + ")");
        } else {
            System.err.println("Failed to load data for " + id + " (days=" + days + ")");
        }

        return data;
    }

    private HistoricalData fetchHistoricalDataFromAPI(String id, String days) {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        int maxRetries = retryDelays.length + 1; // Initial attempt + retries

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String url = String.format("%s/coins/%s/market_chart?vs_currency=usd&days=%s", baseUrl, id, days);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET();

                /*
                 * String apiKey = props.getProperty("coingecko.api.key");
                 * if (apiKey != null && !apiKey.isBlank()) {
                 * reqBuilder.header("X-CG-API-KEY", apiKey);
                 * reqBuilder.header("Authorization", "Bearer " + apiKey);
                 * }
                 */

                HttpRequest request = reqBuilder.build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseMarketChartJson(response.body());
                } else {
                    // Any non-2xx status code (including 429 rate limit) - retry with increasing
                    // delay
                    if (attempt < maxRetries - 1) {
                        int delay = retryDelays[attempt];
                        String statusMsg = response.statusCode() == 429 ? "Rate limit exceeded"
                                : "API returned status " + response.statusCode();
                        System.out.println(statusMsg + " for " + id + ". Waiting " + (delay / 1000)
                                + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return new HistoricalData(null);
                        }
                        continue; // Retry the request
                    } else {
                        // All retries exhausted
                        System.err.println("Failed to fetch market_chart for " + id + " after " + maxRetries
                                + " attempts. Last status: " + response.statusCode());
                        return new HistoricalData(null);
                    }
                }
            } catch (IOException | InterruptedException e) {
                // Network errors or interruptions - retry with increasing delay
                if (attempt < maxRetries - 1) {
                    int delay = retryDelays[attempt];
                    System.out.println("Network error for " + id + " (" + e.getMessage() + "). Waiting "
                            + (delay / 1000) + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new HistoricalData(null);
                    }
                    continue; // Retry the request
                } else {
                    // All retries exhausted
                    System.err.println("Failed to fetch market_chart for " + id + " after " + maxRetries + " attempts: "
                            + e.getMessage());
                    return new HistoricalData(null);
                }
            }
        }

        return new HistoricalData(null);
    }

    private ICryptoService.DataLoadedCallback dataLoadedCallback;

    // Track failed data loads for retry
    private final java.util.List<FailedDataLoad> failedLoads = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    // Track interval loading progress: map of interval -> count of loaded cryptos
    private final java.util.Map<String, Integer> intervalLoadCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private int totalCryptoCount = 0;

    private static class FailedDataLoad {
        final String cryptoId;
        final String days;
        final String intervalName;

        FailedDataLoad(String cryptoId, String days, String intervalName) {
            this.cryptoId = cryptoId;
            this.days = days;
            this.intervalName = intervalName;
        }
    }

    @Override
    public void setDataLoadedCallback(ICryptoService.DataLoadedCallback callback) {
        this.dataLoadedCallback = callback;
    }

    /**
     * Retry loading failed data
     */
    private void retryFailedLoads() {
        if (failedLoads.isEmpty()) {
            System.out.println("No failed loads to retry.");
            return;
        }

        System.out.println("Retrying " + failedLoads.size() + " failed data loads...");

        java.util.List<FailedDataLoad> toRetry = new java.util.ArrayList<>(failedLoads);
        failedLoads.clear();

        for (FailedDataLoad failed : toRetry) {
            try {
                System.out.println("Retrying " + failed.intervalName + " data for " + failed.cryptoId + "...");
                HistoricalData data = fetchHistoricalDataFromAPI(failed.cryptoId, failed.days);
                boolean success = false;
                if (data != null && data.getPoints() != null && !data.getPoints().isEmpty()) {
                    cache.putHistoricalData(failed.cryptoId, failed.days, data);
                    System.out.println("✓ Successfully loaded " + failed.intervalName + " data for " + failed.cryptoId);
                    success = true;
                    // Increment load count for this interval
                    intervalLoadCounts.put(failed.intervalName,
                            intervalLoadCounts.getOrDefault(failed.intervalName, 0) + 1);
                } else {
                    System.err
                            .println("✗ Still failed to load " + failed.intervalName + " data for " + failed.cryptoId);
                    // Add back to failed list
                    failedLoads.add(failed);
                }

                // Notify callback only if all cryptos have loaded this interval
                if (dataLoadedCallback != null && success) {
                    int loadedCount = intervalLoadCounts.getOrDefault(failed.intervalName, 0);
                    // Enable interval button only when all cryptos have loaded this interval
                    if (loadedCount == totalCryptoCount) {
                        dataLoadedCallback.onIntervalDataLoaded(null, failed.intervalName, true); // null cryptoId means
                                                                                                  // all loaded
                    }
                }

                // Wait between calls
                if (toRetry.indexOf(failed) < toRetry.size() - 1) {
                    Thread.sleep(delayBetweenCalls);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while retrying data for " + failed.cryptoId);
                break;
            } catch (Exception e) {
                System.err.println("Error retrying " + failed.intervalName + " data for " + failed.cryptoId + ": "
                        + e.getMessage());
                // Add back to failed list
                failedLoads.add(failed);
            }
        }

        System.out.println("Retry complete. " + failedLoads.size() + " loads still failed.");
    }

    /**
     * Preload cryptocurrency data at startup
     * Loads data in phases: first all 1D data, then longer intervals
     * Makes API calls with 5 second delay between calls
     * 
     * CoinGecko free API limit: 5-15 calls per minute
     * Strategy: 5 second delay = 12 calls/min (safe margin)
     */
    @Override
    public void preloadAllData() {
        System.out.println("Preloading cryptocurrency data...");

        // Fetch top cryptos (1 API call)
        List<Crypto> cryptos = getTopCryptos();
        totalCryptoCount = cryptos.size();
        System.out.println("Loaded " + cryptos.size() + " cryptocurrencies");

        // Initialize interval load counts
        intervalLoadCounts.clear();
        intervalLoadCounts.put("1D", 0);
        intervalLoadCounts.put("1W", 0);
        intervalLoadCounts.put("1M", 0);
        intervalLoadCounts.put("3M", 0);
        intervalLoadCounts.put("1Y", 0);

        // Time intervals to load (in order: 1D first, then longer intervals)
        String[] intervals = { "1", "7", "30", "90", "365" }; // 1D, 1W, 1M, 3M, 1Y
        String[] intervalNames = { "1D", "1W", "1M", "3M", "1Y" };

        // Delay between API calls: configurable, default 5 seconds = 12 calls/min
        // (safe margin under 15 calls/min limit)

        // Phase 1: Load 1D data for all cryptos first
        System.out.println("Phase 1: Loading 1D data for all cryptos...");
        for (Crypto crypto : cryptos) {
            System.out.println("Loading 1D data for " + crypto.getName() + "...");
            try {
                HistoricalData data = fetchHistoricalDataFromAPI(crypto.getId(), "1");
                boolean success = false;
                if (data != null && data.getPoints() != null && !data.getPoints().isEmpty()) {
                    cache.putHistoricalData(crypto.getId(), "1", data);
                    System.out.println("✓ Successfully loaded 1D data for " + crypto.getName());
                    success = true;
                    // Increment load count for 1D
                    intervalLoadCounts.put("1D", intervalLoadCounts.get("1D") + 1);
                } else {
                    System.err.println("✗ Failed to load 1D data for " + crypto.getName());
                }

                // Notify callback that this crypto's 1D data is loaded
                if (dataLoadedCallback != null) {
                    dataLoadedCallback.onDataLoaded(crypto.getId(), success);
                    // Check if all 1D data is loaded (1D is always enabled, but we track it anyway)
                    if (intervalLoadCounts.get("1D") == totalCryptoCount) {
                        dataLoadedCallback.onIntervalDataLoaded(null, "1D", true); // null cryptoId means all loaded
                    }
                }

                // Wait 5 seconds before next call (except for last crypto in phase 1)
                if (cryptos.indexOf(crypto) < cryptos.size() - 1) {
                    Thread.sleep(delayBetweenCalls);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while preloading 1D data for " + crypto.getName());
                break;
            } catch (Exception e) {
                System.err.println("Failed to preload 1D data for " + crypto.getName() + ": " + e.getMessage());
                // Notify callback even on failure
                if (dataLoadedCallback != null) {
                    dataLoadedCallback.onDataLoaded(crypto.getId(), false);
                }
            }
        }

        System.out.println("Phase 1 complete! All cryptos now have 1D data available.");
        System.out.println("Phase 2: Loading longer intervals (1W, 1M, 3M, 1Y)...");

        // Phase 2: Load longer intervals for all cryptos
        // Skip 1D (index 0) since we already loaded it
        for (int i = 1; i < intervals.length; i++) {
            String days = intervals[i];
            String intervalName = intervalNames[i];

            System.out.println("Loading " + intervalName + " data for all cryptos...");

            for (Crypto crypto : cryptos) {
                System.out.println("Loading " + intervalName + " data for " + crypto.getName() + "...");
                try {
                    HistoricalData data = fetchHistoricalDataFromAPI(crypto.getId(), days);
                    if (data != null && data.getPoints() != null && !data.getPoints().isEmpty()) {
                        cache.putHistoricalData(crypto.getId(), days, data);
                        System.out.println("✓ Successfully loaded " + intervalName + " data for " + crypto.getName());
                        // Increment load count for this interval
                        intervalLoadCounts.put(intervalName, intervalLoadCounts.get(intervalName) + 1);
                    } else {
                        System.err.println("✗ Failed to load " + intervalName + " data for " + crypto.getName());
                        // Track failed load for retry
                        failedLoads.add(new FailedDataLoad(crypto.getId(), days, intervalName));
                    }

                    // Notify callback about interval data load
                    // Only enable interval button when ALL cryptos have loaded this interval
                    if (dataLoadedCallback != null) {
                        int loadedCount = intervalLoadCounts.get(intervalName);
                        // Enable interval button only when all cryptos have loaded this interval
                        if (loadedCount == totalCryptoCount) {
                            dataLoadedCallback.onIntervalDataLoaded(null, intervalName, true); // null cryptoId means
                                                                                               // all loaded
                        }
                    }

                    // Wait 5 seconds before next call (except for last call)
                    if (i < intervals.length - 1 || cryptos.indexOf(crypto) < cryptos.size() - 1) {
                        Thread.sleep(delayBetweenCalls);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err
                            .println("Interrupted while preloading " + intervalName + " data for " + crypto.getName());
                    return;
                } catch (Exception e) {
                    System.err.println("Failed to preload " + intervalName + " data for " + crypto.getName() + ": "
                            + e.getMessage());
                    // Track failed load for retry
                    failedLoads.add(new FailedDataLoad(crypto.getId(), days, intervalName));
                    // Don't notify callback about individual failures - we only enable when all
                    // succeed
                }
            }
        }

        System.out.println("All data preloading complete!");

        // Retry failed loads once
        if (!failedLoads.isEmpty()) {
            System.out.println("Retrying " + failedLoads.size() + " failed data loads...");
            retryFailedLoads();
        }

        System.out.println("Preloading complete. Failed loads: " + failedLoads.size());
    }

    /**
     * Get count of failed data loads
     */
    @Override
    public int getFailedLoadsCount() {
        return failedLoads.size();
    }

    /**
     * Clear cache to allow refreshing all data
     */
    @Override
    public void clearCache() {
        cache.clear();
        failedLoads.clear();
        intervalLoadCounts.clear();
    }

    /**
     * Parse a market_chart JSON response into HistoricalData using prices and
     * total_volumes.
     * Expected arrays of [ [timestampMs, value], ... ]. If volumes array is
     * missing, volume will be null.
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
            if (!pNode.isArray() || pNode.size() < 2)
                continue;

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
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0)
            return "$0";
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
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0)
            return "0";
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
