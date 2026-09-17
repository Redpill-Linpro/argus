package com.redpill_linpro.argus.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import org.apache.activemq.artemis.core.server.ActiveMQServer;

import com.redpill_linpro.argus.broker.BrokerException;
import com.redpill_linpro.argus.broker.CoreBrokerClient;
import com.redpill_linpro.argus.broker.Subscription;
import com.redpill_linpro.argus.model.AddressInfo;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.MessageDraft;
import com.redpill_linpro.argus.model.MessageSnapshot;
import com.redpill_linpro.argus.model.Protocol;
import com.redpill_linpro.argus.model.QueueInfo;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CoreBrokerClientIT {

    static ActiveMQServer server;
    static int port;

    @BeforeAll
    static void startServer() throws Exception {
        port = EmbeddedArtemisSupport.freePort();
        server = EmbeddedArtemisSupport.start(port, false);
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.stop();
    }

    private static ConnectionProfile profile(String user, String password) {
        return new ConnectionProfile("it-core", Protocol.CORE, "127.0.0.1", port, user, password, false, false);
    }

    @Test
    @Order(1)
    void listsPreparedAddressAndQueue() {
        try (CoreBrokerClient client = new CoreBrokerClient(profile("argus", "arguspw"))) {
            List<AddressInfo> addresses = client.listAddresses();
            List<String> names = addresses.stream().map(AddressInfo::name).toList();
            assertTrue(names.contains("TEST"), "expected TEST in " + names);
            assertTrue(names.stream().anyMatch(n -> n.startsWith("argus-reply-")),
                    "expected temporary management reply queue address in " + names);

            List<QueueInfo> queues = client.listQueues("TEST");
            assertEquals(1, queues.size());
            QueueInfo queue = queues.get(0);
            assertEquals("TESTQ", queue.name());
            assertEquals("TEST", queue.address());
            assertEquals("ANYCAST", queue.routingType());
            assertEquals(0, queue.messageCount());
            assertEquals(0, queue.consumerCount());
            assertTrue(queue.durable() == false);
        }
    }

    @Test
    @Order(2)
    void browsesSentMessagesWithPropertiesAndBody() {
        try (CoreBrokerClient client = new CoreBrokerClient(profile("argus", "arguspw"))) {
            client.send("TEST::TESTQ", com.redpill_linpro.argus.model.DestinationType.QUEUE,
                    new MessageDraft(MessageDraft.Kind.TEXT, "m1", Map.of("ord", "1")));
            client.send("TEST::TESTQ", com.redpill_linpro.argus.model.DestinationType.QUEUE,
                    new MessageDraft(MessageDraft.Kind.TEXT, "m2", Map.of("ord", "2")));

            List<MessageSnapshot> messages = client.browse("TEST", "TESTQ", 10, null);
            assertEquals(2, messages.size());
            MessageSnapshot first = messages.get(0);
            assertEquals("m1", first.body());
            assertEquals("text", first.type());
            assertEquals("1", String.valueOf(first.properties().get("ord")));
            assertNotNull(first.messageId());
        }
    }

    @Test
    @Order(3)
    void appliesSelectorAndMax() {
        try (CoreBrokerClient client = new CoreBrokerClient(profile("argus", "arguspw"))) {
            client.send("TEST::TESTQ", com.redpill_linpro.argus.model.DestinationType.QUEUE,
                    new MessageDraft(MessageDraft.Kind.TEXT, "s1", Map.of("prio", "1")));
            client.send("TEST::TESTQ", com.redpill_linpro.argus.model.DestinationType.QUEUE,
                    new MessageDraft(MessageDraft.Kind.BYTES_UTF8, "s2", Map.of("prio", "2")));
            client.send("TEST::TESTQ", com.redpill_linpro.argus.model.DestinationType.QUEUE,
                    new MessageDraft(MessageDraft.Kind.TEXT, "s3", Map.of("prio", "3")));

            List<MessageSnapshot> selected = client.browse("TEST", "TESTQ", 10, "prio = 2");
            assertEquals(1, selected.size());
            assertEquals("bytes", selected.get(0).type());
            assertTrue(selected.get(0).body().endsWith("s2"));

            List<MessageSnapshot> limited = client.browse("TEST", "TESTQ", 1, null);
            assertEquals(1, limited.size());
        }
    }

    @Test
    @Order(4)
    void browseDeniedWithoutBrowsePermission() {
        try (CoreBrokerClient restricted = new CoreBrokerClient(profile("restricted", "restrictedpw"))) {
            restricted.send("TEST2::TEST2", com.redpill_linpro.argus.model.DestinationType.QUEUE,
                    new MessageDraft(MessageDraft.Kind.TEXT, "allowed", Map.of()));
            try {
                restricted.browse("TEST2", "TEST2", 10, null);
                fail("expected browse to be denied");
            } catch (BrokerException e) {
                assertTrue(e.isAccessDenied(), "expected access denied, got: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(5)
    void listingDeniedWithoutManagePermission() {
        try (CoreBrokerClient restricted = new CoreBrokerClient(profile("restricted", "restrictedpw"))) {
            try {
                restricted.listAddresses();
                fail("expected listing to be denied");
            } catch (BrokerException e) {
                assertTrue(e.isAccessDenied(), "expected access denied, got: " + e.getMessage());
            }
        }
        assertFalse(serverStateBroken());
    }

    @Test
    @Order(6)
    void subscribesToMulticastAddressAndReceivesLiveMessages() throws Exception {
        try (CoreBrokerClient client = new CoreBrokerClient(profile("argus", "arguspw"))) {
            BlockingQueue<MessageSnapshot> received = new LinkedBlockingQueue<>();
            Subscription sub = client.subscribe("TESTM", null, received::add);
            try {
                client.send("TESTM", com.redpill_linpro.argus.model.DestinationType.TOPIC,
                        new MessageDraft(MessageDraft.Kind.TEXT, "live-1", Map.of("ord", "1")));
                client.send("TESTM", com.redpill_linpro.argus.model.DestinationType.TOPIC,
                        new MessageDraft(MessageDraft.Kind.TEXT, "live-2", Map.of("ord", "2")));
                MessageSnapshot first = received.poll(10, TimeUnit.SECONDS);
                assertNotNull(first, "expected a live topic message");
                assertEquals("live-1", first.body());
                MessageSnapshot second = received.poll(10, TimeUnit.SECONDS);
                assertNotNull(second, "expected the second live topic message");
                assertEquals("live-2", second.body());
            } finally {
                sub.close();
            }
        }
        try (CoreBrokerClient client = new CoreBrokerClient(profile("argus", "arguspw"))) {
            List<QueueInfo> queues = client.listQueues("TESTM");
            long deadline = System.currentTimeMillis() + 10_000;
            while (!queues.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
                queues = client.listQueues("TESTM");
            }
            assertTrue(queues.isEmpty(), "non-durable subscription must not leave queues behind: " + queues);
        }
    }

    @Test
    @Order(7)
    void subscribesWithSelector() throws Exception {
        try (CoreBrokerClient client = new CoreBrokerClient(profile("argus", "arguspw"))) {
            BlockingQueue<MessageSnapshot> received = new LinkedBlockingQueue<>();
            Subscription sub = client.subscribe("TESTM", "ord = 'keep'", received::add);
            try {
                client.send("TESTM", com.redpill_linpro.argus.model.DestinationType.TOPIC,
                        new MessageDraft(MessageDraft.Kind.TEXT, "dropped", Map.of("ord", "drop")));
                client.send("TESTM", com.redpill_linpro.argus.model.DestinationType.TOPIC,
                        new MessageDraft(MessageDraft.Kind.TEXT, "kept", Map.of("ord", "keep")));
                MessageSnapshot only = received.poll(10, TimeUnit.SECONDS);
                assertNotNull(only, "expected the message matching the selector");
                assertEquals("kept", only.body());
                assertNull(received.poll(1, TimeUnit.SECONDS), "selector-filtered messages must not arrive");
            } finally {
                sub.close();
            }
        }
    }

    @Test
    @Order(8)
    void repeatedListingReusesSingleReplyQueue() throws Exception {
        try (CoreBrokerClient first = new CoreBrokerClient(profile("argus", "arguspw"))) {
            first.listAddresses();
            first.listAddresses();
            try (CoreBrokerClient second = new CoreBrokerClient(profile("argus", "arguspw"))) {
                List<AddressInfo> listed = second.listAddresses();
                long replyQueues = listed.stream()
                        .map(AddressInfo::name)
                        .filter(name -> name.startsWith("argus-reply-"))
                        .count();
                assertEquals(2, replyQueues,
                        "each connection must hold exactly one reply queue: " + listed);
            }
        }
        try (CoreBrokerClient probe = new CoreBrokerClient(profile("argus", "arguspw"))) {
            List<AddressInfo> listed = probe.listAddresses();
            long replyQueues = listed.stream()
                    .map(AddressInfo::name)
                    .filter(name -> name.startsWith("argus-reply-"))
                    .count();
            assertTrue(replyQueues <= 1,
                    "closed connections must not leave reply queues behind (probe's own reply queue is at most one): "
                            + listed);
        }
    }

    private boolean serverStateBroken() {
        try {
            server.getState();
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
