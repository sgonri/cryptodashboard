package com.mycompany.app.models;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Crypto model
 */
public class CryptoTest {

    @Test
    public void testCryptoConstruction() {
        Crypto crypto = new Crypto("bitcoin", "Bitcoin", "BTC", 50000.0, 2.5, "$1T", "$50B", "19M");
        
        assertEquals("bitcoin", crypto.getId());
        assertEquals("Bitcoin", crypto.getName());
        assertEquals("BTC", crypto.getSymbol());
        assertEquals(50000.0, crypto.getPrice(), 0.001);
        assertEquals(2.5, crypto.getChangePercent(), 0.001);
        assertEquals("$1T", crypto.getMarketCap());
        assertEquals("$50B", crypto.getVolume());
        assertEquals("19M", crypto.getCirculatingSupply());
    }

    @Test
    public void testGetPriceFormattedStartsWithDollarSign() {
        Crypto crypto = new Crypto("bitcoin", "Bitcoin", "BTC", 50000.50, 0, "", "", "");
        String formatted = crypto.getPriceFormatted();
        assertTrue(formatted.startsWith("$"), "Price should start with $");
        assertTrue(formatted.contains("50") && formatted.contains("00"), "Should contain price value");
    }

    @Test
    public void testGetPriceFormattedWithZero() {
        Crypto crypto = new Crypto("test", "Test", "TST", 0.0, 0, "", "", "");
        String formatted = crypto.getPriceFormatted();
        assertTrue(formatted.startsWith("$"), "Price should start with $");
        assertTrue(formatted.contains("0"), "Should contain zero");
    }

    @Test
    public void testGetChangeFormattedPositive() {
        Crypto crypto = new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 5.75, "", "", "");
        String formatted = crypto.getChangeFormatted();
        assertTrue(formatted.startsWith("▲"), "Positive change should show up arrow");
        assertTrue(formatted.contains("5") && formatted.contains("75"), "Should contain percentage value");
        assertTrue(formatted.endsWith("%"), "Should end with %");
    }

    @Test
    public void testGetChangeFormattedNegative() {
        Crypto crypto = new Crypto("bitcoin", "Bitcoin", "BTC", 50000, -3.25, "", "", "");
        String formatted = crypto.getChangeFormatted();
        assertTrue(formatted.startsWith("▼"), "Negative change should show down arrow");
        assertTrue(formatted.contains("3") && formatted.contains("25"), "Should contain percentage value");
        assertTrue(formatted.endsWith("%"), "Should end with %");
    }

    @Test
    public void testGetChangeFormattedZero() {
        Crypto crypto = new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 0.0, "", "", "");
        String formatted = crypto.getChangeFormatted();
        assertTrue(formatted.startsWith("▲"), "Zero change should show up arrow");
        assertTrue(formatted.endsWith("%"), "Should end with %");
    }

    @Test
    public void testEqualsWithSameId() {
        Crypto crypto1 = new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 2.5, "$1T", "$50B", "19M");
        Crypto crypto2 = new Crypto("bitcoin", "Bitcoin 2", "BTC2", 60000, 3.5, "$2T", "$60B", "20M");
        
        assertEquals(crypto1, crypto2);
    }

    @Test
    public void testEqualsWithDifferentId() {
        Crypto crypto1 = new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 2.5, "", "", "");
        Crypto crypto2 = new Crypto("ethereum", "Ethereum", "ETH", 3000, 1.5, "", "", "");
        
        assertNotEquals(crypto1, crypto2);
    }

    @Test
    public void testEqualsWithNull() {
        Crypto crypto = new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 2.5, "", "", "");
        assertNotEquals(crypto, null);
    }

    @Test
    public void testHashCodeConsistency() {
        Crypto crypto1 = new Crypto("bitcoin", "Bitcoin", "BTC", 50000, 2.5, "", "", "");
        Crypto crypto2 = new Crypto("bitcoin", "Different", "DIFF", 1, 0, "", "", "");
        
        assertEquals(crypto1.hashCode(), crypto2.hashCode());
    }
}
