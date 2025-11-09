package com.mycompany.app.models;

import java.util.Collections;
import java.util.List;

public class HistoricalData {
    private final List<ChartPoint> points;

    public HistoricalData(List<ChartPoint> points) {
        this.points = points == null ? Collections.emptyList() : Collections.unmodifiableList(points);
    }

    public List<ChartPoint> getPoints() { return points; }
}