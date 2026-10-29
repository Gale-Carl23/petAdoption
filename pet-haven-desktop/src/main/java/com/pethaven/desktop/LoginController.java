package com.pethaven.desktop;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pet-haven-desktop-login");
        thread.setDaemon(true);
        return thread;
    });
    private DesktopApiClient apiClient;
    private Runnable onLoginSuccess;

    public void configure(DesktopApiClient apiClient, Runnable onLoginSuccess) {
        this.apiClient = apiClient;
        this.onLoginSuccess = onLoginSuccess;
    }

    @FXML
    private void handleLogin() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (email.isBlank() || password.isBlank()) {
            showMessage("Enter your email address and password.", false);
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("Signing in…");
        hideMessage();

        Task<Void> loginTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                apiClient.login(email, password);
                return null;
            }
        };

        loginTask.setOnSucceeded(event -> {
            passwordField.clear();
            executor.shutdown();
            onLoginSuccess.run();
        });
        loginTask.setOnFailed(event -> {
            Throwable error = loginTask.getException();
            String message = error instanceof SecurityException
                    ? error.getMessage()
                    : "Could not connect to the server. Check the API address and try again.";
            showMessage(message, true);
            loginButton.setDisable(false);
            loginButton.setText("Sign in");
        });
        executor.execute(loginTask);
    }

    private void showMessage(String message, boolean error) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("login-message-error", "login-message-info");
        messageLabel.getStyleClass().add(error ? "login-message-error" : "login-message-info");
        messageLabel.setManaged(true);
        messageLabel.setVisible(true);
    }

    private void hideMessage() {
        messageLabel.setManaged(false);
        messageLabel.setVisible(false);
    }
}
