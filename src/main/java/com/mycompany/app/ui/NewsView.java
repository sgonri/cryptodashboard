package com.mycompany.app.ui;

import com.mycompany.app.models.News;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.List;

public class NewsView extends VBox {
    private final VBox newsList = new VBox(12);
    private final ToggleButton allToggle;
    private final ToggleButton selectedToggle;
    private final Runnable onToggleChanged;

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
        Label title = new Label(news.getTitle());
        title.getStyleClass().add("news-title");
        title.setWrapText(true);

        Label subtitle = new Label(news.getSource() + " · " + news.getTimeAgo());
        subtitle.getStyleClass().add("news-subtitle");

        card.getChildren().addAll(title, subtitle);
        return card;
    }
}
