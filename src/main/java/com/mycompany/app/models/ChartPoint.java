package com.mycompany.app.models;

import java.time.Instant;

public class ChartPoint {
    private final Instant time;
    private final Double price;
    private final Double volume;

    public ChartPoint(Instant time, Double price, Double volume) {
        this.time = time;
        this.price = price;
        this.volume = volume;
    }

    public Instant getTime() { return time; }
    public Double getPrice() { return price; }
    public Double getVolume() { return volume; }

    public long getEpochMilli() { return time.toEpochMilli(); }

    public String getTimeIso() { return time.toString(); }
}