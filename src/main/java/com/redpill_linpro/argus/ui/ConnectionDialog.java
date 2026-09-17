package com.redpill_linpro.argus.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.redpill_linpro.argus.broker.BrokerClient;
import com.redpill_linpro.argus.broker.BrokerClientFactory;
import com.redpill_linpro.argus.broker.BrokerException;
import com.redpill_linpro.argus.config.ProfileStore;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.Protocol;
import com.redpill_linpro.argus.util.BrokerExecutor;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public final class ConnectionDialog extends Dialog<ConnectionProfile> {

    private final ProfileStore store;
    private final BrokerExecutor executor = new BrokerExecutor();

    private final ComboBox<String> profileNameBox = new ComboBox<>();
    private final ComboBox<Protocol> protocolBox = new ComboBox<>();
    private final TextField hostField = new TextField("localhost");
    private final TextField portField = new TextField("61616");
    private final TextField userField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final CheckBox sslBox = new CheckBox("Use TLS");
    private final CheckBox savePasswordBox = new CheckBox("Save password");
    private final TextField trustStoreField = new TextField();
    private final PasswordField trustStorePasswordField = new PasswordField();
    private final TextField keyStoreField = new TextField();
    private final PasswordField keyStorePasswordField = new PasswordField();
    private final Label statusLabel = new Label();
    private final AtomicReference<BrokerClient> createdClient = new AtomicReference<>();
    private volatile ConnectionProfile connectedProfile;

    public ConnectionDialog(ProfileStore store) {
        this.store = store;
        setTitle("Argus");
        setHeaderText("Connect to ActiveMQ Artemis");

        ButtonType connectButtonType = new ButtonType("Connect", ButtonType.OK.getButtonData());
        ButtonType exitButtonType = new ButtonType("Exit", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(connectButtonType, exitButtonType);

        protocolBox.getItems().addAll(Protocol.values());
        protocolBox.getSelectionModel().selectFirst();
        protocolBox.setOnAction(e -> {
            if (protocolBox.getValue() == Protocol.CORE) {
                if ("61613".equals(portField.getText())) {
                    portField.setText("61616");
                }
            }
        });

        List<ConnectionProfile> profiles = store.load();
        profileNameBox.getItems().addAll(profiles.stream().map(ConnectionProfile::name).sorted().toList());
        profileNameBox.setOnAction(e -> applyProfile(profiles));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16, 16, 8, 16));

        int row = 0;
        grid.add(new Label("Profile name"), 0, row);
        grid.add(profileNameBox, 1, row);
        profileNameBox.setEditable(true);
        GridPane.setHgrow(profileNameBox, Priority.ALWAYS);
        row++;
        grid.add(new Label("Protocol"), 0, row);
        grid.add(protocolBox, 1, row);
        row++;
        grid.add(new Label("Host"), 0, row);
        grid.add(hostField, 1, row);
        row++;
        grid.add(new Label("Port"), 0, row);
        grid.add(portField, 1, row);
        row++;
        grid.add(new Label("Username"), 0, row);
        grid.add(userField, 1, row);
        row++;
        grid.add(new Label("Password"), 0, row);
        grid.add(passwordField, 1, row);
        row++;
        HBox boxes = new HBox(16, sslBox, savePasswordBox);
        grid.add(boxes, 1, row);
        row++;
        row = addTlsField(grid, row, "Truststore", createStoreField(trustStoreField));
        row = addTlsField(grid, row, "Truststore password", trustStorePasswordField);
        row = addTlsField(grid, row, "Keystore", createStoreField(keyStoreField));
        row = addTlsField(grid, row, "Keystore password", keyStorePasswordField);
        grid.add(statusLabel, 0, row);
        GridPane.setColumnSpan(statusLabel, 2);

        getDialogPane().setContent(grid);

        Button connectButton = (Button) getDialogPane().lookupButton(connectButtonType);
        connectButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            connect(connectButton);
        });

        savePasswordBox.selectedProperty().addListener((obs, was, is) -> {
            if (!is) {
                passwordField.clear();
            }
        });
    }

    private int addTlsField(GridPane grid, int row, String label, javafx.scene.layout.Region input) {
        Label fieldLabel = new Label(label);
        fieldLabel.visibleProperty().bind(sslBox.selectedProperty());
        fieldLabel.managedProperty().bind(sslBox.selectedProperty());
        input.visibleProperty().bind(sslBox.selectedProperty());
        input.managedProperty().bind(sslBox.selectedProperty());
        grid.add(fieldLabel, 0, row);
        grid.add(input, 1, row);
        GridPane.setHgrow(input, Priority.ALWAYS);
        return row + 1;
    }

    private HBox createStoreField(TextField field) {
        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select keystore file");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(
                            "Keystore files (*.p12, *.pfx, *.pkcs12, *.keystore, *.jks)",
                            "*.p12", "*.pfx", "*.pkcs12", "*.keystore", "*.jks"),
                    new FileChooser.ExtensionFilter("All files (*.*)", "*.*"));
            chooser.setInitialDirectory(initialDirectory(field));
            Window window = null;
            var scene = getDialogPane().getScene();
            if (scene != null) {
                window = scene.getWindow();
            }
            File selected = chooser.showOpenDialog(window);
            if (selected != null) {
                field.setText(selected.getAbsolutePath());
            }
        });
        HBox box = new HBox(6, field, browseButton);
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
    }

    private static File initialDirectory(TextField field) {
        String current = field.getText();
        if (current != null && !current.isBlank()) {
            Path path = Path.of(current.trim()).toAbsolutePath();
            if (Files.isDirectory(path)) {
                return path.toFile();
            }
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                return parent.toFile();
            }
        }
        return new File(System.getProperty("user.home"));
    }

    private void applyProfile(List<ConnectionProfile> profiles) {
        String selected = profileNameBox.getValue();
        if (selected == null) {
            return;
        }
        profiles.stream()
                .filter(p -> p.name().equalsIgnoreCase(selected))
                .findFirst()
                .ifPresent(p -> {
                    protocolBox.setValue(p.protocol());
                    hostField.setText(p.host());
                    portField.setText(String.valueOf(p.port()));
                    userField.setText(p.username() == null ? "" : p.username());
                    passwordField.setText(p.password() == null ? "" : p.password());
                    sslBox.setSelected(p.ssl());
                    trustStoreField.setText(p.trustStorePath() == null ? "" : p.trustStorePath());
                    trustStorePasswordField.setText(p.trustStorePassword() == null ? "" : p.trustStorePassword());
                    keyStoreField.setText(p.keyStorePath() == null ? "" : p.keyStorePath());
                    keyStorePasswordField.setText(p.keyStorePassword() == null ? "" : p.keyStorePassword());
                    savePasswordBox.setSelected(p.persistPassword());
                });
    }

    private void connect(Button connectButton) {
        Optional<ConnectionProfile> profile = buildProfile();
        if (profile.isEmpty()) {
            return;
        }
        connectButton.setDisable(true);
        statusLabel.setText("Connecting to " + profile.get().displayUrl() + " ...");
        executor.call(() -> (BrokerClient) BrokerClientFactory.create(profile.get()))
                .whenComplete((client, error) -> Platform.runLater(() -> {
                    connectButton.setDisable(false);
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        String message = cause instanceof BrokerException be
                                ? be.getMessage()
                                : "Connection failed: " + cause.getMessage();
                        statusLabel.setText(message);
                        if (cause instanceof BrokerException be && be.isAccessDenied()
                                && message.contains("ActiveMQ.Advisory")) {
                            Alert info = new Alert(Alert.AlertType.INFORMATION);
                            info.setTitle("Argus - connect failed");
                            info.setHeaderText("You do not have permission to list queues on this broker");
                            info.setContentText("The connected user lacks the broker permissions required "
                                    + "for destination listing, so the connection could not be completed. "
                                    + "You can still switch protocol to Core or ask the broker admin for "
                                    + "listing permissions."
                                    + System.lineSeparator() + System.lineSeparator()
                                    + "Reported by broker: " + message);
                            info.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                            try {
                                if (getDialogPane().getScene() != null
                                        && getDialogPane().getScene().getWindow() != null) {
                                    info.initOwner(getDialogPane().getScene().getWindow());
                                }
                            } catch (Throwable ignored) {
                            }
                            info.show();
                            return;
                        }
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Argus - connection failed");
                        alert.setHeaderText(null);
                        alert.setContentText(message);
                        alert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                        try {
                            if (getDialogPane().getScene() != null
                                    && getDialogPane().getScene().getWindow() != null) {
                                alert.initOwner(getDialogPane().getScene().getWindow());
                            }
                        } catch (Throwable ignored) {
                        }
                        alert.show();
                        return;
                    }
                    createdClient.set(client);
                    connectedProfile = profile.get();
                    try {
                        ConnectionProfile persisted = profile.get();
                        if (!persisted.persistPassword()) {
                            persisted = persisted.withoutSecret();
                        }
                        store.upsert(persisted);
                    } catch (Exception ignored) {
                    }
                    setResult(profile.get());
                    close();
                }));
    }

    private Optional<ConnectionProfile> buildProfile() {
        String name = profileNameBox.getValue();
        if (name == null || name.isBlank()) {
            statusLabel.setText("Profile name is required");
            return Optional.empty();
        }
        String host = hostField.getText();
        if (host == null || host.isBlank()) {
            statusLabel.setText("Host is required");
            return Optional.empty();
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Port must be a number between 1 and 65535");
            return Optional.empty();
        }
        boolean persistPassword = savePasswordBox.isSelected();
        String password = persistPassword ? passwordField.getText() : null;
        return Optional.of(new ConnectionProfile(
                name.trim(),
                protocolBox.getValue(),
                host.trim(),
                port,
                userField.getText().trim(),
                password,
                sslBox.isSelected(),
                persistPassword,
                blankToNull(trustStoreField.getText()),
                blankToNull(trustStorePasswordField.getText()),
                blankToNull(keyStoreField.getText()),
                blankToNull(keyStorePasswordField.getText())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public BrokerClient createdClient() {
        return createdClient.get();
    }

    public ConnectionProfile connectedProfile() {
        return connectedProfile;
    }

    public void shutdownExecutor() {
        executor.shutdown();
    }
}
