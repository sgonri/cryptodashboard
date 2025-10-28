package com.mycompany.app.models;

import java.util.Objects;

public class Crypto {
    private final String id;
    private final String name;
    private final String symbol;
    private final double price;
    private final double changePercent;
    private final String marketCap;
    private final String volume;
    private final String circulatingSupply;

    public Crypto(String id, String name, String symbol, double price, double changePercent, String marketCap, String volume, String circulatingSupply) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.price = price;
        this.changePercent = changePercent;
        this.marketCap = marketCap;
        this.volume = volume;
        this.circulatingSupply = circulatingSupply;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public double getChangePercent() { return changePercent; }
    public String getMarketCap() { return marketCap; }
    public String getVolume() { return volume; }
    public String getCirculatingSupply() { return circulatingSupply; }

    public String getPriceFormatted() {
        return String.format("$%,.2f", price);
    }

    public String getChangeFormatted() {
        return String.format("%s%.2f%%", changePercent >= 0 ? "▲" : "▼", Math.abs(changePercent));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Crypto crypto = (Crypto) o;
        return Objects.equals(id, crypto.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
