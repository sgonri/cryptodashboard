package com.mycompany.app.services;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mycompany.app.models.News;

/**
 * Unit tests for the SerpAPINewsService
 * Uses mocked HttpClient to avoid actual API calls
 */
public class SerpAPINewsServiceTest {

    private HttpClient mockHttpClient;
    private NewsService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        mockHttpClient = Mockito.mock(HttpClient.class);
        service = new NewsService(mockHttpClient, "test-api-key");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetNewsForCryptoSuccess() throws Exception {
        String jsonResponse = """
            {
                "news_results": [
                    {
                        "title": "Bitcoin Hits New High",
                        "source": "CoinDesk",
                        "link": "https://example.com/news1",
                        "snippet": "Bitcoin reached a new all-time high today...",
                        "date": "2 hours ago"
                    },
                    {
                        "title": "Market Analysis: BTC Trends",
                        "source": "CryptoNews",
                        "link": "https://example.com/news2",
                        "snippet": "Analysts predict continued growth...",
                        "date": "5 hours ago"
                    }
                ]
            }
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<News> news = service.getNewsForCrypto("bitcoin");

        assertNotNull(news);
        assertEquals(2, news.size());
        
        News firstNews = news.get(0);
        assertEquals("Bitcoin Hits New High", firstNews.getTitle());
        assertEquals("CoinDesk", firstNews.getSource());
        assertEquals("https://example.com/news1", firstNews.getLink());
        assertEquals("Bitcoin reached a new all-time high today...", firstNews.getSummary());
        assertEquals("2 hours ago", firstNews.getTimeAgo());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetNewsForCryptoEmptyResults() throws Exception {
        String jsonResponse = """
            {
                "news_results": []
            }
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<News> news = service.getNewsForCrypto("unknowncoin");

        assertNotNull(news);
        assertTrue(news.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetNewsHandlesApiError() throws Exception {
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn("Unauthorized");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<News> news = service.getNewsForCrypto("bitcoin");

        assertNotNull(news);
        assertTrue(news.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetNewsHandlesNetworkError() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new java.io.IOException("Network error"));

        List<News> news = service.getNewsForCrypto("bitcoin");

        assertNotNull(news);
        assertTrue(news.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetNewsSkipsItemsWithoutTitle() throws Exception {
        String jsonResponse = """
            {
                "news_results": [
                    {
                        "title": "",
                        "source": "Source1",
                        "link": "https://example.com/news1"
                    },
                    {
                        "title": "Valid News",
                        "source": "Source2",
                        "link": "https://example.com/news2"
                    }
                ]
            }
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<News> news = service.getNewsForCrypto("bitcoin");

        assertEquals(1, news.size());
        assertEquals("Valid News", news.get(0).getTitle());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetNewsSkipsItemsWithoutLink() throws Exception {
        String jsonResponse = """
            {
                "news_results": [
                    {
                        "title": "No Link News",
                        "source": "Source1",
                        "link": ""
                    },
                    {
                        "title": "Valid News",
                        "source": "Source2",
                        "link": "https://example.com/news2"
                    }
                ]
            }
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<News> news = service.getNewsForCrypto("bitcoin");

        assertEquals(1, news.size());
        assertEquals("Valid News", news.get(0).getTitle());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetGeneralNews() throws Exception {
        String jsonResponse = """
            {
                "news_results": [
                    {
                        "title": "Crypto Market Update",
                        "source": "Bloomberg",
                        "link": "https://example.com/news",
                        "snippet": "The crypto market...",
                        "date": "1 hour ago"
                    }
                ]
            }
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<News> news = service.getGeneralNews();

        assertNotNull(news);
        assertEquals(1, news.size());
        assertEquals("Crypto Market Update", news.get(0).getTitle());
    }

    @Test
    @SuppressWarnings("unchecked") 
    public void testGetNewsFallsBackToRecentlyForMissingDate() throws Exception {
        String jsonResponse = """
            {
                "news_results": [
                    {
                        "title": "News Without Date",
                        "source": "Source",
                        "link": "https://example.com/news"
                    }
                ]
            }
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<News> news = service.getNewsForCrypto("bitcoin");

        assertEquals(1, news.size());
        assertEquals("Recently", news.get(0).getTimeAgo());
    }

    @Test
    public void testConstructorWithNullHttpClient() {
        assertThrows(IllegalArgumentException.class, () -> {
            new NewsService(null, "api-key");
        });
    }
}
