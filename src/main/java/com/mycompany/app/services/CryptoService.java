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

/**
 * Service responsible for fetching cryptocurrency information from the CoinGecko API
 * and caching results for use by the UI. This class encapsulates both synchronous
 * and asynchronous operations:
 *
 * - Retrieving the top N cryptocurrencies by market cap (`getTopCryptos`).
 * - Fetching historical time series data for a single currency (`getHistoricalDataForCrypto`).
 * - Preloading historical data for the top cryptocurrencies in parallel
 *   (`preloadAllData`) with batched retries for resiliency.
 *
 * Design notes and responsibilities:
 * - Uses a `CryptoCache` instance to avoid repeated network calls.
 * - Uses `HttpClient` for HTTP requests; the client can be injected for testing.
 * - Read-only properties are loaded from `application.properties` when present.
 * - Provides configurable retry delays (useful for tests) via `setRetryDelays`.
 * - Not thread-safe for mutation of configuration, but read operations and
 *   parallel preload use careful synchronization and concurrent collections.
 */
public class CryptoService implements ICryptoService {
    // Default API endpoint; override via properties if necessary.
    private static final String DEFAULT_API_URL = "https://api.coingecko.com/api/v3";
    // How many top coins to fetch for the main list.
    private static final int TOP_N = 5;
    // Path inside resources for properties used by the service.
    private static final String PROPERTIES_PATH = "/application.properties";

    // Http client used for all outgoing requests. Injected to support testing.
    private final HttpClient httpClient;
    // Jackson mapper for JSON parsing.
    private final ObjectMapper mapper = new ObjectMapper();
    // Loaded properties from resources (may be empty if not present).
    private final Properties props;
    // Local cache to store top list and historical data to limit API calls.
    private final CryptoCache cache;

    // Configurable retry delays (milliseconds). Default values are conservative to
    // cope with rate-limiting; tests may override these via `setRetryDelays`.
    private int[] retryDelays = { 10000, 20000, 30000, 30000 };
    // Delay used between batches when retrying failed requests during preload.
    private int delayBetweenCalls = 5000;

    /**
     * Default constructor used by the application. Creates a new HttpClient and
     * a new in-memory `CryptoCache`, and attempts to load properties.
     */
    public CryptoService() {
        this(HttpClient.newHttpClient(), new CryptoCache(), loadDefaultProperties());
    }

    /**
     * Convenience constructor allowing a pre-existing `CryptoCache` to be used.
     * This is useful in tests to pre-populate the cache or verify cache behavior.
     */
    public CryptoService(CryptoCache cache) {
        this(HttpClient.newHttpClient(), cache, loadDefaultProperties());
    }

    /**
     * Allows injection of an `HttpClient` and `CryptoCache`. Useful for tests
     * to provide a mock HTTP client.
     */
    public CryptoService(HttpClient httpClient, CryptoCache cache) {
        this(httpClient, cache, loadDefaultProperties());
    }
    
    /**
     * Full constructor for maximum testability and control.
     *
     * @param httpClient HTTP client to use for outbound requests (must not be null)
     * @param cache      Cache instance used to store results (must not be null)
     * @param props      Optional properties to configure endpoints and API keys.
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
     * Replace the retry delays used by the service. Intended for tests only.
     *
     * @param delays non-empty array of millisecond delays used between retries
     */
    void setRetryDelays(int[] delays) {
        if (delays != null && delays.length > 0) {
            this.retryDelays = delays;
        }
    }
    
    /**
     * Configure the delay placed between batched retry calls (milliseconds).
     * Minimum value is 0.
     */
    void setDelayBetweenCalls(int delay) {
        this.delayBetweenCalls = Math.max(0, delay);
    }
    
    /**
     * Load properties from `/application.properties` on the classpath. If the
     * file cannot be found or read, an empty Properties instance is returned.
     */
    private static Properties loadDefaultProperties() {
        Properties p = new Properties();
        try (InputStream is = CryptoService.class.getResourceAsStream(PROPERTIES_PATH)) {
            if (is != null) {
                p.load(is);
            }
        } catch (IOException e) {
            // Don't fail hard if properties are absent; log for debugging.
            System.err.println("Failed to load properties: " + e.getMessage());
        }
        return p;
    }

