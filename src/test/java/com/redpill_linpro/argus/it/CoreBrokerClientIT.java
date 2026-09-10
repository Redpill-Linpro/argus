package com.redpill_linpro.argus.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import org.apache.activemq.artemis.core.server.ActiveMQServer;

import com.redpill_linpro.argus.broker.BrokerException;
import com.redpill_linpro.argus.broker.CoreBrokerClient;
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
            assertTrue(names.stream().anyMatch(n -> n.startsWith("activemq.management")),
                    "expected management infrastructure address in " + names);

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

    private boolean serverStateBroken() {
        try {
            server.getState();
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
