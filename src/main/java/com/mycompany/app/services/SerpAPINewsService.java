package com.mycompany.app.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.app.config.ApiConfig;
import com.mycompany.app.models.News;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * NewsService implementation using SerpAPI for Google News searches
 * Fetches news for top 5 cryptocurrencies and general crypto news
 */
public class SerpAPINewsService implements INewsService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String API_URL = "https://serpapi.com/search";
    private final String apiKey;

    public SerpAPINewsService() {
        this(HttpClient.newHttpClient(), ApiConfig.getSerpApiKey());
    }

    public SerpAPINewsService(HttpClient httpClient) {
        this(httpClient, ApiConfig.getSerpApiKey());
    }

    public SerpAPINewsService(HttpClient httpClient, String apiKey) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient cannot be null");
        }
        this.httpClient = httpClient;
        this.apiKey = apiKey != null ? apiKey : "";
        if (this.apiKey.isEmpty()) {
            System.err.println("Warning: SerpAPI key is not set. News fetching will fail.");
        }
    }

    // Search queries for the 5 top cryptocurrencies
    private static final String[] TOP_CRYPTOS = {"Bitcoin", "Ethereum", "Binance", "Solana", "Ripple"};
    private static final String GENERAL_CRYPTO_NEWS_QUERY = "crypto recent major news";

    @Override
    public List<News> getNewsForCrypto(String cryptoName) {
        return searchNews(cryptoName + " recent news");
    }

    @Override
    public List<News> getGeneralNews() {
        return searchNews(GENERAL_CRYPTO_NEWS_QUERY);
    }

    /**
     * Get news for all top 5 cryptocurrencies and general crypto news
     * Total of 6 searches
     */
    @Override
    public List<News> getAllNews() {
        List<News> allNews = new ArrayList<>();

        // Fetch news for each of the top 5 cryptos
        for (String crypto : TOP_CRYPTOS) {
            allNews.addAll(searchNews(crypto + " recent news"));
        }

        // Fetch general crypto news
        allNews.addAll(searchNews(GENERAL_CRYPTO_NEWS_QUERY));

        return allNews;
    }

    /**
     * Execute a news search using SerpAPI
     * Uses tbm=nws parameter to get Google News results
     */
    private List<News> searchNews(String query) {
        List<News> newsList = new ArrayList<>();

        try {
            // Build the URL with parameters
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String urlString = String.format(
                    "%s?q=%s&tbm=nws&api_key=%s&num=5",
                    API_URL,
                    encodedQuery,
                    apiKey
            );

            // Create and send HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                newsList = parseNewsResults(response.body());
            } else {
                System.err.println("SerpAPI request failed with status: " + response.statusCode());
                System.err.println("Response: " + response.body());
            }

        } catch (Exception e) {
            System.err.println("Error fetching news: " + e.getMessage());
            e.printStackTrace();
        }

        return newsList;
    }

    /**
     * Parse the JSON response from SerpAPI and extract news results
     */
    private List<News> parseNewsResults(String jsonResponse) {
        List<News> newsList = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Extract news results from the "news_results" array
            JsonNode newsResults = root.path("news_results");

            if (newsResults.isArray()) {
                for (JsonNode newsItem : newsResults) {
                    String title = newsItem.path("title").asText("");
                    String source = newsItem.path("source").asText("");
                    String link = newsItem.path("link").asText("");
                    String snippet = newsItem.path("snippet").asText("");
                    String date = newsItem.path("date").asText("");

                    // Fallback to "3h ago" format if date is not available
                    if (date.isEmpty()) {
                        date = extractTimeAgo(newsItem);
                    }

                    if (!title.isEmpty() && !link.isEmpty()) {
                        News news = new News(title, source, date, snippet, link, date);
                        newsList.add(news);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing news results: " + e.getMessage());
            e.printStackTrace();
        }

        return newsList;
    }

    /**
     * Extract time ago information from the news item
     */
    private String extractTimeAgo(JsonNode newsItem) {
        // Try to extract from "posted_at" or other fields that might contain time information
        JsonNode postedAt = newsItem.path("posted_at");
        if (!postedAt.isMissingNode()) {
            return postedAt.asText("");
        }

        // Default fallback
        return "Recently";
    }

    /**
     * Async version of searchNews for non-blocking operations
     */
    @Override
    public CompletableFuture<List<News>> searchNewsAsync(String query) {
        return CompletableFuture.supplyAsync(() -> searchNews(query));
    }
}
