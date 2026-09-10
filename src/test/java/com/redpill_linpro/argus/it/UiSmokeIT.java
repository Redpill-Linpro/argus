package com.redpill_linpro.argus.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.redpill_linpro.argus.broker.BrokerClient;
import com.redpill_linpro.argus.broker.BrokerClientFactory;
import com.redpill_linpro.argus.config.ProfileStore;
import com.redpill_linpro.argus.model.AddressInfo;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.MessageDraft;
import com.redpill_linpro.argus.model.MessageSnapshot;
import com.redpill_linpro.argus.model.Protocol;
import com.redpill_linpro.argus.model.QueueInfo;
import com.redpill_linpro.argus.ui.ConnectionDialog;
import com.redpill_linpro.argus.ui.MainController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UiSmokeIT {

    static ActiveMQServer server;
    static String host;
    static int port;
    static String user;
    static String pass;
    static final java.util.concurrent.atomic.AtomicBoolean fxRunning =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @BeforeAll
    static void setup() throws Exception {
        String display = System.getenv("DISPLAY");
        if ((display == null || display.isBlank()) && !"monocle".equalsIgnoreCase(System.getProperty("glass.platform"))) {
            System.out.println("UI-SMOKE skipped: no X display available");
            org.junit.jupiter.api.Assumptions.abort("no X display available");
        }

        CountDownLatch fxStarted = new CountDownLatch(1);
        try {
            Platform.startup(fxStarted::countDown);
        } catch (IllegalStateException alreadyRunning) {
            fxStarted.countDown();
        }
        assertTrue(fxStarted.await(60, TimeUnit.SECONDS), "JavaFX toolkit did not start");
        fxRunning.set(true);
        System.setProperty("argus.ui.errorDialogs", "false");

        String remoteHost = System.getProperty("argus.it.host");
        if (remoteHost != null && !remoteHost.isBlank()) {
            host = remoteHost;
            port = Integer.getInteger("argus.it.port", 61616);
            user = propOrBlank("argus.it.user");
            pass = propOrBlank("argus.it.pass");
        } else {
            port = EmbeddedArtemisSupport.freePort();
            server = EmbeddedArtemisSupport.start(port, false);
            host = "127.0.0.1";
            user = "argus";
            pass = "arguspw";
            try (BrokerClient preSend = BrokerClientFactory.create(profile())) {
                for (int i = 1; i <= 3; i++) {
                    preSend.send("TEST::TESTQ", com.redpill_linpro.argus.model.DestinationType.QUEUE,
                            new MessageDraft(MessageDraft.Kind.TEXT, "ui-seed-" + i,
                                    java.util.Map.of("seed", String.valueOf(i))));
                }
            }
        }
    }

    @AfterAll
    static void teardown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (fxRunning.getAndSet(false)) {
            try {
                runFx(() -> Platform.exit());
            } catch (Throwable ignored) {
            }
        }
    }

    static void ensureFx() throws Exception {
        if (fxRunning.get()) {
            return;
        }
        CountDownLatch fxStarted = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                fxRunning.set(true);
                fxStarted.countDown();
            });
        } catch (IllegalStateException alreadyRunning) {
            fxRunning.set(true);
            fxStarted.countDown();
        }
        assertTrue(fxStarted.await(60, TimeUnit.SECONDS), "JavaFX toolkit did not (re)start");
    }

    private static ConnectionProfile profile() {
        return new ConnectionProfile("ui-smoke", Protocol.CORE, host, port, user, pass, false, false);
    }

    private static String propOrBlank(String key) {
        String v = System.getProperty(key);
        return v == null ? "" : v;
    }

    static void runFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(60, TimeUnit.SECONDS), "FX task timed out");
        Throwable t = error.get();
        if (t instanceof RuntimeException re) throw re;
        if (t instanceof Exception e) throw e;
        if (t != null) throw new IllegalStateException(t);
    }

    @SuppressWarnings("unchecked")
    static <T> T runFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(60, TimeUnit.SECONDS), "FX task timed out");
        Throwable t = error.get();
        if (t instanceof RuntimeException re) throw re;
        if (t instanceof Exception e) throw e;
        if (t != null) throw new IllegalStateException(t);
        return result.get();
    }

    static void await(String what, BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        org.junit.jupiter.api.Assertions.fail("Timed out waiting for: " + what);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(target);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void invoke(Object target, String method) {
        try {
            Method m = target.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(target);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void doubleClickQueue(TreeView<Object> tree) throws Exception {
        runFx(() -> {
            javafx.scene.input.MouseEvent event = new javafx.scene.input.MouseEvent(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED, 10, 10, 10, 10,
                    javafx.scene.input.MouseButton.PRIMARY, 2,
                    false, false, false, false, true, false, false, false, false, false,
                    new javafx.scene.input.PickResult(new javafx.scene.Group(),
                            javafx.geometry.Point3D.ZERO, 0.0));
            javafx.event.Event.fireEvent(tree, event);
        });
    }

    @Test
    @Order(1)
    void connectionDialogConstructs() throws Exception {
        runFx(() -> {
            ConnectionDialog dialog = new ConnectionDialog(new ProfileStore());
            assertNotNull(dialog.getDialogPane().getContent(), "dialog content should build");
            ButtonType connectButtonType = dialog.getDialogPane().getButtonTypes().stream()
                    .filter(bt -> "Connect".equals(bt.getText()))
                    .findFirst().orElseThrow();
            assertNotNull(dialog.getDialogPane().lookupButton(connectButtonType), "Connect button must exist");
            dialog.close();
        });
    }

    @Test
    @Order(2)
    void mainWindowEndToEnd() throws Exception {
        MainController controller = loadController();
        try {
            TreeView<Object> tree = field(controller, "tree");
            await("addresses to load in tree", () -> {
                try {
                    return runFx(() -> {
                        TreeItem<Object> root = tree.getRoot();
                        return root != null && !root.getChildren().isEmpty();
                    });
                } catch (Exception e) {
                    return false;
                }
            });

            AtomicReference<TreeItem<Object>> found = new AtomicReference<>();
            await("TEST address item", () -> {
                try {
                    found.set(runFx(() -> {
                        TreeItem<Object> root = tree.getRoot();
                        if (root == null) {
                            return null;
                        }
                        for (TreeItem<Object> child : root.getChildren()) {
                            if (child.getValue() instanceof AddressInfo info && "TEST".equals(info.name())) {
                                return child;
                            }
                        }
                        return null;
                    }));
                    return found.get() != null;
                } catch (Exception e) {
                    return false;
                }
            });
            TreeItem<Object> testAddressItem = found.get();

            System.out.println("UI-SMOKE step: setExpanded");
            runFx(() -> testAddressItem.setExpanded(true));
            await("queues under TEST", () -> {
                try {
                    return runFx(() -> testAddressItem.getChildren().stream()
                            .anyMatch(item -> item.getValue() instanceof QueueInfo));
                } catch (Exception e) {
                    return false;
                }
            });

            System.out.println("UI-SMOKE step: select-queue");
            AtomicReference<QueueInfo> queueRef = new AtomicReference<>();
            String labelBeforeSelect = runFx((Callable<String>) () -> {
                Label label = field(controller, "browseQueueLabel");
                return label.getText();
            });
            runFx(() -> {
                TreeItem<Object> queueItem = testAddressItem.getChildren().stream()
                        .filter(item -> item.getValue() instanceof QueueInfo)
                        .findFirst().orElseThrow();
                tree.getSelectionModel().select(queueItem);
                queueRef.set((QueueInfo) queueItem.getValue());
            });
            QueueInfo selectedQueue = queueRef.get();
            String queueName = selectedQueue.name();
            if (server != null) {
                assertEquals("TESTQ", queueName);
            }
            System.out.println("UI-SMOKE queue under TEST: " + queueName);
            String labelAfterSelect = runFx((Callable<String>) () -> {
                Label label = field(controller, "browseQueueLabel");
                return label.getText();
            });
            assertEquals(labelBeforeSelect, labelAfterSelect,
                    "selecting a queue must NOT update the browse label");

            final String queueNameFinal = queueName;
            await("queue details populated", () -> {
                try {
                    TextArea details = field(controller, "detailsArea");
                    return runFx(() -> details.getText().contains(queueNameFinal));
                } catch (Exception e) {
                    return false;
                }
            });

            System.out.println("UI-SMOKE step: double-click queue to browse");
            doubleClickQueue(tree);
            if (server != null) {
                await("browse results in table", () -> {
                    try {
                        return runFx(() -> {
                            TableView<MessageSnapshot> table = field(controller, "messagesTable");
                            return !table.getItems().isEmpty();
                        });
                    } catch (Exception e) {
                        return false;
                    }
                });
            } else {
                await("browse to finish", () -> {
                    try {
                        Label status = field(controller, "statusLabel");
                        String text = runFx((Callable<String>) status::getText);
                        return text.endsWith(")") || text.contains("0 messages") || text.contains("messages on");
                    } catch (Exception e) {
                        return false;
                    }
                });
            }
            String labelAfterBrowse = runFx((Callable<String>) () -> {
                Label label = field(controller, "browseQueueLabel");
                return label.getText();
            });
            assertEquals(selectedQueue.address() + " :: " + selectedQueue.name(), labelAfterBrowse,
                    "browse label must show the browsed target");

            if (server != null) {
                System.out.println("UI-SMOKE step: select-message-row");
                runFx(() -> {
                    TableView<MessageSnapshot> table = field(controller, "messagesTable");
                    table.getSelectionModel().select(0);
                });
                await("message detail shown", () -> {
                    try {
                        return runFx(() -> {
                            Label messageId = field(controller, "headerMessageId");
                            TextArea body = field(controller, "bodyArea");
                            return !messageId.getText().isBlank() && !body.getText().isBlank();
                        });
                    } catch (Exception e) {
                        return false;
                    }
                });
            }

            System.out.println("UI-SMOKE step: fill-send-form");
            String destination = runFx((Callable<String>) () -> {
                TextField destinationField = field(controller, "sendDestinationField");
                return destinationField.getText();
            });
            assertFalse(destination.isBlank(), "send destination should be pre-filled from the tree selection");
            System.out.println("UI-SMOKE send destination: " + destination);
            runFx(() -> {
                TextField destinationField = field(controller, "sendDestinationField");
                destinationField.setText(destination);
                javafx.scene.control.RadioButton queueRadio = field(controller, "queueRadio");
                queueRadio.setSelected(true);
                javafx.scene.control.RadioButton textBody = field(controller, "textBodyRadio");
                textBody.setSelected(true);
                TextArea sendBody = field(controller, "sendBodyArea");
                sendBody.setText("ui-smoke-send");
            });
            System.out.println("UI-SMOKE step: onSend");
            runFx(() -> invoke(controller, "onSend"));
            await("sent message visible after re-browse", () -> {
                try {
                    Boolean done = runFx(() -> {
                        Label sendStatus = field(controller, "sendStatusLabel");
                        return sendStatus.getText().contains("Sent");
                    });
                    if (done) {
                        System.out.println("UI-SMOKE step: onBrowse");
            runFx(() -> invoke(controller, "onBrowse"));
                        return runFx(() -> {
                            TableView<MessageSnapshot> table = field(controller, "messagesTable");
                            return table.getItems().stream()
                                    .anyMatch(m -> m.body() != null && m.body().contains("ui-smoke-send"));
                        });
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                }
            });

            String sendStatus = runFx((Callable<String>) () -> {
                Label statusLabel = field(controller, "sendStatusLabel");
                return statusLabel.getText();
            });
            assertTrue(sendStatus.startsWith("Sent"), "send status: " + sendStatus);
            String connectionLabel = runFx((Callable<String>) () -> {
                Label label = field(controller, "connectionLabel");
                return label.getText();
            });
            assertFalse(connectionLabel.isBlank());

            System.out.println("UI-SMOKE step: destination type resolution (lock radios)");
            javafx.scene.control.RadioButton queueRadio = field(controller, "queueRadio");
            javafx.scene.control.RadioButton topicRadio = field(controller, "topicRadio");
            TextField sendDestinationField = field(controller, "sendDestinationField");
            await("queue radio locked for resolved queue destination", () -> {
                try {
                    return runFx((Callable<Boolean>) () -> queueRadio.isSelected()
                            && queueRadio.isDisabled() && topicRadio.isDisabled());
                } catch (Exception e) {
                    return false;
                }
            });

            System.out.println("UI-SMOKE step: topic destination auto-mark");
            runFx(() -> sendDestinationField.setText("activemq.notifications"));
            await("topic radio locked for multicast destination", () -> {
                try {
                    return runFx((Callable<Boolean>) () -> topicRadio.isSelected()
                            && topicRadio.isDisabled() && queueRadio.isDisabled());
                } catch (Exception e) {
                    return false;
                }
            });

            final String destinationAfterTopicPoke = destination;
            runFx(() -> sendDestinationField.setText(destinationAfterTopicPoke));
            await("queue radio re-locked after restore", () -> {
                try {
                    return runFx((Callable<Boolean>) () -> queueRadio.isSelected()
                            && queueRadio.isDisabled() && topicRadio.isDisabled());
                } catch (Exception e) {
                    return false;
                }
            });

            Path screenshot = Path.of(System.getProperty("argus.ui.screenshot", "target/ui-smoke.png"));
            Files.createDirectories(screenshot.toAbsolutePath().getParent());
            boolean captured = capture(screenshot);
            System.out.println("UI-SMOKE screenshot: " + screenshot.toAbsolutePath() + " captured=" + captured);

            System.out.println("UI-SMOKE step: shutdown (window stays open for next test)");
            runFx(controller::shutdown);
        } finally {
            runFx(() -> {
                BrokerClient client = field(controller, "client");
                if (client != null) {
                    try {
                        client.close();
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }

    @Test
    @Order(3)
    void browseWorksWithAddressSelected() throws Exception {
        ensureFx();
        MainController controller = loadController();
        try {
            TreeView<Object> tree = field(controller, "tree");
            await("addresses to load for address-browse", () -> {
                try {
                    return runFx(() -> {
                        TreeItem<Object> root = tree.getRoot();
                        return root != null && !root.getChildren().isEmpty();
                    });
                } catch (Exception e) {
                    return false;
                }
            });

            AtomicReference<TreeItem<Object>> found = new AtomicReference<>();
            await("TEST address item for address-browse", () -> {
                try {
                    found.set(runFx(() -> {
                        TreeItem<Object> root = tree.getRoot();
                        if (root == null) {
                            return null;
                        }
                        for (TreeItem<Object> child : root.getChildren()) {
                            if (child.getValue() instanceof AddressInfo info && "TEST".equals(info.name())) {
                                return child;
                            }
                        }
                        return null;
                    }));
                    return found.get() != null;
                } catch (Exception e) {
                    return false;
                }
            });

            System.out.println("UI-SMOKE step: expand + select-ADDRESS (user scenario)");
            runFx(() -> {
                found.get().setExpanded(true);
                tree.getSelectionModel().select(found.get());
            });
            await("queues under TEST for address-browse", () -> {
                try {
                    return runFx(() -> found.get().getChildren().stream()
                            .anyMatch(item -> item.getValue() instanceof QueueInfo));
                } catch (Exception e) {
                    return false;
                }
            });
            await("address details panel", () -> {
                try {
                    TextArea details = field(controller, "detailsArea");
                    return runFx((Callable<Boolean>) () -> details.getText().startsWith("Address:"));
                } catch (Exception e) {
                    return false;
                }
            });

            String labelAfterAddressSelect = runFx((Callable<String>) () -> {
                Label label = field(controller, "browseQueueLabel");
                return label.getText();
            });
            assertEquals("-", labelAfterAddressSelect, "selecting an address alone must not browse");

            AtomicReference<String> childQueueRef = new AtomicReference<>();
            System.out.println("UI-SMOKE step: select queue child + double-click");
            runFx(() -> {
                TreeItem<Object> queueItem = found.get().getChildren().stream()
                        .filter(item -> item.getValue() instanceof QueueInfo)
                        .findFirst().orElseThrow();
                tree.getSelectionModel().select(queueItem);
                childQueueRef.set(((QueueInfo) queueItem.getValue()).address() + " :: "
                        + ((QueueInfo) queueItem.getValue()).name());
            });
            doubleClickQueue(tree);
            await("browse completed from double-click", () -> {
                try {
                    Label status = field(controller, "statusLabel");
                    String text = runFx((Callable<String>) status::getText);
                    return text.contains("messages on");
                } catch (Exception e) {
                    return false;
                }
            });
            Label status = field(controller, "statusLabel");
            String text = runFx((Callable<String>) status::getText);
            assertFalse(text.contains("Select a queue"), "queue double-click must browse: " + text);
            String labelAfterDoubleClick = runFx((Callable<String>) () -> {
                Label label = field(controller, "browseQueueLabel");
                return label.getText();
            });
            assertEquals(childQueueRef.get(), labelAfterDoubleClick,
                    "browse label must track the double-clicked queue");

            runFx(controller::shutdown);
        } finally {
            if (fxRunning.get()) {
                runFx(() -> {
                    try {
                        BrokerClient client = field(controller, "client");
                        if (client != null) {
                            client.close();
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    @Order(4)
    void refreshPreservesExpansionQueuesAndSelection() throws Exception {
        ensureFx();
        MainController controller = loadController();
        try {
            TreeView<Object> tree = field(controller, "tree");
            await("addresses before refresh", () -> {
                try {
                    return runFx(() -> {
                        TreeItem<Object> root = tree.getRoot();
                        return root != null && !root.getChildren().isEmpty();
                    });
                } catch (Exception e) {
                    return false;
                }
            });

            AtomicReference<TreeItem<Object>> testItem = new AtomicReference<>();
            await("TEST address before refresh", () -> {
                try {
                    testItem.set(runFx((Callable<TreeItem<Object>>) () -> {
                        TreeItem<Object> root = tree.getRoot();
                        if (root == null) {
                            return null;
                        }
                        for (TreeItem<Object> child : root.getChildren()) {
                            if (child.getValue() instanceof AddressInfo info && "TEST".equals(info.name())) {
                                return child;
                            }
                        }
                        return null;
                    }));
                    return testItem.get() != null;
                } catch (Exception e) {
                    return false;
                }
            });

            System.out.println("UI-SMOKE step: expand + select queue before refresh");
            runFx(() -> {
                testItem.get().setExpanded(true);
                tree.getSelectionModel().select(testItem.get());
            });
            await("queues before refresh", () -> {
                try {
                    return runFx(() -> testItem.get().getChildren().stream()
                            .anyMatch(item -> item.getValue() instanceof QueueInfo));
                } catch (Exception e) {
                    return false;
                }
            });
            runFx(() -> {
                TreeItem<Object> queueItem = testItem.get().getChildren().stream()
                        .filter(item -> item.getValue() instanceof QueueInfo)
                        .findFirst().orElseThrow();
                tree.getSelectionModel().select(queueItem);
            });

            System.out.println("UI-SMOKE step: refresh");
            runFx(() -> invoke(controller, "onRefreshAddresses"));

            AtomicReference<TreeItem<Object>> refreshedItem = new AtomicReference<>();
            await("TEST address still expanded after refresh", () -> {
                try {
                    refreshedItem.set(runFx((Callable<TreeItem<Object>>) () -> {
                        TreeItem<Object> root = tree.getRoot();
                        if (root == null) {
                            return null;
                        }
                        for (TreeItem<Object> child : root.getChildren()) {
                            if (child.getValue() instanceof AddressInfo info
                                    && "TEST".equals(info.name()) && child.isExpanded()) {
                                return child;
                            }
                        }
                        return null;
                    }));
                    return refreshedItem.get() != null;
                } catch (Exception e) {
                    return false;
                }
            });
            await("queues reappear after refresh", () -> {
                try {
                    return runFx(() -> refreshedItem.get().getChildren().stream()
                            .anyMatch(item -> item.getValue() instanceof QueueInfo));
                } catch (Exception e) {
                    return false;
                }
            });
            await("queue selection restored after refresh", () -> {
                try {
                    return runFx((Callable<Boolean>) () -> {
                        TreeItem<Object> selected = tree.getSelectionModel().getSelectedItem();
                        return selected != null
                                && selected.getValue() instanceof QueueInfo
                                && "TEST".equals(((QueueInfo) selected.getValue()).address());
                    });
                } catch (Exception e) {
                    return false;
                }
            });

            runFx(controller::shutdown);
        } finally {
            if (fxRunning.get()) {
                runFx(() -> {
                    try {
                        BrokerClient client = field(controller, "client");
                        if (client != null) {
                            client.close();
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private static MainController loadController() throws Exception {
        return runFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(MainController.class.getResource("main-view.fxml"));
                Parent root = loader.load();
                MainController controller = loader.getController();
                BrokerClient client = BrokerClientFactory.create(profile());
                controller.init(profile(), client);

                Stage stage = new Stage();
                javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 900);
                var css = MainController.class.getResource("styles.css");
                if (css != null) {
                    scene.getStylesheets().add(css.toExternalForm());
                }
                stage.setScene(scene);
                stage.setTitle("Argus - ui-smoke");
                stage.show();
                capturedScene.set(scene);
                System.out.println("UI-SMOKE loaded ok; stage shown; bound to " + host + ":" + port);
                return controller;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static final java.util.concurrent.atomic.AtomicReference<javafx.scene.Scene>
            capturedScene = new java.util.concurrent.atomic.AtomicReference<>();

    private static boolean capture(Path target) throws Exception {
        try {
            javafx.scene.Scene scene = capturedScene.get();
            if (scene == null) {
                return false;
            }
            javafx.scene.image.WritableImage image =
                    runFx(() -> scene.getRoot().snapshot(null, null));
            int width = (int) Math.round(image.getWidth());
            int height = (int) Math.round(image.getHeight());
            BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            javafx.scene.image.PixelReader reader = image.getPixelReader();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    buffered.setRGB(x, y, reader.getArgb(x, y));
                }
            }
            ImageIO.write(buffered, "png", target.toFile());
            return true;
        } catch (Throwable t) {
            System.out.println("UI-SMOKE capture failed: " + t);
            return false;
        }
    }
}
