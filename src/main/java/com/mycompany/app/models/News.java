package com.mycompany.app.models;

public class News {
    private final String title;
    private final String source;
    private final String timeAgo;
    private final String summary;
    private final String link;
    private final String date;

    public News(String title, String source, String timeAgo) {
        this(title, source, timeAgo, "", "", "");
    }

    public News(String title, String source, String timeAgo, String summary, String link, String date) {
        this.title = title;
        this.source = source;
        this.timeAgo = timeAgo;
        this.summary = summary;
        this.link = link;
        this.date = date;
    }

    public String getTitle() { return title; }
    public String getSource() { return source; }
    public String getTimeAgo() { return timeAgo; }
    public String getSummary() { return summary; }
    public String getLink() { return link; }
    public String getDate() { return date; }
}
