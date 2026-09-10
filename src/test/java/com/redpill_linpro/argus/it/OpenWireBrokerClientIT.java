package com.redpill_linpro.argus.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.activemq.artemis.core.server.ActiveMQServer;

import com.redpill_linpro.argus.broker.OpenWireBrokerClient;
import com.redpill_linpro.argus.model.AddressInfo;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.DestinationType;
import com.redpill_linpro.argus.model.MessageDraft;
import com.redpill_linpro.argus.model.MessageSnapshot;
import com.redpill_linpro.argus.model.Protocol;
import com.redpill_linpro.argus.model.QueueInfo;

class OpenWireBrokerClientIT {

    static ActiveMQServer server;
    static int port;

    @BeforeAll
    static void startServer() throws Exception {
        port = EmbeddedArtemisSupport.freePort();
        server = EmbeddedArtemisSupport.start(port, true);
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.stop();
    }

    private static ConnectionProfile profile() {
        return new ConnectionProfile("it-openwire", Protocol.OPENWIRE, "127.0.0.1", port,
                "argus", "arguspw", false, false);
    }

    @Test
    void sendsAndBrowsesMessages() {
        try (OpenWireBrokerClient client = new OpenWireBrokerClient(profile())) {
            for (int i = 1; i <= 3; i++) {
                client.send("OW.Q1", DestinationType.QUEUE,
                        new MessageDraft(MessageDraft.Kind.TEXT, "ow-" + i, java.util.Map.of("n", String.valueOf(i))));
            }
            List<MessageSnapshot> selected = client.browse("OW.Q1", "OW.Q1", 10, "n = 2");
            assertEquals(1, selected.size(), "selector should filter to 1");
            assertEquals("ow-2", selected.get(0).body());

            List<MessageSnapshot> alll = client.browse("OW.Q1", "OW.Q1", 10, null);
            assertEquals(3, alll.size());

            List<QueueInfo> queues = client.listQueues("OW.Q1");
            if (!queues.isEmpty()) {
                assertEquals("ANYCAST", queues.get(0).routingType());
                assertEquals(-1, queues.get(0).messageCount());
            }
        }
    }

    @Test
    void listsDestinationsViaAdvisories() throws Exception {
        try (OpenWireBrokerClient client = new OpenWireBrokerClient(profile())) {
            assertTrue(client.listAddresses().isEmpty(),
                    "no destinations used yet; advisories list should be empty (may also fail if broker is misconfigured)");
            client.send("OW.Q2", DestinationType.QUEUE,
                    new MessageDraft(MessageDraft.Kind.TEXT, "listed", java.util.Map.of()));

            long deadline = System.currentTimeMillis() + 15_000;
            List<AddressInfo> listed = client.listAddresses();
            while (System.currentTimeMillis() < deadline && listed.stream().noneMatch(a -> a.name().equals("OW.Q2"))) {
                Thread.sleep(250);
                listed = client.listAddresses();
            }
            assertTrue(listed.stream().anyMatch(a -> a.name().equals("OW.Q2")),
                    "OW.Q2 should appear via destination advisory: " + listed);

            List<QueueInfo> queues = client.listQueues("OW.Q2");
            assertEquals(1, queues.size());
            assertEquals("ANYCAST", queues.get(0).routingType());
            assertEquals(-1, queues.get(0).messageCount());
        }
    }
}
