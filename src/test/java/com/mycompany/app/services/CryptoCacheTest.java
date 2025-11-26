package com.mycompany.app.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mycompany.app.models.ChartPoint;
import com.mycompany.app.models.Crypto;
import com.mycompany.app.models.HistoricalData;

/**
 * Unit tests for the CryptoCache service
 */
public class CryptoCacheTest {

    private CryptoCache cache;

    @BeforeEach
    public void setUp() {
        cache = new CryptoCache();
    }

    // --- Top Cryptos Tests ---

    @Test
    public void testHasTopCryptosInitiallyFalse() {
        assertFalse(cache.hasTopCryptos());
    }

    @Test
    public void testSetAndGetTopCryptos() {
        List<Crypto> cryptos = Arrays.asList(
            new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 2.5, "$1T", "$50B", "19M"),
            new Crypto("ethereum", "Ethereum", "ETH", 3000, 1.5, "$500B", "$20B", "120M")
        );

        cache.setTopCryptos(cryptos);

        assertTrue(cache.hasTopCryptos());
        assertEquals(2, cache.getTopCryptos().size());
        assertEquals("bitcoin", cache.getTopCryptos().get(0).getId());
    }

    @Test
    public void testGetTopCryptosReturnsCopy() {
        List<Crypto> cryptos = Arrays.asList(
            new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 2.5, "", "", "")
        );
        cache.setTopCryptos(cryptos);

        List<Crypto> retrieved = cache.getTopCryptos();
        retrieved.clear(); // Modify the returned list

        // Original cache should still have data
        assertTrue(cache.hasTopCryptos());
        assertEquals(1, cache.getTopCryptos().size());
    }

    @Test
    public void testSetTopCryptosWithNull() {
        cache.setTopCryptos(null);
        assertFalse(cache.hasTopCryptos());
        assertTrue(cache.getTopCryptos().isEmpty());
    }

    // --- Historical Data Tests ---

    @Test
    public void testHasHistoricalDataInitiallyFalse() {
        assertFalse(cache.hasHistoricalData("bitcoin", "1"));
    }

    @Test
    public void testPutAndGetHistoricalData() {
        List<ChartPoint> points = Arrays.asList(
            new ChartPoint(Instant.now(), 50000.0, 1000000.0),
            new ChartPoint(Instant.now().plusSeconds(60), 50100.0, 1100000.0)
        );
        HistoricalData data = new HistoricalData(points);

        cache.putHistoricalData("bitcoin", "1", data);

        assertTrue(cache.hasHistoricalData("bitcoin", "1"));
        assertEquals(2, cache.getHistoricalData("bitcoin", "1").getPoints().size());
    }

    @Test
    public void testGetHistoricalDataForDifferentIntervals() {
        HistoricalData data1Day = new HistoricalData(Arrays.asList(
            new ChartPoint(Instant.now(), 50000.0, null)
        ));
        HistoricalData data7Day = new HistoricalData(Arrays.asList(
            new ChartPoint(Instant.now(), 48000.0, null),
            new ChartPoint(Instant.now(), 50000.0, null)
        ));

        cache.putHistoricalData("bitcoin", "1", data1Day);
        cache.putHistoricalData("bitcoin", "7", data7Day);

        assertTrue(cache.hasHistoricalData("bitcoin", "1"));
        assertTrue(cache.hasHistoricalData("bitcoin", "7"));
        assertFalse(cache.hasHistoricalData("bitcoin", "30"));

        assertEquals(1, cache.getHistoricalData("bitcoin", "1").getPoints().size());
        assertEquals(2, cache.getHistoricalData("bitcoin", "7").getPoints().size());
    }

    // --- Clear Tests ---

    @Test
    public void testClearRemovesAllData() {
        // Add some data
        cache.setTopCryptos(Arrays.asList(
            new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 2.5, "", "", "")
        ));
        cache.putHistoricalData("bitcoin", "1", new HistoricalData(Arrays.asList(
            new ChartPoint(Instant.now(), 50000.0, null)
        )));

        assertTrue(cache.hasTopCryptos());
        assertTrue(cache.hasHistoricalData("bitcoin", "1"));

        // Clear
        cache.clear();

        assertFalse(cache.hasTopCryptos());
        assertFalse(cache.hasHistoricalData("bitcoin", "1"));
        assertEquals(0, cache.getHistoricalDataCount());
    }

    // --- Count Tests ---

    @Test
    public void testGetHistoricalDataCount() {
        assertEquals(0, cache.getHistoricalDataCount());

        cache.putHistoricalData("bitcoin", "1", new HistoricalData(null));
        assertEquals(1, cache.getHistoricalDataCount());

        cache.putHistoricalData("bitcoin", "7", new HistoricalData(null));
        assertEquals(2, cache.getHistoricalDataCount());

        cache.putHistoricalData("ethereum", "1", new HistoricalData(null));
        assertEquals(3, cache.getHistoricalDataCount());
    }
}