    /**
     * Retrieve the top cryptocurrencies (by market cap). The method first checks
     * the local cache and returns cached data if present. If not cached, it will
     * fetch the list from CoinGecko and populate the cache.
     *
     * This method uses a simple synchronized block to ensure only one thread
     * performs the initial network fetch and cache population.
     *
     * @return List of `Crypto` objects. May be empty if the API call fails.
     */
    @Override
    public List<Crypto> getTopCryptos() {
        if (cache.hasTopCryptos()) {
            System.out.println("Returning top cryptos from cache");
            return cache.getTopCryptos();
        }

        // Double-checked locking: only one thread should perform the network fetch
        synchronized (this) {
            if (cache.hasTopCryptos()) {
                System.out.println("Returning top cryptos from cache (synced)");
                return cache.getTopCryptos();
            }

            // Cache miss: perform a single network fetch and populate cache
            // so subsequent callers can retrieve data without extra API calls.
            System.out.println("Fetching top cryptos from API...");
            List<Crypto> cryptos = fetchTopCryptosFromAPI();
            System.out.println("Fetched " + (cryptos == null ? "null" : cryptos.size()) + " cryptos from API");

            if (cryptos != null && !cryptos.isEmpty()) {
                // Only store non-empty results
                cache.setTopCryptos(cryptos);
            }

            return cryptos;
        }
    }

