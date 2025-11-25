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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    
    void setRetryDelays(int[] delays) {
        if (delays != null && delays.length > 0) {
            this.retryDelays = delays;
        }
    }
    
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
        if (cache.hasTopCryptos()) {
            System.out.println("Returning top cryptos from cache");
            return cache.getTopCryptos();
        }

        synchronized (this) {
            if (cache.hasTopCryptos()) {
                System.out.println("Returning top cryptos from cache (synced)");
                return cache.getTopCryptos();
            }

            System.out.println("Fetching top cryptos from API...");
            List<Crypto> cryptos = fetchTopCryptosFromAPI();
            System.out.println("Fetched " + (cryptos == null ? "null" : cryptos.size()) + " cryptos from API");

            if (cryptos != null && !cryptos.isEmpty()) {
                cache.setTopCryptos(cryptos);
            }

            return cryptos;
        }
    }

    private List<Crypto> fetchTopCryptosFromAPI() {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        int maxRetries = retryDelays.length + 1;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String url = String.format(
                        "%s/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=%d&page=1&sparkline=false&price_change_percentage=24h",
                        baseUrl, TOP_N);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET();

                String apiKey = props.getProperty("coingecko.api.key");
                if (apiKey != null && !apiKey.isBlank()) {
                    reqBuilder.header("x-cg-demo-api-key", apiKey);
                }

                HttpRequest request = reqBuilder.build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseCoinsJson(response.body());
                } else {
                    if (attempt < maxRetries - 1) {
                        int delay = retryDelays[attempt];
                        String statusMsg = response.statusCode() == 429 ? "Rate limit exceeded"
                                : "API returned status " + response.statusCode();
                        System.out.println(statusMsg + " for top cryptos. Waiting " + (delay / 1000)
                                + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                        Thread.sleep(delay);
                        continue;
                    } else {
                        System.err.println("CoinGecko API returned non-2xx: " + response.statusCode());
                        return new ArrayList<>();
                    }
                }
            } catch (IOException | InterruptedException e) {
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
                    continue;
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

    @Override
    public boolean hasHistoricalData(String id, String days) {
        if (id == null || id.isBlank() || days == null || days.isBlank()) {
            return false;
        }
        return cache.hasHistoricalData(id, days);
    }

    @Override
    public HistoricalData getHistoricalDataForCrypto(String id, String days) {
        if (id == null || id.isBlank())
            return new HistoricalData(null);
        if (days == null || days.isBlank())
            days = "1";

        if (cache.hasHistoricalData(id, days)) {
            return cache.getHistoricalData(id, days);
        }

        System.out.println("Loading historical data for " + id + " (days=" + days + ") from API...");
        HistoricalData data = fetchHistoricalDataFromAPI(id, days);

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
        int maxRetries = retryDelays.length + 1;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String url = String.format("%s/coins/%s/market_chart?vs_currency=usd&days=%s", baseUrl, id, days);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET();

                String apiKey = props.getProperty("coingecko.api.key");
                if (apiKey != null && !apiKey.isBlank()) {
                    reqBuilder.header("x-cg-demo-api-key", apiKey);
                }

                HttpRequest request = reqBuilder.build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseMarketChartJson(response.body());
                } else {
                    if (attempt < maxRetries - 1) {
                        int delay = retryDelays[attempt];
                        String statusMsg = response.statusCode() == 429 ? "Rate limit exceeded"
                                : "API returned status " + response.statusCode();
                        System.out.println(statusMsg + " for " + id + ". Waiting " + (delay / 1000)
                                + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                        Thread.sleep(delay);
                        continue;
                    } else {
                        System.err.println("Failed to fetch market_chart for " + id + " after " + maxRetries
                                + " attempts. Last status: " + response.statusCode());
                        return new HistoricalData(null);
                    }
                }
            } catch (IOException | InterruptedException e) {
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
                    continue;
                } else {
                    System.err.println("Failed to fetch market_chart for " + id + " after " + maxRetries + " attempts: "
                            + e.getMessage());
                    return new HistoricalData(null);
                }
            }
        }
        return new HistoricalData(null);
    }

    private ICryptoService.DataLoadedCallback dataLoadedCallback;
    private final java.util.List<FailedDataLoad> failedLoads = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
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

    @Override
    public void preloadAllData() {
        System.out.println("Preloading cryptocurrency data (parallel mode)...");

        List<Crypto> cryptos = getTopCryptos();
        totalCryptoCount = cryptos.size();
        System.out.println("Loaded " + cryptos.size() + " cryptocurrencies");

        intervalLoadCounts.clear();
        String[] intervalNames = { "1D", "1W", "1M", "3M", "1Y" };
        for (String name : intervalNames) {
            intervalLoadCounts.put(name, 0);
        }

        String[] intervals = { "1", "7", "30", "90", "365" };

        List<FetchTask> allTasks = new ArrayList<>();
        for (Crypto crypto : cryptos) {
            for (int i = 0; i < intervals.length; i++) {
                allTasks.add(new FetchTask(crypto.getId(), crypto.getName(), intervals[i], intervalNames[i]));
            }
        }

        System.out.println("Starting " + allTasks.size() + " parallel API calls...");
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<FetchResult>> futures = new ArrayList<>();
        for (FetchTask task : allTasks) {
            CompletableFuture<FetchResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    HistoricalData data = fetchHistoricalDataFromAPINoRetry(task.cryptoId, task.days);
                    boolean success = data != null && data.getPoints() != null && !data.getPoints().isEmpty();
                    return new FetchResult(task, data, success);
                } catch (Exception e) {
                    System.err.println("Error fetching " + task.intervalName + " for " + task.cryptoName + ": " + e.getMessage());
                    return new FetchResult(task, null, false);
                }
            });
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<FetchTask> failedTasks = new ArrayList<>();
        ConcurrentHashMap<String, AtomicInteger> intervalSuccessCounts = new ConcurrentHashMap<>();
        for (String name : intervalNames) {
            intervalSuccessCounts.put(name, new AtomicInteger(0));
        }

        for (CompletableFuture<FetchResult> future : futures) {
            try {
                FetchResult result = future.get();
                if (result.success) {
                    cache.putHistoricalData(result.task.cryptoId, result.task.days, result.data);
                    System.out.println("✓ " + result.task.intervalName + " for " + result.task.cryptoName);
                    intervalSuccessCounts.get(result.task.intervalName).incrementAndGet();
                    
                    if (result.task.days.equals("1") && dataLoadedCallback != null) {
                        final String cryptoId = result.task.cryptoId;
                        javafx.application.Platform.runLater(() -> {
                            dataLoadedCallback.onDataLoaded(cryptoId, true);
                        });
                    }
                } else {
                    System.err.println("✗ " + result.task.intervalName + " for " + result.task.cryptoName + " - will retry");
                    failedTasks.add(result.task);
                }
            } catch (Exception e) {
                System.err.println("Error processing result: " + e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Parallel phase complete in " + elapsed + "ms. Success: " + (allTasks.size() - failedTasks.size()) + "/" + allTasks.size());

        for (String intervalName : intervalNames) {
            int count = intervalSuccessCounts.get(intervalName).get();
            intervalLoadCounts.put(intervalName, count);
            if (count == totalCryptoCount && dataLoadedCallback != null) {
                final String interval = intervalName;
                javafx.application.Platform.runLater(() -> {
                    dataLoadedCallback.onIntervalDataLoaded(null, interval, true);
                });
            }
        }

        if (!failedTasks.isEmpty()) {
            System.out.println("Retrying " + failedTasks.size() + " failed calls in batches...");
            retryInBatches(failedTasks, intervalSuccessCounts, 5, 2000);
        }

        System.out.println("All data preloading complete!");
    }

    private void retryInBatches(List<FetchTask> failedTasks, ConcurrentHashMap<String, AtomicInteger> intervalSuccessCounts, 
                                 int batchSize, int delayBetweenBatchesMs) {
        List<FetchTask> remaining = new ArrayList<>(failedTasks);
        int retryAttempt = 0;
        int maxRetryAttempts = 3;

        while (!remaining.isEmpty() && retryAttempt < maxRetryAttempts) {
            retryAttempt++;
            System.out.println("Retry attempt " + retryAttempt + "/" + maxRetryAttempts + " for " + remaining.size() + " tasks...");
            
            List<FetchTask> stillFailed = new ArrayList<>();
            
            for (int i = 0; i < remaining.size(); i += batchSize) {
                int end = Math.min(i + batchSize, remaining.size());
                List<FetchTask> batch = remaining.subList(i, end);
                
                System.out.println("Processing batch of " + batch.size() + " calls...");
                
                List<CompletableFuture<FetchResult>> batchFutures = new ArrayList<>();
                for (FetchTask task : batch) {
                    CompletableFuture<FetchResult> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            HistoricalData data = fetchHistoricalDataFromAPINoRetry(task.cryptoId, task.days);
                            boolean success = data != null && data.getPoints() != null && !data.getPoints().isEmpty();
                            return new FetchResult(task, data, success);
                        } catch (Exception e) {
                            return new FetchResult(task, null, false);
                        }
                    });
                    batchFutures.add(future);
                }
                
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
                
                for (CompletableFuture<FetchResult> future : batchFutures) {
                    try {
                        FetchResult result = future.get();
                        if (result.success) {
                            cache.putHistoricalData(result.task.cryptoId, result.task.days, result.data);
                            System.out.println("✓ Retry success: " + result.task.intervalName + " for " + result.task.cryptoName);
                            intervalSuccessCounts.get(result.task.intervalName).incrementAndGet();
                            
                            if (result.task.days.equals("1") && dataLoadedCallback != null) {
                                final String cryptoId = result.task.cryptoId;
                                javafx.application.Platform.runLater(() -> {
                                    dataLoadedCallback.onDataLoaded(cryptoId, true);
                                });
                            }
                            
                            int count = intervalSuccessCounts.get(result.task.intervalName).get();
                            intervalLoadCounts.put(result.task.intervalName, count);
                            if (count == totalCryptoCount && dataLoadedCallback != null) {
                                final String interval = result.task.intervalName;
                                javafx.application.Platform.runLater(() -> {
                                    dataLoadedCallback.onIntervalDataLoaded(null, interval, true);
                                });
                            }
                        } else {
                            stillFailed.add(result.task);
                        }
                    } catch (Exception e) {
                        // Keep task in failed list
                    }
                }
                
                if (end < remaining.size()) {
                    try {
                        Thread.sleep(delayBetweenBatchesMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            
            remaining = stillFailed;
            
            if (!remaining.isEmpty() && retryAttempt < maxRetryAttempts) {
                try {
                    System.out.println("Waiting 5s before next retry attempt...");
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        for (FetchTask task : remaining) {
            failedLoads.add(new FailedDataLoad(task.cryptoId, task.days, task.intervalName));
        }
        
        if (!remaining.isEmpty()) {
            System.err.println(remaining.size() + " tasks still failed after all retries.");
        }
    }

    private HistoricalData fetchHistoricalDataFromAPINoRetry(String id, String days) {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        
        try {
            String url = String.format("%s/coins/%s/market_chart?vs_currency=usd&days=%s", baseUrl, id, days);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();

            String apiKey = props.getProperty("coingecko.api.key");
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("x-cg-demo-api-key", apiKey);
            }

            HttpRequest request = reqBuilder.build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseMarketChartJson(response.body());
            } else {
                System.err.println("API returned " + response.statusCode() + " for " + id + " days=" + days);
                return null;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error fetching " + id + " days=" + days + ": " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static class FetchTask {
        final String cryptoId;
        final String cryptoName;
        final String days;
        final String intervalName;

        FetchTask(String cryptoId, String cryptoName, String days, String intervalName) {
            this.cryptoId = cryptoId;
            this.cryptoName = cryptoName;
            this.days = days;
            this.intervalName = intervalName;
        }
    }

    private static class FetchResult {
        final FetchTask task;
        final HistoricalData data;
        final boolean success;

        FetchResult(FetchTask task, HistoricalData data, boolean success) {
            this.task = task;
            this.data = data;
            this.success = success;
        }
    }

    @Override
    public int getFailedLoadsCount() {
        return failedLoads.size();
    }

    @Override
    public void clearCache() {
        cache.clear();
        failedLoads.clear();
        intervalLoadCounts.clear();
    }

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
