package com.redpill_linpro.argus.ui;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.redpill_linpro.argus.broker.BrokerClient;
import com.redpill_linpro.argus.broker.BrokerException;
import com.redpill_linpro.argus.broker.Subscription;
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
import javafx.scene.control.ToggleButton;
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
    private Button addAddressButton;
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
    private Button subscribeButton;
    @FXML
    private ToggleButton autoRefreshButton;
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
    private final Map<String, String> manualAddresses = new java.util.LinkedHashMap<>();
    private Runnable onDisconnected;

    public void setOnDisconnected(Runnable onDisconnected) {
        this.onDisconnected = onDisconnected;
    }
    private final javafx.animation.PauseTransition destinationResolveDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));

    private SelectedQueue browsedQueue;
    private long lastSeenCount = Long.MIN_VALUE;
    private final AtomicBoolean pollInFlight = new AtomicBoolean(false);
    private final javafx.animation.Timeline autoRefreshTimer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3), e -> autoRefreshTick()));

    private Subscription subscription;
    private String subscribedAddress;
    private long subscribedCount;

    public void init(ConnectionProfile profile, BrokerClient client) {
        this.profile = profile;
        this.client = client;
        onRefreshAddresses();
        connectionLabel.setText(profile.displayUrl());
        executor.call(client::brokerInfo).whenComplete((info, error) -> Platform.runLater(() -> {
            if (info != null) {
                connectionLabel.setText(info + " / " + profile.displayUrl());
            }
        }));
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

        autoRefreshTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        autoRefreshButton.selectedProperty().addListener((obs, was, on) -> {
            if (on && browsedQueue != null) {
                autoRefreshTick();
            }
            updateAutoRefreshTimer();
        });
        autoRefreshButton.setDisable(true);
    }

    private void updateAutoRefreshTimer() {
        if (autoRefreshButton.isSelected()) {
            autoRefreshTimer.playFromStart();
        } else {
            autoRefreshTimer.stop();
        }
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
        } else if (item != null && subscribeTarget() != null) {
            onToggleSubscribe();
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
        updateSubscribeButton();
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
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                if (cause instanceof BrokerException be && be.isAccessDenied()) {
                    showListingDenied(addressesError(error));
                } else {
                    showError(addressesError(error));
                }
                reattachManualAddresses(root);
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
            reattachManualAddresses(root);
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
            if (client instanceof com.redpill_linpro.argus.broker.OpenWireBrokerClient) {
                return base + " (needs createNonDurableQueue/consume permission on "
                        + "ActiveMQ.Advisory.#; enter destination names manually)";
            }
            return base + " (needs 'manage' permission on " + "activemq.management"
                    + "; you can still browse by entering a queue name manually)";
        }
        if (client instanceof com.redpill_linpro.argus.broker.OpenWireBrokerClient ow && ow.isAdvisoryError()) {
            return base + " (destination advisories unavailable; enter destination names manually)";
        }
        return base;
    }

    @FXML
    private void onAddAddress() {
        if (client == null || tree.getRoot() == null) {
            statusLabel.setText("Connect to a broker before adding addresses");
            return;
        }
        AddAddressDialog.Result result = new AddAddressDialog().showAndWait().orElse(null);
        if (result == null) {
            return;
        }
        String address = result.address() == null ? "" : result.address().trim();
        if (address.isEmpty()) {
            statusLabel.setText("Address name required");
            return;
        }
        String queue = result.queue() == null ? "" : result.queue().trim();
        manualAddresses.put(address, queue);
        TreeItem<Object> addressItem = attachManualAddress(tree.getRoot(), address, queue);
        selectManualEntry(addressItem);
        statusLabel.setText("Added '" + address + "' manually to the tree");
    }

    void addManualDestination(String address, String queue) {
        if (tree.getRoot() == null) {
            statusLabel.setText("Connect to a broker before adding addresses");
            return;
        }
        manualAddresses.put(address, queue == null ? "" : queue);
        TreeItem<Object> addressItem = attachManualAddress(tree.getRoot(), address, queue == null ? "" : queue);
        selectManualEntry(addressItem);
        statusLabel.setText("Added '" + address + "' manually to the tree");
    }

    private TreeItem<Object> attachManualAddress(TreeItem<Object> root, String address, String queue) {
        for (TreeItem<Object> child : root.getChildren()) {
            if (child.getValue() instanceof AddressInfo info && info.name().equals(address)) {
                ensureManualQueueChild(child, address, queue);
                return child;
            }
        }
        TreeItem<Object> addressItem = new TreeItem<>(new AddressInfo(address, List.of("MANUAL")));
        if (!queue.isEmpty()) {
            addressItem.getChildren().add(new TreeItem<>(
                    new QueueInfo(queue, address, "ANYCAST", -1, -1, true)));
        }
        attachLazyQueues(addressItem, new AddressInfo(address, List.of("MANUAL")));
        root.getChildren().add(addressItem);
        return addressItem;
    }

    private void ensureManualQueueChild(TreeItem<Object> addressItem, String address, String queue) {
        if (queue.isEmpty()) {
            return;
        }
        boolean present = addressItem.getChildren().stream()
                .anyMatch(item -> item.getValue() instanceof QueueInfo q && q.name().equals(queue));
        if (!present) {
            addressItem.getChildren().add(new TreeItem<>(
                    new QueueInfo(queue, address, "ANYCAST", -1, -1, true)));
        }
    }

    private void selectManualEntry(TreeItem<Object> addressItem) {
        TreeItem<Object> target = addressItem;
        for (TreeItem<Object> child : addressItem.getChildren()) {
            if (child.getValue() instanceof QueueInfo) {
                target = child;
                break;
            }
        }
        tree.getSelectionModel().select(target);
    }

    private void reattachManualAddresses(TreeItem<Object> root) {
        for (Map.Entry<String, String> entry : manualAddresses.entrySet()) {
            attachManualAddress(root, entry.getKey(), entry.getValue());
        }
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
                        if (cause instanceof BrokerException be && be.isAccessDenied()) {
                            addressItem.getChildren().setAll(new TreeItem<>("(listing not permitted)"));
                            statusLabel.setText("Queue listing not permitted for '" + addressName
                                    + "'; manual entries remain browsable");
                            return;
                        }
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
        stopSubscription();
        int max = maxMessagesSpinner.getValue();
        String selector = selectorField.getText();
        SelectedQueue target = new SelectedQueue(selected.queueName(), selected.address(), max, blankToNull(selector));
        browseQueueLabel.setText(target.address() + " :: " + target.queueName());
        statusLabel.setText("Browsing " + target.queueName() + "...");
        executor.call(() -> client.browse(target.address(), target.queueName(), target.maxMessages(), target.selector()))
                .whenComplete((messages, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showError(browseError(error, target));
                        return;
                    }
                    browsedQueue = target;
                    lastSeenCount = Long.MIN_VALUE;
                    autoRefreshButton.setDisable(false);
                    if (autoRefreshButton.isSelected()) {
                        updateAutoRefreshTimer();
                    }
                    applyMessages(target, messages);
                }));
    }

    private void applyMessages(SelectedQueue target, List<MessageSnapshot> messages) {
        messagesTable.getItems().setAll(messages);
        showMessage(null);
        statusLabel.setText(messages.size() + " messages on "
                + target.address() + " :: " + target.queueName());
    }

    private void autoRefreshTick() {
        if (pollInFlight.get() || subscription != null) {
            return;
        }
        SelectedQueue target = browsedQueue;
        if (target == null || client == null) {
            return;
        }
        pollInFlight.set(true);
        executor.call(() -> {
            boolean countKnown = target.selector() == null
                    && profile.protocol() == com.redpill_linpro.argus.model.Protocol.CORE;
            long count = countKnown ? client.messageCount(target.address(), target.queueName()) : Long.MIN_VALUE;
            boolean refreshNeeded = count == Long.MIN_VALUE || count != lastSeenCount;
            List<MessageSnapshot> messages = refreshNeeded
                    ? client.browse(target.address(), target.queueName(), target.maxMessages(), target.selector())
                    : null;
            return new AutoRefreshResult(count, messages);
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            pollInFlight.set(false);
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                statusLabel.setText("Auto-refresh failed: " + cause.getMessage());
                autoRefreshButton.setSelected(false);
                return;
            }
            if (result.count() != Long.MIN_VALUE) {
                lastSeenCount = result.count();
            }
            if (result.messages() != null) {
                applyMessages(target, result.messages());
            }
        }));
    }

    private record AutoRefreshResult(long count, List<MessageSnapshot> messages) {
    }

    private String browseError(Throwable error, SelectedQueue selected) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        if (cause instanceof BrokerException be && be.isAccessDenied()) {
            return "Access denied browsing '" + selected.queueName()
                    + "' (needs 'browse' permission on the queue)";
        }
        return "Browse failed: " + cause.getMessage();
    }

    private AddressInfo subscribeTarget() {
        TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
        if (item == null) {
            return null;
        }
        Object value = item.getValue();
        if (value instanceof AddressInfo address && address.routingTypes().contains("MULTICAST")) {
            return address;
        }
        if (value instanceof QueueInfo queue && "MULTICAST".equals(queue.routingType())) {
            return new AddressInfo(queue.address(), List.of(queue.routingType()));
        }
        return null;
    }

    private void updateSubscribeButton() {
        AddressInfo target = subscribeTarget();
        if (subscription != null && (target == null || target.name().equals(subscribedAddress))) {
            subscribeButton.setText("Unsubscribe");
            subscribeButton.setDisable(false);
        } else if (target != null) {
            subscribeButton.setText("Subscribe");
            subscribeButton.setDisable(false);
        } else {
            subscribeButton.setText("Subscribe");
            subscribeButton.setDisable(true);
        }
    }

    @FXML
    private void onToggleSubscribe() {
        AddressInfo target = subscribeTarget();
        if (subscription != null && (target == null || target.name().equals(subscribedAddress))) {
            stopSubscription();
            return;
        }
        if (target == null) {
            statusLabel.setText("Select a multicast address in the tree to subscribe");
            return;
        }
        startSubscribe(target.name(), blankToNull(selectorField.getText()));
    }

    private void startSubscribe(String address, String selector) {
        stopSubscription();
        statusLabel.setText("Subscribing to '" + address + "'...");
        subscribeButton.setDisable(true);
        messagesTable.getItems().clear();
        showMessage(null);
        executor.call(() -> client.subscribe(address, selector, this::onSubscribedMessage))
                .whenComplete((sub, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showError(subscribeError(error, address));
                        updateSubscribeButton();
                        return;
                    }
                    subscription = sub;
                    subscribedAddress = address;
                    subscribedCount = 0;
                    browseQueueLabel.setText(address + " (live)");
                    autoRefreshButton.setSelected(false);
                    autoRefreshButton.setDisable(true);
                    statusLabel.setText("Subscribed to '" + address + "' - waiting for messages");
                    updateSubscribeButton();
                }));
    }

    private void stopSubscription() {
        Subscription current = subscription;
        subscription = null;
        subscribedAddress = null;
        subscribedCount = 0;
        if (current != null) {
            executor.call(() -> {
                current.close();
                return null;
            });
        }
        autoRefreshButton.setDisable(browsedQueue == null);
        updateSubscribeButton();
    }

    private void onSubscribedMessage(MessageSnapshot message) {
        Platform.runLater(() -> {
            if (subscription == null) {
                return;
            }
            subscribedCount++;
            javafx.collections.ObservableList<MessageSnapshot> items = messagesTable.getItems();
            items.add(message);
            int max = maxMessagesSpinner.getValue();
            while (items.size() > max) {
                items.remove(0);
            }
            statusLabel.setText(subscribedCount + (subscribedCount == 1 ? " message from '" : " messages from '")
                    + subscribedAddress + "'");
        });
    }

    private String subscribeError(Throwable error, String address) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return "Subscribe to '" + address + "' failed: " + cause.getMessage();
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
        if (onDisconnected != null) {
            onDisconnected.run();
            return;
        }
        Stage stage = (Stage) disconnectButton.getScene().getWindow();
        stage.close();
    }

    private SelectedQueue selectedQueue() {
        TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
        if (item != null) {
            Object value = item.getValue();
            if (value instanceof QueueInfo queue) {
                lastQueueSelection = queue;
                return new SelectedQueue(queue.name(), queue.address(), -1, null);
            }
            if (value instanceof AddressInfo) {
                QueueInfo firstQueue = item.getChildren().stream()
                        .filter(child -> child.getValue() instanceof QueueInfo)
                        .map(child -> (QueueInfo) child.getValue())
                        .findFirst().orElse(null);
                if (firstQueue != null) {
                    lastQueueSelection = firstQueue;
                    return new SelectedQueue(firstQueue.name(), firstQueue.address(), -1, null);
                }
            }
        }
        if (lastQueueSelection != null) {
            return new SelectedQueue(lastQueueSelection.name(), lastQueueSelection.address(), -1, null);
        }
        return null;
    }

    private record SelectedQueue(String queueName, String address, int maxMessages, String selector) {
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
        showAlert(Alert.AlertType.ERROR, "Argus", null, message);
    }

    private void showListingDenied(String detail) {
        String summary = "You do not have permission to list queues on this broker. You can still "
                + "send messages, subscribe and browse a known queue by entering its destination "
                + "manually on the Send tab.";
        setStatusError(summary + System.lineSeparator() + detail);
        String reported = truncate(detail);
        showAlert(Alert.AlertType.INFORMATION, "Argus - listing not permitted",
                "You do not have permission to list queues on this broker",
                summary + System.lineSeparator() + System.lineSeparator()
                        + "Reported by broker: " + reported);
    }

    private void showAlert(Alert.AlertType type, String title, String headerText, String contentText) {
        if ("false".equalsIgnoreCase(System.getProperty("argus.ui.errorDialogs"))) {
            return;
        }
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
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
        autoRefreshTimer.stop();
        Subscription current = subscription;
        subscription = null;
        subscribedAddress = null;
        if (current != null) {
            executor.call(() -> {
                current.close();
                return null;
            });
        }
        BrokerClient closable = client;
        if (closable != null) {
            executor.call(() -> {
                closable.close();
                return null;
            });
        }
        executor.shutdown();
    }
}
