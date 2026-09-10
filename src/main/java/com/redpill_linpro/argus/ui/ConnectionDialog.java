package com.redpill_linpro.argus.ui;

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

public final class ConnectionDialog extends Dialog<ConnectionProfile> {

    private final ProfileStore store;
    private final BrokerExecutor executor = new BrokerExecutor();

    private final ComboBox<String> profileNameBox = new ComboBox<>();
    private final ComboBox<Protocol> protocolBox = new ComboBox<>();
    private final TextField hostField = new TextField("localhost");
    private final TextField portField = new TextField("61616");
    private final TextField userField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final CheckBox sslBox = new CheckBox("Use SSL");
    private final CheckBox savePasswordBox = new CheckBox("Save password");
    private final Label statusLabel = new Label();
    private final AtomicReference<BrokerClient> createdClient = new AtomicReference<>();

    public ConnectionDialog(ProfileStore store) {
        this.store = store;
        setTitle("Argus");
        setHeaderText("Connect to ActiveMQ Artemis");

        ButtonType connectButtonType = new ButtonType("Connect", ButtonType.OK.getButtonData());
        getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

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
                persistPassword));
    }

    public BrokerClient createdClient() {
        return createdClient.get();
    }

    public void shutdownExecutor() {
        executor.shutdown();
    }
}
