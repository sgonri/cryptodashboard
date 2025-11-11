package com.mycompany.app.ui;

import com.mycompany.app.models.News;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.List;

public class NewsView extends VBox {
    private final VBox newsList = new VBox(12);
    private final ToggleButton allToggle;
    private final ToggleButton selectedToggle;
    private final Runnable onToggleChanged;
    private HostServices hostServices;

    public NewsView(Runnable onToggleChanged) {
        super(15);
        this.onToggleChanged = onToggleChanged;
        setPadding(new Insets(25));
        setPrefWidth(350);
        getStyleClass().add("news-pane");

        Label header = new Label("News & Insights");
        header.getStyleClass().add("section-header");

        ToggleGroup toggleGroup = new ToggleGroup();
        allToggle = new ToggleButton("All");
        allToggle.setToggleGroup(toggleGroup);
        allToggle.setSelected(true);
        allToggle.getStyleClass().add("news-toggle");

        selectedToggle = new ToggleButton("Selected");
        selectedToggle.setToggleGroup(toggleGroup);
        selectedToggle.getStyleClass().add("news-toggle");

        HBox toggleBox = new HBox(allToggle, selectedToggle);
        toggleBox.getStyleClass().add("news-toggle-box");

        toggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                this.onToggleChanged.run();
            }
        });

        ScrollPane scrollPane = new ScrollPane(newsList);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("news-scroll-pane");

        getChildren().addAll(header, toggleBox, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public boolean isShowAllSelected() {
        return allToggle.isSelected();
    }

    public void updateNews(List<News> news) {
        newsList.getChildren().clear();
        if (news == null) return;
        for (News n : news) {
            newsList.getChildren().add(createNewsCard(n));
        }
    }

    private VBox createNewsCard(News news) {
        VBox card = new VBox(5);
        card.getStyleClass().add("news-card");
        
        // Title (Headline)
        Label title = new Label(news.getTitle());
        title.getStyleClass().add("news-title");
        title.setWrapText(true);

        // Source and Date
        Label metadata = new Label(news.getSource() + " · " + news.getDate());
        metadata.getStyleClass().add("news-subtitle");
        metadata.setWrapText(false);

        // Summary/Snippet
        VBox summaryContainer = new VBox();
        if (news.getSummary() != null && !news.getSummary().isEmpty()) {
            Label summary = new Label(news.getSummary());
            summary.getStyleClass().add("news-summary");
            summary.setWrapText(true);
            summary.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 11;");
            summaryContainer.getChildren().add(summary);
            summaryContainer.setPadding(new Insets(5, 0, 5, 0));
        }

        // Read More Link
        VBox linkContainer = new VBox();
        if (news.getLink() != null && !news.getLink().isEmpty()) {
            Hyperlink readMore = new Hyperlink("Read full article");
            readMore.getStyleClass().add("news-link");
            readMore.setStyle("-fx-font-size: 11; -fx-text-fill: #4a9eff;");
            readMore.setOnAction(event -> {
                if (hostServices != null) {
                    hostServices.showDocument(news.getLink());
                }
            });
            linkContainer.getChildren().add(readMore);
        }

        card.getChildren().addAll(title, metadata);
        if (!summaryContainer.getChildren().isEmpty()) {
            card.getChildren().add(summaryContainer);
        }
        if (!linkContainer.getChildren().isEmpty()) {
            card.getChildren().add(linkContainer);
        }

        return card;
    }
}
