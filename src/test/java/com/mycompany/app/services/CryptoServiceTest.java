package com.mycompany.app.services;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;

/**
 * Unit tests for the CryptoService
 * Uses mocked HttpClient to avoid actual API calls
 */
public class CryptoServiceTest {

    private HttpClient mockHttpClient;
    private CryptoCache cache;
    private CryptoService service;
    private Properties testProps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        mockHttpClient = Mockito.mock(HttpClient.class);
        cache = new CryptoCache();
        testProps = new Properties();
        testProps.setProperty("coingecko.api.url", "https://api.coingecko.com/api/v3");
        
        service = new CryptoService(mockHttpClient, cache, testProps);
        // Use short retry delays for faster tests
        service.setRetryDelays(new int[]{10, 10});
        service.setDelayBetweenCalls(0);
    }

    // --- getTopCryptos Tests ---

    @Test
    @SuppressWarnings("unchecked")
    public void testGetTopCryptosSuccess() throws Exception {
        String jsonResponse = """
            [
                {
                    "id": "bitcoin",
                    "name": "Bitcoin",
                    "symbol": "btc",
                    "current_price": 50000.0,
                    "price_change_percentage_24h": 2.5,
                    "market_cap": 1000000000000,
                    "total_volume": 50000000000,
                    "circulating_supply": 19000000
                },
                {
                    "id": "ethereum",
                    "name": "Ethereum",
                    "symbol": "eth",
                    "current_price": 3000.0,
                    "price_change_percentage_24h": -1.5,
                    "market_cap": 500000000000,
                    "total_volume": 20000000000,
                    "circulating_supply": 120000000
                }
            ]
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<Crypto> cryptos = service.getTopCryptos();

        assertNotNull(cryptos);
        assertEquals(2, cryptos.size());
        
        Crypto bitcoin = cryptos.get(0);
        assertEquals("bitcoin", bitcoin.getId());
        assertEquals("Bitcoin", bitcoin.getName());
        assertEquals("BTC", bitcoin.getSymbol());
        assertEquals(50000.0, bitcoin.getPrice(), 0.001);
        assertEquals(2.5, bitcoin.getChangePercent(), 0.001);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetTopCryptosCachesResult() throws Exception {
        String jsonResponse = """
            [{"id": "bitcoin", "name": "Bitcoin", "symbol": "btc", "current_price": 50000.0, 
              "price_change_percentage_24h": 2.5, "market_cap": 1000000000000, 
              "total_volume": 50000000000, "circulating_supply": 19000000}]
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        // First call - fetches from API
        List<Crypto> cryptos1 = service.getTopCryptos();
        
        // Second call - should use cache
        List<Crypto> cryptos2 = service.getTopCryptos();

        assertEquals(cryptos1.size(), cryptos2.size());
        // Verify API was only called once (cache was used for second call)
        Mockito.verify(mockHttpClient, Mockito.times(1))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetTopCryptosHandlesApiError() throws Exception {
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<Crypto> cryptos = service.getTopCryptos();

        assertNotNull(cryptos);
        assertTrue(cryptos.isEmpty());
    }

    // --- getHistoricalDataForCrypto Tests ---

    @Test
    @SuppressWarnings("unchecked")
    public void testGetHistoricalDataSuccess() throws Exception {
        String jsonResponse = """
            {
                "prices": [
                    [1700000000000, 50000.0],
                    [1700003600000, 50100.0],
                    [1700007200000, 49900.0]
                ],
                "total_volumes": [
                    [1700000000000, 1000000000],
                    [1700003600000, 1100000000],
                    [1700007200000, 900000000]
                ]
            }
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        HistoricalData data = service.getHistoricalDataForCrypto("bitcoin", "1");

        assertNotNull(data);
        assertNotNull(data.getPoints());
        assertEquals(3, data.getPoints().size());
        assertEquals(50000.0, data.getPoints().get(0).getPrice(), 0.001);
    }

    @Test
    public void testGetHistoricalDataWithNullId() {
        HistoricalData data = service.getHistoricalDataForCrypto(null, "1");
        assertNotNull(data);
        assertTrue(data.getPoints().isEmpty());
    }

    @Test
    public void testGetHistoricalDataWithBlankId() {
        HistoricalData data = service.getHistoricalDataForCrypto("", "1");
        assertNotNull(data);
        assertTrue(data.getPoints().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetHistoricalDataUsesCache() throws Exception {
        String jsonResponse = """
            {"prices": [[1700000000000, 50000.0]], "total_volumes": [[1700000000000, 1000000000]]}
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        // First call
        service.getHistoricalDataForCrypto("bitcoin", "1");
        
        // Second call - should use cache
        service.getHistoricalDataForCrypto("bitcoin", "1");

        // API should only be called once
        Mockito.verify(mockHttpClient, Mockito.times(1))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    // --- hasHistoricalData Tests ---

    @Test
    public void testHasHistoricalDataWithNullParams() {
        assertFalse(service.hasHistoricalData(null, "1"));
        assertFalse(service.hasHistoricalData("bitcoin", null));
        assertFalse(service.hasHistoricalData("", "1"));
        assertFalse(service.hasHistoricalData("bitcoin", ""));
    }

    // --- clearCache Tests ---

    @Test
    @SuppressWarnings("unchecked")
    public void testClearCache() throws Exception {
        String jsonResponse = """
            [{"id": "bitcoin", "name": "Bitcoin", "symbol": "btc", "current_price": 50000.0,
              "price_change_percentage_24h": 2.5, "market_cap": 1000000000000,
              "total_volume": 50000000000, "circulating_supply": 19000000}]
            """;

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        // Populate cache
        service.getTopCryptos();
        
        // Clear cache
        service.clearCache();

        // Next call should hit API again
        service.getTopCryptos();

        // API should be called twice now
        Mockito.verify(mockHttpClient, Mockito.times(2))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    // --- Constructor Tests ---

    @Test
    public void testConstructorWithNullHttpClient() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CryptoService(null, cache, testProps);
        });
    }

    @Test
    public void testConstructorWithNullCache() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CryptoService(mockHttpClient, null, testProps);
        });
    }
}
