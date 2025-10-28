package com.mycompany.app.model;

public class News {
    private final String title;
    private final String source;
    private final String timeAgo;

    public News(String title, String source, String timeAgo) {
        this.title = title;
        this.source = source;
        this.timeAgo = timeAgo;
    }

    public String getTitle() { return title; }
    public String getSource() { return source; }
    public String getTimeAgo() { return timeAgo; }
}
