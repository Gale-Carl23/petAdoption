package com.pethaven.desktop;

import java.io.IOException;
import java.util.Objects;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class PetHavenDesktopApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Objects.requireNonNull(
                        getClass().getResource("/fxml/dashboard.fxml")
                )
        );

        Parent root = loader.load();
        Scene scene = new Scene(root, 1200, 760);

        String stylesheet = Objects.requireNonNull(
                getClass().getResource("/css/dashboard.css")
        ).toExternalForm();

        scene.getStylesheets().add(stylesheet);

        stage.setTitle("Pet Haven - Administration");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}