package com.redpill_linpro.argus.broker;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.advisory.DestinationSource;

import com.redpill_linpro.argus.model.AddressInfo;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.DestinationType;
import com.redpill_linpro.argus.model.MessageDraft;
import com.redpill_linpro.argus.model.MessageSnapshot;
import com.redpill_linpro.argus.model.QueueInfo;
import com.redpill_linpro.argus.util.JmsMessageReader;
import com.redpill_linpro.argus.util.MessageDraftFactory;

import jakarta.jms.ConnectionMetaData;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.QueueBrowser;
import jakarta.jms.QueueConnection;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;

public final class OpenWireBrokerClient implements BrokerClient {

    private static final long ADVISORY_WAIT_MS = 3_000;

    private final ConnectionProfile profile;
    private final AtomicBoolean open = new AtomicBoolean(false);

    private volatile ActiveMQConnection connection;
    private volatile Set<String> knownQueues = Set.of();
    private volatile Set<String> knownTopics = Set.of();
    private volatile boolean advisoryError;

    public OpenWireBrokerClient(ConnectionProfile profile) {
        this.profile = profile;
        String url = openWireUrl();
        try {
            org.apache.activemq.ActiveMQConnectionFactory cf =
                    new org.apache.activemq.ActiveMQConnectionFactory(url);
            cf.setTrustAllPackages(false);
            this.connection = (ActiveMQConnection) cf.createConnection(
                    blankToNull(profile.username()), blankToNull(profile.password()));
            this.connection.start();
            open.set(true);
        } catch (Exception e) {
            closeQuietly();
            throw new BrokerException("Cannot connect to " + url + ": " + rootMessage(e), e);
        }
    }

    private String openWireUrl() {
        return (profile.ssl() ? "ssl://" : "tcp://") + profile.url();
    }

    @Override
    public String brokerInfo() {
        try {
            ConnectionMetaData meta = connection.getMetaData();
            return "OpenWire " + meta.getProviderVersion();
        } catch (Exception e) {
            return "OpenWire";
        }
    }

    @Override
    public List<AddressInfo> listAddresses() {
        List<AddressInfo> addresses = new ArrayList<>();
        try {
            DestinationSource destinationSource = connection.getDestinationSource();
            destinationSource.start();
            long deadline = System.currentTimeMillis() + ADVISORY_WAIT_MS;
            while (System.currentTimeMillis() < deadline) {
                if (!destinationSource.getQueues().isEmpty() || !destinationSource.getTopics().isEmpty()) {
                    break;
                }
                Thread.sleep(200);
            }
            Set<String> queuesCapture = new HashSet<>();
            for (ActiveMQQueue queue : destinationSource.getQueues()) {
                String name = queue.getQueueName();
                queuesCapture.add(name);
                addresses.add(new AddressInfo(name, List.of("ANYCAST")));
            }
            Set<String> topicsCapture = new HashSet<>();
            for (ActiveMQTopic topic : destinationSource.getTopics()) {
                String name = topic.getTopicName();
                topicsCapture.add(name);
                addresses.add(new AddressInfo(name, List.of("MULTICAST")));
            }
            knownQueues = queuesCapture;
            knownTopics = topicsCapture;
        } catch (Exception e) {
            advisoryError = true;
        }
        addresses.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return addresses;
    }

    @Override
    public List<QueueInfo> listQueues(String address) {
        List<QueueInfo> result = new ArrayList<>();
        if (knownQueues.contains(address)) {
            result.add(new QueueInfo(address, address, "ANYCAST", -1, -1, true));
        } else if (knownTopics.contains(address)) {
            result.add(new QueueInfo(address, address, "MULTICAST", -1, -1, false));
        }
        return result;
    }

    public boolean isAdvisoryError() {
        return advisoryError;
    }

    @Override
    public java.util.Optional<DestinationType> resolveDestinationType(String destination) {
        if (knownQueues.contains(destination)) {
            return java.util.Optional.of(DestinationType.QUEUE);
        }
        if (knownTopics.contains(destination)) {
            return java.util.Optional.of(DestinationType.TOPIC);
        }
        return java.util.Optional.empty();
    }

    @Override
    public List<MessageSnapshot> browse(String address, String queueName, Integer maxMessages, String selector) {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            jakarta.jms.Queue queue = new ActiveMQQueue(queueName);
            QueueBrowser browser = (selector == null || selector.isBlank())
                    ? session.createBrowser(queue)
                    : session.createBrowser(queue, selector);
            List<MessageSnapshot> messages = new ArrayList<>();
            Enumeration<?> enumeration = browser.getEnumeration();
            while (enumeration.hasMoreElements()) {
                messages.add(JmsMessageReader.read((Message) enumeration.nextElement()));
                if (maxMessages != null && messages.size() >= maxMessages) {
                    break;
                }
            }
            browser.close();
            return messages;
        } catch (JMSException e) {
            throw new BrokerException("Browse failed for queue '" + queueName + "': " + rootMessage(e), e);
        }
    }

    @Override
    public void send(String destination, DestinationType destinationType, MessageDraft draft) {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            jakarta.jms.Destination target = destinationType == DestinationType.TOPIC
                    ? new ActiveMQTopic(destination)
                    : new ActiveMQQueue(destination);
            try (MessageProducer producer = session.createProducer(target)) {
                producer.send(MessageDraftFactory.create(session, draft));
            }
        } catch (JMSException e) {
            throw new BrokerException("Send to '" + destination + "' failed: " + rootMessage(e), e);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String rootMessage(Throwable t) {
        Throwable cursor = t;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private void closeQuietly() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
        open.set(false);
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void close() {
        closeQuietly();
    }
}
