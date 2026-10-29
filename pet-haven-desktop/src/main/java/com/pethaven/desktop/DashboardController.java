package com.pethaven.desktop;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DashboardController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    @FXML private VBox contentArea;
    @FXML private Label pageTitleLabel;
    @FXML private Label statusLabel;
    @FXML private Label userNameLabel;
    @FXML private Button navDashboard;
    @FXML private Button navPets;
    @FXML private Button navApplications;
    @FXML private Button navDonations;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pet-haven-desktop-api");
        thread.setDaemon(true);
        return thread;
    });
    private DesktopApiClient apiClient;
    private Runnable onLogout;

    public void configure(DesktopApiClient apiClient, Runnable onLogout) {
        this.apiClient = Objects.requireNonNull(apiClient);
        this.onLogout = Objects.requireNonNull(onLogout);
        userNameLabel.setText(apiClient.username());
        showDashboard();
    }

    @FXML
    private void showDashboard() {
        activate(navDashboard, "Overview");
        setContent(loading("Loading your overview…"));
        load(apiClient::dashboardSummary, summary -> {
            HBox stats = new HBox(16,
                    statCard("🐾", "PET PROFILES", Long.toString(summary.totalPets()), "Manage the adoption listings"),
                    statCard("♡", "APPLICATIONS", Long.toString(summary.totalApplications()), "Submitted by adopters"),
                    statCard("₱", "DONATIONS", Long.toString(summary.totalDonations()), "Shared to support the pets"));
            stats.getStyleClass().add("stats-row");

            VBox quickActions = new VBox(12);
            quickActions.getStyleClass().add("desktop-panel");
            Label title = new Label("Your workspace");
            title.getStyleClass().add("panel-title");
            Label note = new Label("Manage pet profiles and review the latest community submissions.");
            note.setWrapText(true);
            note.getStyleClass().add("panel-copy");
            HBox actions = new HBox(10,
                    actionButton("Manage pets", this::showPets),
                    actionButton("Review applications", this::showApplications),
                    actionButton("Review donations", this::showDonations));
            actions.getStyleClass().add("quick-actions");
            quickActions.getChildren().addAll(title, note, actions);
            load(apiClient::referralAnalytics, sources -> {
                long totalResponses = sources.stream()
                        .mapToLong(DesktopApiClient.ReferralSourceRecord::count).sum();
                VBox outreach = panel("How people find us",
                        totalResponses + " survey responses from adoption and donation submissions");
                if (sources.isEmpty()) {
                    outreach.getChildren().add(emptyState("No survey responses yet",
                            "Responses from the thank-you page will appear here."));
                } else {
                    for (DesktopApiClient.ReferralSourceRecord source : sources) {
                        Label sourceLabel = new Label(source.label());
                        sourceLabel.getStyleClass().add("referral-source-label");
                        Label countLabel = new Label(Long.toString(source.count()));
                        countLabel.getStyleClass().add("panel-copy");
                        HBox row = new HBox(12, sourceLabel, countLabel);
                        HBox.setHgrow(sourceLabel, Priority.ALWAYS);
                        ProgressBar progress = new ProgressBar((double) source.count() / totalResponses);
                        progress.getStyleClass().add("referral-progress");
                        progress.setMaxWidth(Double.MAX_VALUE);
                        outreach.getChildren().addAll(row, progress);
                    }
                }
                setContent(stats, quickActions, outreach);
                showStatus("Connected to the secure admin API.", false);
            });
        });
    }

    @FXML
    private void showPets() {
        activate(navPets, "Manage pets");
        setContent(loading("Loading pet profiles…"));
        load(apiClient::pets, pets -> renderPets(pets));
    }

    @FXML
    private void showApplications() {
        activate(navApplications, "Applications");
        setContent(loading("Loading adoption applications…"));
        load(apiClient::applications, applications -> renderApplications(applications));
    }

    @FXML
    private void showDonations() {
        activate(navDonations, "Donations");
        setContent(loading("Loading donation submissions…"));
        load(apiClient::donations, donations -> renderDonations(donations));
    }

    @FXML
    private void handleLogout() {
        statusLabel.setText("Signing out…");
        load(() -> {
            apiClient.logout();
            return null;
        }, ignored -> onLogout.run());
    }

    private void renderPets(List<DesktopApiClient.PetRecord> pets) {
        VBox panel = panel("Pet profiles", pets.size() + " profiles");
        Button addButton = actionButton("＋ Add a pet", () -> openPetDialog(null));
        TableView<DesktopApiClient.PetRecord> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(textColumn("Pet", DesktopApiClient.PetRecord::name, 180));
        table.getColumns().add(textColumn("Breed", DesktopApiClient.PetRecord::breed, 150));
        table.getColumns().add(textColumn("Age", pet -> pet.age() == 1 ? "1 year" : pet.age() + " years", 90));
        table.getColumns().add(textColumn("Status", DesktopApiClient.PetRecord::status, 120));
        TableColumn<DesktopApiClient.PetRecord, DesktopApiClient.PetRecord> edit =
                objectColumn("Action", pet -> pet);
        edit.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            private final Button button = new Button("Edit");
            {
                button.getStyleClass().add("table-action-button");
                button.setOnAction(event -> openPetDialog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(DesktopApiClient.PetRecord item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : button);
            }
        });
        table.getColumns().add(edit);
        table.setItems(FXCollections.observableArrayList(pets));
        panel.getChildren().addAll(addButton, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        setContent(panel);
    }

    private void renderApplications(List<DesktopApiClient.ApplicationRecord> applications) {
        VBox panel = panel("Adoption applications", applications.size() + " submissions · newest first");
        if (applications.isEmpty()) {
            panel.getChildren().add(emptyState("No applications yet", "New adoption applications will appear here."));
        } else {
            TableView<DesktopApiClient.ApplicationRecord> table = new TableView<>();
            table.getStyleClass().add("data-table");
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.getColumns().add(textColumn("Applicant", DesktopApiClient.ApplicationRecord::applicantName, 140));
            table.getColumns().add(textColumn("Email", DesktopApiClient.ApplicationRecord::applicantEmail, 190));
            table.getColumns().add(textColumn("Pet", DesktopApiClient.ApplicationRecord::petName, 140));
            table.getColumns().add(textColumn("Home", DesktopApiClient.ApplicationRecord::housingType, 100));
            table.getColumns().add(textColumn("Experience", DesktopApiClient.ApplicationRecord::petExperience, 130));
            table.getColumns().add(textColumn("Status", DesktopApiClient.ApplicationRecord::status, 110));
            table.getColumns().add(textColumn("Submitted", this::dateOnly, 120));
            table.getColumns().add(textColumn("Reason", DesktopApiClient.ApplicationRecord::reason, 260));
            TableColumn<DesktopApiClient.ApplicationRecord, DesktopApiClient.ApplicationRecord> decision =
                    objectColumn("Decision", application -> application);
            decision.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
                private final HBox actions = new HBox(6);
                private final Button approve = new Button("Approve");
                private final Button decline = new Button("Decline");
                {
                    approve.getStyleClass().addAll("table-action-button", "review-approve-button");
                    decline.getStyleClass().addAll("table-action-button", "review-reject-button");
                    approve.setOnAction(event -> reviewApplication(
                            getTableView().getItems().get(getIndex()), "APPROVED"));
                    decline.setOnAction(event -> reviewApplication(
                            getTableView().getItems().get(getIndex()), "REJECTED"));
                    actions.getChildren().addAll(approve, decline);
                }
                @Override protected void updateItem(DesktopApiClient.ApplicationRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : isApplicationPending(item) ? actions : reviewedLabel());
                }
            });
            table.getColumns().add(decision);
            table.setItems(FXCollections.observableArrayList(applications));
            panel.getChildren().add(table);
            VBox.setVgrow(table, Priority.ALWAYS);
        }
        setContent(panel);
    }

    private void renderDonations(List<DesktopApiClient.DonationRecord> donations) {
        VBox panel = panel("Donation submissions", donations.size() + " submissions · newest first");
        if (donations.isEmpty()) {
            panel.getChildren().add(emptyState("No donations yet", "New donation submissions will appear here."));
        } else {
            TableView<DesktopApiClient.DonationRecord> table = new TableView<>();
            table.getStyleClass().add("data-table");
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.getColumns().add(textColumn("Donor", DesktopApiClient.DonationRecord::donorName, 130));
            table.getColumns().add(textColumn("Email", DesktopApiClient.DonationRecord::donorEmail, 190));
            table.getColumns().add(textColumn("Amount", donation -> "PHP " + donation.amount().toPlainString(), 110));
            table.getColumns().add(textColumn("Date sent", DesktopApiClient.DonationRecord::dateSent, 110));
            table.getColumns().add(textColumn("Status", DesktopApiClient.DonationRecord::status, 150));
            table.getColumns().add(textColumn("Message", donation -> safe(donation.message()), 190));
            TableColumn<DesktopApiClient.DonationRecord, DesktopApiClient.DonationRecord> proof =
                    objectColumn("Payment proof", donation -> donation);
            proof.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
                private final Button button = new Button("Open proof");
                {
                    button.getStyleClass().add("table-action-button");
                    button.setOnAction(event -> openProof(getTableView().getItems().get(getIndex())));
                }
                @Override protected void updateItem(DesktopApiClient.DonationRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : button);
                }
            });
            table.getColumns().add(proof);
            TableColumn<DesktopApiClient.DonationRecord, DesktopApiClient.DonationRecord> decision =
                    objectColumn("Decision", donation -> donation);
            decision.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
                private final HBox actions = new HBox(6);
                private final Button verify = new Button("Verify");
                private final Button decline = new Button("Decline");
                {
                    verify.getStyleClass().addAll("table-action-button", "review-approve-button");
                    decline.getStyleClass().addAll("table-action-button", "review-reject-button");
                    verify.setOnAction(event -> reviewDonation(
                            getTableView().getItems().get(getIndex()), "VERIFIED"));
                    decline.setOnAction(event -> reviewDonation(
                            getTableView().getItems().get(getIndex()), "REJECTED"));
                    actions.getChildren().addAll(verify, decline);
                }
                @Override protected void updateItem(DesktopApiClient.DonationRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : "PENDING_VERIFICATION".equals(item.status())
                            ? actions : reviewedLabel());
                }
            });
            table.getColumns().add(decision);
            table.setItems(FXCollections.observableArrayList(donations));
            panel.getChildren().add(table);
            VBox.setVgrow(table, Priority.ALWAYS);
        }
        setContent(panel);
    }

    private void openPetDialog(DesktopApiClient.PetRecord existing) {
        Dialog<DesktopApiClient.PetPayload> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add a pet" : "Edit pet profile");
        dialog.setHeaderText(existing == null
                ? "Add a profile for a pet looking for a home."
                : "Update " + existing.name() + "’s profile.");
        Window owner = contentArea.getScene().getWindow();
        dialog.initOwner(owner);

        TextField name = new TextField(existing == null ? "" : safe(existing.name()));
        TextField breed = new TextField(existing == null ? "" : safe(existing.breed()));
        TextField age = new TextField(existing == null || existing.age() == null ? "" : existing.age().toString());
        TextArea description = new TextArea(existing == null ? "" : safe(existing.description()));
        TextField imageUrl = new TextField(existing == null ? "" : safe(existing.imageUrl()));
        description.setPrefRowCount(3);
        name.setPromptText("Pet name");
        breed.setPromptText("Breed");
        age.setPromptText("Age in years");
        description.setPromptText("Personality and needs");
        imageUrl.setPromptText("/images/pets/example.png");

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(11);
        fields.addRow(0, new Label("Name"), name);
        fields.addRow(1, new Label("Breed"), breed);
        fields.addRow(2, new Label("Age"), age);
        fields.addRow(3, new Label("Description"), description);
        fields.addRow(4, new Label("Photo URL"), imageUrl);
        fields.getStyleClass().add("pet-editor-grid");
        GridPane.setHgrow(name, Priority.ALWAYS);
        GridPane.setHgrow(breed, Priority.ALWAYS);
        GridPane.setHgrow(age, Priority.ALWAYS);
        GridPane.setHgrow(description, Priority.ALWAYS);
        GridPane.setHgrow(imageUrl, Priority.ALWAYS);
        dialog.getDialogPane().setContent(fields);

        ButtonType save = new ButtonType("Save profile", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/dashboard.css")).toExternalForm());
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                String parsedName = name.getText().trim();
                String parsedBreed = breed.getText().trim();
                int parsedAge = Integer.parseInt(age.getText().trim());
                if (parsedName.isBlank() || parsedBreed.isBlank() || parsedAge < 0) return null;
                return new DesktopApiClient.PetPayload(parsedName, parsedBreed, parsedAge,
                        description.getText().trim(), imageUrl.getText().trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        });

        Optional<DesktopApiClient.PetPayload> result = dialog.showAndWait();
        if (result.isEmpty()) return;
        DesktopApiClient.PetPayload payload = result.get();
        if (existing == null) {
            load(() -> apiClient.createPet(payload), created -> showPets());
        } else {
            load(() -> apiClient.updatePet(existing.id(), payload), updated -> showPets());
        }
    }

    private void openProof(DesktopApiClient.DonationRecord donation) {
        load(() -> {
            byte[] contents = apiClient.donationProof(donation.id());
            String extension = safe(donation.proofPath()).toLowerCase(Locale.ROOT);
            String suffix = extension.endsWith(".pdf") ? ".pdf"
                    : extension.endsWith(".png") ? ".png" : ".jpg";
            Path proof = Files.createTempFile("pawprints-proof-", suffix);
            Files.write(proof, contents);
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("This computer cannot open files automatically.");
            }
            Desktop.getDesktop().open(proof.toFile());
            return null;
        }, ignored -> showStatus("Payment proof opened.", false));
    }

    private void reviewApplication(DesktopApiClient.ApplicationRecord application, String status) {
        load(() -> apiClient.reviewApplication(application.id(), status), updated -> {
            showStatus("Application " + status.toLowerCase(Locale.ROOT) + ".", false);
            showApplications();
        });
    }

    private void reviewDonation(DesktopApiClient.DonationRecord donation, String status) {
        load(() -> apiClient.reviewDonation(donation.id(), status), updated -> {
            showStatus("Donation marked " + status.toLowerCase(Locale.ROOT) + ".", false);
            showDonations();
        });
    }

    private static boolean isApplicationPending(DesktopApiClient.ApplicationRecord application) {
        return "PENDING".equals(application.status()) || "UNDER_REVIEW".equals(application.status());
    }

    private static Label reviewedLabel() {
        Label label = new Label("Recorded");
        label.getStyleClass().add("panel-copy");
        return label;
    }

    private <T> void load(ApiCall<T> call, Consumer<T> success) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return call.run(); }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            String message = failure instanceof SecurityException
                    ? failure.getMessage()
                    : failure == null ? "The request failed." : safe(failure.getMessage());
            showStatus(message, true);
            boolean pageWasLoading = contentArea.getChildren().stream()
                    .anyMatch(node -> node instanceof Label label
                            && label.getStyleClass().contains("loading-label"));
            if (pageWasLoading) {
                setContent(emptyState("Could not load this page", message));
            }
        });
        executor.execute(task);
    }

    private void activate(Button selected, String title) {
        pageTitleLabel.setText(title);
        for (Button button : List.of(navDashboard, navPets, navApplications, navDonations)) {
            button.getStyleClass().remove("nav-button-active");
        }
        selected.getStyleClass().add("nav-button-active");
        hideStatus();
    }

    private VBox panel(String title, String subtitle) {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("desktop-panel");
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-title");
        Label note = new Label(subtitle);
        note.getStyleClass().add("panel-copy");
        panel.getChildren().addAll(heading, note);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private VBox statCard(String icon, String title, String value, String note) {
        VBox card = new VBox(11);
        card.getStyleClass().add("stat-card");
        Label label = new Label(icon + "   " + title);
        label.getStyleClass().add("stat-label");
        Label count = new Label(value);
        count.getStyleClass().add("stat-value");
        Label detail = new Label(note);
        detail.getStyleClass().add("stat-note");
        card.getChildren().addAll(label, count, detail);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("desktop-action");
        button.setOnAction(event -> action.run());
        return button;
    }

    private Label loading(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("loading-label");
        return label;
    }

    private VBox emptyState(String title, String message) {
        VBox state = new VBox(8);
        state.getStyleClass().add("desktop-empty-state");
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-title");
        Label copy = new Label(message);
        copy.setWrapText(true);
        copy.getStyleClass().add("panel-copy");
        state.getChildren().addAll(heading, copy);
        return state;
    }

    private <T> TableColumn<T, String> textColumn(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(safe(value.apply(cell.getValue()))));
        return column;
    }

    private <T> TableColumn<T, T> objectColumn(String title, Function<T, T> value) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setPrefWidth(110);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }

    private void setContent(Node... nodes) {
        contentArea.getChildren().setAll(nodes);
        if (nodes.length == 1 && nodes[0] instanceof VBox box) {
            VBox.setVgrow(box, Priority.ALWAYS);
        }
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("desktop-status-error", "desktop-status-success");
        statusLabel.getStyleClass().add(error ? "desktop-status-error" : "desktop-status-success");
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
    }

    private void hideStatus() {
        statusLabel.setManaged(false);
        statusLabel.setVisible(false);
    }

    private String dateOnly(DesktopApiClient.ApplicationRecord application) {
        return formatDate(application.submittedAt());
    }

    private static String formatDate(String value) {
        if (value == null || value.isBlank()) return "—";
        try {
            return DATE_FORMAT.format(LocalDateTime.parse(value));
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        T run() throws Exception;
    }
}
