package com.redpill_linpro.argus.ui;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.redpill_linpro.argus.broker.BrokerClient;
import com.redpill_linpro.argus.broker.BrokerException;
import com.redpill_linpro.argus.model.AddressInfo;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.DestinationType;
import com.redpill_linpro.argus.model.MessageDraft;
import com.redpill_linpro.argus.model.MessageSnapshot;
import com.redpill_linpro.argus.model.QueueInfo;
import com.redpill_linpro.argus.util.BrokerExecutor;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.stage.Modality;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

public class MainController {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @FXML
    private TreeView<Object> tree;
    @FXML
    private Button refreshButton;
    @FXML
    private Button disconnectButton;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label browseQueueLabel;
    @FXML
    private Spinner<Integer> maxMessagesSpinner;
    @FXML
    private TextField selectorField;
    @FXML
    private TableView<MessageSnapshot> messagesTable;
    @FXML
    private TableColumn<MessageSnapshot, String> colId;
    @FXML
    private TableColumn<MessageSnapshot, String> colTimestamp;
    @FXML
    private TableColumn<MessageSnapshot, String> colType;
    @FXML
    private TableColumn<MessageSnapshot, String> colPriority;
    @FXML
    private TableColumn<MessageSnapshot, String> colDeliveryCount;
    @FXML
    private TableColumn<MessageSnapshot, String> colProps;
    @FXML
    private TableColumn<MessageSnapshot, String> colBody;
    @FXML
    private Label headerMessageId;
    @FXML
    private Label headerCorrelationId;
    @FXML
    private Label headerTimestamp;
    @FXML
    private Label headerExpiration;
    @FXML
    private Label headerPriority;
    @FXML
    private Label headerDeliveryCount;
    @FXML
    private Label headerRedelivered;
    @FXML
    private Label headerType;
    @FXML
    private TableView<Map.Entry<String, Object>> messagePropertiesTable;
    @FXML
    private TableColumn<Map.Entry<String, Object>, String> colPropName;
    @FXML
    private TableColumn<Map.Entry<String, Object>, String> colPropValue;
    @FXML
    private TextArea bodyArea;
    @FXML
    private TextField sendDestinationField;
    @FXML
    private RadioButton queueRadio;
    @FXML
    private RadioButton topicRadio;
    @FXML
    private RadioButton textBodyRadio;
    @FXML
    private RadioButton bytesBodyRadio;
    @FXML
    private TextArea sendBodyArea;
    @FXML
    private TextArea sendPropertiesArea;
    @FXML
    private Button sendButton;
    @FXML
    private Label sendStatusLabel;
    @FXML
    private TextArea detailsArea;
    @FXML
    private Label statusLabel;

    private ConnectionProfile profile;
    private BrokerClient client;
    private final BrokerExecutor executor = new BrokerExecutor();

    private final ObservableList<Map.Entry<String, Object>> messageProperties =
            FXCollections.observableArrayList();