    /**
     * Internal helper that performs the HTTP request to fetch the top coins and
     * translates the JSON response into domain objects.
     *
     * The method implements a retry loop: on transient errors (network issues or
     * rate-limiting responses) it will wait and retry using configured delays.
     * Non-2xx final responses produce an empty list.
     */
    private List<Crypto> fetchTopCryptosFromAPI() {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        int maxRetries = retryDelays.length + 1;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Build CoinGecko markets endpoint URL with query parameters
                // (currency, ordering, page size and additional options).
                String url = String.format(
                    "%s/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=%d&page=1&sparkline=false&price_change_percentage=24h",
                    baseUrl, TOP_N);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET();

                // Optional API key header — present only in some environments
                String apiKey = props.getProperty("coingecko.api.key");
                if (apiKey != null && !apiKey.isBlank()) {
                    reqBuilder.header("x-cg-demo-api-key", apiKey);
                }

                // Build the HTTP request and execute it using the injected HttpClient.
                // This call is blocking; higher-level methods manage retry/backoff.
                HttpRequest request = reqBuilder.build();
                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                // Successful response: parse and return results
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseCoinsJson(response.body());
                } else {
                    // For non-success responses, either retry or return empty list
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
                // On network errors, honor retry policy and handle interrupts
                if (attempt < maxRetries - 1) {
                    int delay = retryDelays[attempt];
                    System.out.println("Network error for top cryptos (" + e.getMessage() + "). Waiting "
                            + (delay / 1000) + " seconds before retry " + (attempt + 2) + "/" + maxRetries);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        // Restore interrupt status and abort
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

    /**
     * Parse JSON array returned by the `/coins/markets` endpoint into a list of
     * `Crypto` domain objects. Missing fields are handled gracefully by
     * providing sensible defaults.
     */
    private List<Crypto> parseCoinsJson(String json) throws IOException {
        List<Crypto> list = new ArrayList<>();
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray()) {
            System.err.println("parseCoinsJson: Root node is not an array");
            return new ArrayList<>();
        }

        // Each array element represents a coin object; extract fields safely
        for (JsonNode node : arr) {
            // Use safe accessors to avoid exceptions when fields are missing
            String id = node.path("id").asText("");
            String name = node.path("name").asText("");
            String symbol = node.path("symbol").asText("").toUpperCase();
            double price = node.path("current_price").asDouble(0.0);
            double changePct = node.path("price_change_percentage_24h").asDouble(0.0);
            double marketCapNum = node.path("market_cap").asDouble(0.0);
            double volumeNum = node.path("total_volume").asDouble(0.0);
            double circulating = node.path("circulating_supply").asDouble(0.0);

            // Format numeric values for display in the UI
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

    /**
     * Get historical time series for a given cryptocurrency and time window.
     *
     * Behavior:
     * - Validates inputs, uses default of 1 day if `days` is not provided.
     * - Returns cached data if present.
     * - Otherwise fetches from the API and caches the result if valid.
     *
     * @param id   Coin identifier used by CoinGecko (e.g. "bitcoin")
     * @param days Number of days of history to load (string form, e.g. "1")
     * @return `HistoricalData` instance; may contain null/empty points on failure.
     */
    @Override
    public HistoricalData getHistoricalDataForCrypto(String id, String days) {
        // Validate inputs and set sensible defaults. `days` defaults to "1"
        // (a single-day timeseries) when not provided by the caller.
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

    /**
     * Helper for fetching market chart data with retry logic similar to the
     * top coins fetcher. Returns a HistoricalData object, which may be empty on
     * failure.
     */
    private HistoricalData fetchHistoricalDataFromAPI(String id, String days) {
        String baseUrl = props.getProperty("coingecko.api.url", DEFAULT_API_URL);
        int maxRetries = retryDelays.length + 1;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Build market_chart endpoint URL which returns time series data
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

    // Callback invoked when single-interval data is loaded (used by the UI)
    private ICryptoService.DataLoadedCallback dataLoadedCallback;
    // Thread-safe list of failed loads collected during preload/retries
    private final java.util.List<FailedDataLoad> failedLoads = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    // Map holding counts of successful interval loads (keyed by interval name)
    private final java.util.Map<String, Integer> intervalLoadCounts = new java.util.concurrent.ConcurrentHashMap<>();
    // Number of cryptos being processed during preload; used to determine when an interval is complete
    private int totalCryptoCount = 0;

    /**
     * Records a failed data load to be inspected later.
     */
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
     * Preload historical data for the top cryptocurrencies in parallel. This
     * method:
     *
     * 1. Retrieves the top cryptos (from cache or API).
     * 2. Builds a list of fetch tasks covering several intervals (1,7,30,90,365).
     * 3. Executes the tasks in parallel using `CompletableFuture`.
     * 4. Caches successful results and collects failures for batched retries.
     *
     * The method reports progress to `dataLoadedCallback` and updates
     * `intervalLoadCounts` to signal when a full interval has completed for
     * all cryptos. The implementation is resilient and performs batch retries
     * for failed calls.
     */
    @Override
    public void preloadAllData() {
        System.out.println("Preloading cryptocurrency data (parallel mode)...");

        List<Crypto> cryptos = getTopCryptos();
        totalCryptoCount = cryptos.size();
        System.out.println("Loaded " + cryptos.size() + " cryptocurrencies");

        // Initialize counts per interval
        intervalLoadCounts.clear();
        String[] intervalNames = { "1D", "1W", "1M", "3M", "1Y" };
        for (String name : intervalNames) {
            intervalLoadCounts.put(name, 0);
        }

        // Days values corresponding to the interval names above
        String[] intervals = { "1", "7", "30", "90", "365" };

        // Build a full task list for every coin x interval combination
        List<FetchTask> allTasks = new ArrayList<>();
        for (Crypto crypto : cryptos) {
            for (int i = 0; i < intervals.length; i++) {
                allTasks.add(new FetchTask(crypto.getId(), crypto.getName(), intervals[i], intervalNames[i]));
            }
        }

        System.out.println("Starting " + allTasks.size() + " parallel API calls...");
        long startTime = System.currentTimeMillis();

        // Launch all tasks asynchronously
        List<CompletableFuture<FetchResult>> futures = new ArrayList<>();
        for (FetchTask task : allTasks) {
            // Submit an asynchronous supplier task to the default executor
            // (typically the ForkJoinPool.commonPool()). Each task performs
            // a single HTTP fetch without retry; failures are handled later.
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

        // Wait for all parallel requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<FetchTask> failedTasks = new ArrayList<>();
        ConcurrentHashMap<String, AtomicInteger> intervalSuccessCounts = new ConcurrentHashMap<>();
        for (String name : intervalNames) {
            intervalSuccessCounts.put(name, new AtomicInteger(0));
        }

        // Process results: cache successes, collect failures
        for (CompletableFuture<FetchResult> future : futures) {
            try {
                FetchResult result = future.get();
                if (result.success) {
                    cache.putHistoricalData(result.task.cryptoId, result.task.days, result.data);
                    System.out.println("✓ " + result.task.intervalName + " for " + result.task.cryptoName);
                    intervalSuccessCounts.get(result.task.intervalName).incrementAndGet();
                    
                    // Notify UI of single-day data loads on the JavaFX thread
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

        // Update interval-level counts and notify when an entire interval completes
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

        // If there were failures, attempt batched retries
        if (!failedTasks.isEmpty()) {
            System.out.println("Retrying " + failedTasks.size() + " failed calls in batches...");
            // Use the configured delay between calls so tests can control timing
            // (tests set this to 0 for faster execution). This avoids hardcoding
            // the delay and prevents the `delayBetweenCalls` field from being
            // effectively unused.
            retryInBatches(failedTasks, intervalSuccessCounts, 5, this.delayBetweenCalls);
        }

        System.out.println("All data preloading complete!");
    }

    /**
     * Retry failed fetches in small batches with a limited number of attempts.
     * The method updates success counts and records any permanently failed tasks
     * in `failedLoads` for later inspection.
     */
    private void retryInBatches(List<FetchTask> failedTasks, ConcurrentHashMap<String, AtomicInteger> intervalSuccessCounts, 
                                 int batchSize, int delayBetweenBatchesMs) {
        List<FetchTask> remaining = new ArrayList<>(failedTasks);
        int retryAttempt = 0;
        int maxRetryAttempts = 3;

        while (!remaining.isEmpty() && retryAttempt < maxRetryAttempts) {
            retryAttempt++;
            System.out.println("Retry attempt " + retryAttempt + "/" + maxRetryAttempts + " for " + remaining.size() + " tasks...");
            
            List<FetchTask> stillFailed = new ArrayList<>();
            
            // Process remaining failed tasks in controlled-size batches to
            // avoid creating too many concurrent requests.
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
                        // Keep task in failed list if something goes wrong while processing
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

    /**
     * Fetch historical data without any retry logic. Used when the caller wants
     * to control retries separately (for example inside batch retry loops).
     */
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

    /**
     * Lightweight DTO describing a work item: which crypto, which days, and a
     * human-friendly interval name used for logging and UI callbacks.
     */
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

    /**
     * Result holder for asynchronous fetches. Contains the original task, the
     * parsed data (if any) and a boolean success flag.
     */
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

    /**
     * Parse the `market_chart` JSON response into `HistoricalData`.
     *
     * The `prices` array contains [timestamp, price] entries and `total_volumes`
     * contains [timestamp, volume]. The method pairs entries by index; if the
     * arrays are misaligned or a particular entry is malformed, the method
     * skips the problematic entry and continues.
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
                // Convert epoch milliseconds to Instant and create ChartPoint
                ChartPoint cp = new ChartPoint(Instant.ofEpochMilli(ts), price, vol);
                points.add(cp);
            } catch (Exception ex) {
                // skip malformed timestamps to keep the rest of the data usable
            }
        }

        return new HistoricalData(points);
    }

    /**
     * Format a currency value to a compact string (e.g. 1_500 -> "$1.5K").
     * Handles large values up to trillions and avoids negative/NaN outputs.
     */
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

    /**
     * Format a general large number to a compact representation (no currency
     * symbol). Similar behavior to `formatMoneyShort` but without the dollar.
     */
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
