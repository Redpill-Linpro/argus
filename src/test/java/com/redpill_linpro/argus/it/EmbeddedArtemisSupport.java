package com.redpill_linpro.argus.it;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;

public final class EmbeddedArtemisSupport {

    private EmbeddedArtemisSupport() {
    }

    public static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public static ActiveMQServer start(int port, boolean openwire) throws Exception {
        Path securityDir = Files.createTempDirectory("argus-it-security");
        writeJaasFiles(securityDir);
        System.setProperty("java.security.auth.login.config",
                securityDir.resolve("login.config").toAbsolutePath().toString());

        Path dataDir = Files.createTempDirectory("argus-it-data");
        ConfigurationImpl config = new ConfigurationImpl();
        config.setPersistenceEnabled(false);
        config.setSecurityEnabled(true);
        config.setJMXManagementEnabled(false);
        config.setBindingsDirectory(dataDir.resolve("bindings").toString());
        config.setJournalDirectory(dataDir.resolve("journal").toString());
        config.setPagingDirectory(dataDir.resolve("paging").toString());
        config.setLargeMessagesDirectory(dataDir.resolve("largemessages").toString());
        config.setJournalType(JournalType.NIO);
        if (openwire) {
            config.addAcceptorConfiguration("ow",
                    "tcp://127.0.0.1:" + port + "?protocols=OPENWIRE;supportAdvisory=true");
        } else {
            config.addAcceptorConfiguration("core", "tcp://127.0.0.1:" + port);
        }

        config.putSecurityRoles("activemq.management", java.util.Set.of(full("argus-role")));
        config.putSecurityRoles("activemq.management.#", java.util.Set.of(full("argus-role")));
        config.putSecurityRoles("TEST", java.util.Set.of(full("argus-role")));
        config.putSecurityRoles("TEST2", java.util.Set.of(full("argus-role"), sendOnly("restricted-role")));
        config.putSecurityRoles("OW.#", java.util.Set.of(full("argus-role")));
        config.putSecurityRoles("ActiveMQ.Advisory.#", java.util.Set.of(full("argus-role")));

        AddressSettings noAutoCreate = new AddressSettings();
        noAutoCreate.setAutoCreateAddresses(false);
        noAutoCreate.setAutoCreateQueues(false);
        config.addAddressSetting("#", noAutoCreate);

        AddressSettings openWireAutoCreate = new AddressSettings();
        openWireAutoCreate.setAutoCreateAddresses(true);
        openWireAutoCreate.setAutoCreateQueues(true);
        config.addAddressSetting("OW.#", openWireAutoCreate);

        AddressSettings advisoryAutoCreate = new AddressSettings();
        advisoryAutoCreate.setAutoCreateAddresses(true);
        advisoryAutoCreate.setAutoCreateQueues(true);
        config.addAddressSetting("ActiveMQ.Advisory.#", advisoryAutoCreate);

        ActiveMQServer server = new ActiveMQServerImpl(config, new ActiveMQJAASSecurityManager("argus"));
        server.start();
        server.addAddressInfo(new org.apache.activemq.artemis.core.server.impl.AddressInfo(
                org.apache.activemq.artemis.api.core.SimpleString.of("TEST"),
                org.apache.activemq.artemis.api.core.RoutingType.ANYCAST));
        server.addAddressInfo(new org.apache.activemq.artemis.core.server.impl.AddressInfo(
                org.apache.activemq.artemis.api.core.SimpleString.of("TEST2"),
                org.apache.activemq.artemis.api.core.RoutingType.ANYCAST));
        server.createQueue(new org.apache.activemq.artemis.api.core.QueueConfiguration("TESTQ")
                .setAddress("TEST").setDurable(false)
                .setRoutingType(org.apache.activemq.artemis.api.core.RoutingType.ANYCAST));
        server.createQueue(new org.apache.activemq.artemis.api.core.QueueConfiguration("TEST2")
                .setAddress("TEST2").setDurable(false)
                .setRoutingType(org.apache.activemq.artemis.api.core.RoutingType.ANYCAST));
        return server;
    }

    public static void writeJaasFiles(Path dir) throws Exception {
        Path usersFile = dir.resolve("users.properties");
        Path rolesFile = dir.resolve("roles.properties");
        Files.writeString(usersFile, "argus=arguspw\nrestricted=restrictedpw\n");
        Files.writeString(rolesFile, "argus-role=argus\nrestricted-role=restricted\n");
        Path loginFile = dir.resolve("login.config");
        Files.writeString(loginFile, """
                argus {
                    org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule required
                        debug=false
                        org.apache.activemq.jaas.properties.user="users.properties"
                        org.apache.activemq.jaas.properties.role="roles.properties";
                };
                """);
    }

    private static Role full(String name) {
        return new Role(name, true, true, true, true, true, true, true, true, true, true, true, true);
    }

    private static Role sendOnly(String name) {
        return new Role(name, true, false, false, false, false, false, false, false, false, false, false, false);
    }
}