    private QueueInfo lastQueueSelection;
    private String lastSelectedAddressName;
    private final javafx.animation.PauseTransition destinationResolveDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));

    public void init(ConnectionProfile profile, BrokerClient client) {
        this.profile = profile;
        this.client = client;
        onRefreshAddresses();
        connectionLabel.setText(client.brokerInfo() + " / " + profile.displayUrl());
    }

    @FXML
    private void initialize() {
        maxMessagesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1_000_000, 200));

        colId.setCellValueFactory(d -> new ReadOnlyStringWrapper(nullSafe(d.getValue().messageId())));
        colTimestamp.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                d.getValue().timestamp() <= 0 ? "-" : TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(d.getValue().timestamp()))));
        colType.setCellValueFactory(d -> new ReadOnlyStringWrapper(nullSafe(d.getValue().type())));
        colPriority.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().priority())));
        colDeliveryCount.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().deliveryCount())));
        colProps.setCellValueFactory(d -> new ReadOnlyStringWrapper(summarize(d.getValue().properties())));
        colBody.setCellValueFactory(d -> new ReadOnlyStringWrapper(truncate(d.getValue().body())));

        colPropName.setCellValueFactory(d -> new ReadOnlyStringWrapper(nullSafe(d.getValue().getKey())));
        colPropValue.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().getValue())));
        messagePropertiesTable.setItems(messageProperties);

        messagesTable.getSelectionModel().selectedItemProperty().addListener((obs, was, selected) -> {
            if (selected != null) {
                showMessage(selected);
            }
        });

        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else if (item instanceof AddressInfo address) {
                    setText(address.name() + (address.routingTypes().isEmpty() ? "" : " [" + String.join(",", address.routingTypes()) + "]"));
                } else if (item instanceof QueueInfo queue) {
                    setText(queue.name() + " [" + queue.routingType() + "] ("
                            + (queue.messageCount() < 0 ? "n/a" : queue.messageCount()) + " msg, "
                            + (queue.consumerCount() < 0 ? "n/a" : queue.consumerCount()) + " cons)");
                } else if (item instanceof String s) {
                    setText(s);
                } else {
                    setText(String.valueOf(item));
                }
            }
        });

        tree.getSelectionModel().selectedItemProperty().addListener((obs, was, selected) -> {
            if (selected != null) {
                onSelection(selected);
            }
        });

        tree.setOnMouseClicked(this::onTreeClicked);

        sendDestinationField.textProperty().addListener((obs, was, now) -> {
            if (!now.isBlank()) {
                scheduleDestinationTypeResolve();
            }
        });
    }

    private void scheduleDestinationTypeResolve() {
        destinationResolveDelay.stop();
        destinationResolveDelay.setOnFinished(event -> refreshDestinationRadios());
        destinationResolveDelay.playFromStart();
    }

    private void refreshDestinationRadios() {
        if (client == null) {
            return;
        }
        String destination = sendDestinationField.getText().trim();
        if (destination.isBlank()) {
            applyResolvedDestinationType(null);
            return;
        }
        executor.call(() -> client.resolveDestinationType(destination))
                .whenComplete((resolved, error) -> Platform.runLater(() -> {
                    String current = sendDestinationField.getText().trim();
                    if (!current.equals(destination)) {
                        return;
                    }
                    applyResolvedDestinationType(error == null ? resolved.orElse(null) : null);
                }));
    }

    private void applyResolvedDestinationType(DestinationType resolved) {
        boolean known = resolved != null;
        queueRadio.setDisable(known);
        topicRadio.setDisable(known);
        if (known) {
            (resolved == DestinationType.TOPIC ? topicRadio : queueRadio).setSelected(true);
            if (lastQueueSelection == null || !lastQueueSelection.address().equals(sendDestinationField.getText().trim())) {
                statusLabel.setText("Destination '" + sendDestinationField.getText().trim() + "' is a "
                        + resolved.name().toLowerCase() + " (routing type locked)");
            }
        }
    }

    private void onTreeClicked(javafx.scene.input.MouseEvent event) {
        if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY || event.getClickCount() != 2) {
            return;
        }
        Object picked = event.getPickResult() == null ? null : event.getPickResult().getIntersectedNode();
        if (picked == null || picked == tree) {
            return;
        }
        TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
        if (item != null && item.getValue() instanceof QueueInfo) {
            onBrowse();
        }
    }

    private void onSelection(TreeItem<Object> item) {
        Object value = item.getValue();
        if (value instanceof AddressInfo address) {
            loadAddressDetails(address);
            sendDestinationField.setText(address.name());
            lastSelectedAddressName = address.name();
            if (!item.isExpanded()) {
                item.setExpanded(true);
            }
        } else if (value instanceof QueueInfo queue) {
            loadQueueDetails(queue);
            sendDestinationField.setText(queue.address().equals(queue.name())
                    ? queue.name()
                    : queue.address() + "::" + queue.name());
            lastQueueSelection = queue;
            lastSelectedAddressName = queue.address();
        }
    }

    private void loadAddressDetails(AddressInfo address) {
        setDetails("Address: " + address.name() + System.lineSeparator()
                + "Routing types: " + (address.routingTypes().isEmpty() ? "n/a" : String.join(", ", address.routingTypes())));
    }

    private void loadQueueDetails(QueueInfo queue) {
        setDetails("Queue: " + queue.name() + System.lineSeparator()
                + "Address: " + queue.address() + System.lineSeparator()
                + "Routing type: " + queue.routingType() + System.lineSeparator()
                + "Message count: " + formatCount(queue.messageCount()) + System.lineSeparator()
                + "Consumer count: " + formatCount(queue.consumerCount()) + System.lineSeparator()
                + "Durable: " + queue.durable());
    }

    private void setDetails(String text) {
        detailsArea.setText(text);
    }

    @FXML
    private void onRefreshAddresses() {
        if (client == null) {
            return;
        }
        java.util.Set<String> expandedAddresses = new java.util.HashSet<>();
        TreeItem<Object> previousRoot = tree.getRoot();
        if (previousRoot != null) {
            for (TreeItem<Object> child : previousRoot.getChildren()) {
                if (child.getValue() instanceof AddressInfo info && child.isExpanded()) {
                    expandedAddresses.add(info.name());
                }
            }
        }
        statusLabel.setText("Loading addresses...");
        String brokerLabel = profile.displayUrl();
        TreeItem<Object> root = new TreeItem<>(brokerLabel);
        root.setExpanded(true);
        tree.setRoot(root);
        executor.call(client::listAddresses).whenComplete((addresses, error) -> Platform.runLater(() -> {
            if (error != null) {
                showError(addressesError(error));
                return;
            }
            root.getChildren().clear();
            for (AddressInfo address : addresses) {
                TreeItem<Object> addressItem = new TreeItem<>(address);
                attachLazyQueues(addressItem, address);
                if (expandedAddresses.contains(address.name())) {
                    addressItem.setExpanded(true);
                }
                root.getChildren().add(addressItem);
            }
            if (lastSelectedAddressName != null && lastQueueSelection == null) {
                for (TreeItem<Object> addressItem : root.getChildren()) {
                    if (addressItem.getValue() instanceof AddressInfo info
                            && info.name().equals(lastSelectedAddressName)) {
                        tree.getSelectionModel().select(addressItem);
                        break;
                    }
                }
            }
            statusLabel.setText(addresses.size() + " addresses");
        }));
    }

    private String addressesError(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        String base = "Failed to list addresses: " + cause.getMessage();
        if (cause instanceof BrokerException be && be.isAccessDenied()) {
            return base + " (needs 'manage' permission on " + "activemq.management"
                    + "; you can still browse by entering a queue name manually)";
        }
        if (client instanceof com.redpill_linpro.argus.broker.OpenWireBrokerClient ow && ow.isAdvisoryError()) {
            return base + " (destination advisories unavailable; enter destination names manually)";
        }
        return base;
    }

    private void attachLazyQueues(TreeItem<Object> addressItem, AddressInfo address) {
        addressItem.expandedProperty().addListener((obs, was, is) -> {
            if (Boolean.TRUE.equals(is) && addressItem.getChildren().isEmpty()) {
                statusLabel.setText("Loading queues for " + address.name() + "...");
                loadQueues(addressItem, address.name());
            }
        });
    }

    private void loadQueues(TreeItem<Object> addressItem, String addressName) {
        executor.call(() -> client.listQueues(addressName)).whenComplete((queues, error) ->
                Platform.runLater(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        showError("Failed to list queues for '" + addressName + "': " + cause.getMessage());
                        return;
                    }
                    Map<String, TreeItem<Object>> existing = new java.util.HashMap<>();
                    for (TreeItem<Object> child : addressItem.getChildren()) {
                        if (child.getValue() instanceof QueueInfo info) {
                            existing.put(info.name(), child);
                        }
                    }
                    List<TreeItem<Object>> updated = new ArrayList<>();
                    for (QueueInfo queue : queues) {
                        TreeItem<Object> item = existing.get(queue.name());
                        if (item == null) {
                            item = new TreeItem<>(queue);
                        } else {
                            item.setValue(queue);
                        }
                        updated.add(item);
                    }
                    addressItem.getChildren().setAll(updated);
                    if (queues.isEmpty()) {
                        addressItem.getChildren().add(new TreeItem<>("(no queues)"));
                    }
                    if (lastQueueSelection != null && addressName.equals(lastQueueSelection.address())) {
                        for (TreeItem<Object> child : addressItem.getChildren()) {
                            if (child.getValue() instanceof QueueInfo info
                                    && info.name().equals(lastQueueSelection.name())) {
                                tree.getSelectionModel().select(child);
                                lastQueueSelection = info;
                                break;
                            }
                        }
                    }
                    statusLabel.setText(queues.size() + " queues on " + addressName);
                }));
    }

    @FXML
    private void onBrowse() {
        SelectedQueue selected = selectedQueue();
        if (selected == null) {
            statusLabel.setText("Double-click a queue in the tree to browse it");
            return;
        }
        int max = maxMessagesSpinner.getValue();
        String selector = selectorField.getText();
        browseQueueLabel.setText(selected.address() + " :: " + selected.queueName());
        statusLabel.setText("Browsing " + selected.queueName() + "...");
        executor.call(() -> client.browse(selected.address(), selected.queueName(), max, blankToNull(selector)))
                .whenComplete((messages, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showError(browseError(error, selected));
                        return;
                    }
                    messagesTable.getItems().setAll(messages);
                    showMessage(null);
                    statusLabel.setText(messages.size() + " messages on "
                            + selected.address() + " :: " + selected.queueName());
                }));
    }

    private String browseError(Throwable error, SelectedQueue selected) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        if (cause instanceof BrokerException be && be.isAccessDenied()) {
            return "Access denied browsing '" + selected.queueName()
                    + "' (needs 'browse' permission on the queue)";
        }
        return "Browse failed: " + cause.getMessage();
    }

    private void showMessage(MessageSnapshot message) {
        if (message == null) {
            headerMessageId.setText("-");
            headerCorrelationId.setText("-");
            headerTimestamp.setText("-");
            headerExpiration.setText("-");
            headerPriority.setText("-");
            headerDeliveryCount.setText("-");
            headerRedelivered.setText("-");
            headerType.setText("-");
            messageProperties.clear();
            bodyArea.setText("");
            return;
        }
        headerMessageId.setText(nullSafe(message.messageId()));
        headerCorrelationId.setText(message.correlationId() == null ? "-" : message.correlationId());
        headerTimestamp.setText(message.timestamp() <= 0 ? "-" : TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(message.timestamp())));
        headerExpiration.setText(message.expiration() <= 0 ? "never" : TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(message.expiration())));
        headerPriority.setText(String.valueOf(message.priority()));
        headerDeliveryCount.setText(String.valueOf(message.deliveryCount()));
        headerRedelivered.setText(String.valueOf(message.redelivered()));
        headerType.setText(nullSafe(message.type()));
        messageProperties.clear();
        if (message.properties() != null) {
            for (Map.Entry<String, Object> entry : message.properties().entrySet()) {
                messageProperties.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
        }
        bodyArea.setText(message.body() == null ? "" : message.body());
    }

    @FXML
    private void onSend() {
        String destination = sendDestinationField.getText();
        if (destination == null || destination.isBlank()) {
            sendStatusLabel.setText("Destination required");
            return;
        }
        DestinationType type = topicRadio.isSelected() ? DestinationType.TOPIC : DestinationType.QUEUE;
        MessageDraft.Kind kind = bytesBodyRadio.isSelected() ? MessageDraft.Kind.BYTES_UTF8 : MessageDraft.Kind.TEXT;
        List<Map.Entry<String, String>> properties;
        try {
            properties = parseProperties(sendPropertiesArea.getText());
        } catch (IllegalArgumentException e) {
            sendStatusLabel.setText(e.getMessage());
            return;
        }
        Map<String, String> props = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties) {
            props.put(entry.getKey(), entry.getValue());
        }
        MessageDraft draft = new MessageDraft(kind, sendBodyArea.getText(), props);
        sendButton.setDisable(true);
        sendStatusLabel.setText("Sending to " + (type == DestinationType.TOPIC ? "topic " : "queue ") + destination + " ...");
        executor.call(() -> {
            client.send(destination.trim(), type, draft);
            return null;
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            sendButton.setDisable(false);
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                sendStatusLabel.setText("Send failed");
                showError("Send to '" + destination + "' failed: " + cause.getMessage());
                return;
            }
            sendStatusLabel.setText("Sent to " + destination);
            statusLabel.setText("Message sent to " + (type == DestinationType.TOPIC ? "topic '" : "queue '") + destination + "'");
        }));
    }

    private List<Map.Entry<String, String>> parseProperties(String text) {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int splitAt = trimmed.indexOf('=');
            if (splitAt <= 0) {
                throw new IllegalArgumentException("Invalid property line (expected key = value): " + line);
            }
            String key = trimmed.substring(0, splitAt).trim();
            String value = trimmed.substring(splitAt + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Empty property key in line: " + line);
            }
            result.add(new AbstractMap.SimpleEntry<>(key, value));
        }
        return result;
    }

    @FXML
    private void onDisconnect() {
        shutdown();
        Stage stage = (Stage) disconnectButton.getScene().getWindow();
        stage.close();
        Platform.exit();
    }

    private SelectedQueue selectedQueue() {
        TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
        if (item != null) {
            Object value = item.getValue();
            if (value instanceof QueueInfo queue) {
                lastQueueSelection = queue;
                return new SelectedQueue(queue.name(), queue.address());
            }
            if (value instanceof AddressInfo) {
                QueueInfo firstQueue = item.getChildren().stream()
                        .filter(child -> child.getValue() instanceof QueueInfo)
                        .map(child -> (QueueInfo) child.getValue())
                        .findFirst().orElse(null);
                if (firstQueue != null) {
                    lastQueueSelection = firstQueue;
                    return new SelectedQueue(firstQueue.name(), firstQueue.address());
                }
            }
        }
        if (lastQueueSelection != null) {
            return new SelectedQueue(lastQueueSelection.name(), lastQueueSelection.address());
        }
        return null;
    }

    private record SelectedQueue(String queueName, String address) {
    }

    private static String summarize(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return truncate(sb.toString());
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 140 ? text : text.substring(0, 140) + "...";
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String formatCount(long count) {
        return count < 0 ? "n/a" : String.valueOf(count);
    }

    private void setStatusError(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        setStatusError(message);
        if ("false".equalsIgnoreCase(System.getProperty("argus.ui.errorDialogs"))) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Argus");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initModality(Modality.APPLICATION_MODAL);
        try {
            var scene = statusLabel.getScene();
            if (scene != null && scene.getWindow() != null) {
                alert.initOwner(scene.getWindow());
            }
        } catch (Throwable ignored) {
        }
        alert.show();
    }

    public void shutdown() {
        executor.shutdown();
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
