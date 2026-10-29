package com.pethaven.desktop;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class PetHavenDesktopApplication extends Application {

    private static final String STYLESHEET = "/css/dashboard.css";

    private Stage stage;
    private DesktopApiClient apiClient;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        this.apiClient = new DesktopApiClient();
        stage.setTitle("Pawprints of Hope · Admin Desktop");
        stage.setMinWidth(760);
        stage.setMinHeight(600);
        showLogin();
        stage.show();
        stage.setMaximized(true);
    }

    private void showLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    Objects.requireNonNull(getClass().getResource("/fxml/login.fxml")));
            Parent root = loader.load();
            LoginController controller = loader.getController();
            controller.configure(apiClient, this::showDashboard);
            Scene scene = new Scene(root, 1120, 700);
            scene.getStylesheets().add(stylesheet());
            stage.setScene(scene);
            stage.setMinWidth(920);
            stage.setMinHeight(620);
            stage.centerOnScreen();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load the desktop sign-in screen.", ex);
        }
    }

    private void showDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    Objects.requireNonNull(getClass().getResource("/fxml/dashboard.fxml")));
            Parent root = loader.load();
            DashboardController controller = loader.getController();
            controller.configure(apiClient, this::showLogin);
            Scene scene = new Scene(root, 1240, 800);
            scene.getStylesheets().add(stylesheet());
            stage.setScene(scene);
            stage.setMinWidth(900);
            stage.setMinHeight(650);
            stage.centerOnScreen();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load the admin workspace.", ex);
        }
    }

    private String stylesheet() {
        return Objects.requireNonNull(getClass().getResource(STYLESHEET)).toExternalForm();
    }

    @Override
    public void stop() {
        if (apiClient != null && apiClient.isAuthenticated()) {
            Thread logoutThread = new Thread(() -> {
                try {
                    apiClient.logout();
                } catch (Exception ignored) {
                    // The server session expires independently if shutdown interrupts logout.
                }
            }, "pet-haven-desktop-shutdown");
            logoutThread.setDaemon(true);
            logoutThread.start();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
