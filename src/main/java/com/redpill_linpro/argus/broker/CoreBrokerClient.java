package com.redpill_linpro.argus.broker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;

import com.redpill_linpro.argus.model.AddressInfo;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.DestinationType;
import com.redpill_linpro.argus.model.MessageDraft;
import com.redpill_linpro.argus.model.MessageSnapshot;
import com.redpill_linpro.argus.model.QueueInfo;
import com.redpill_linpro.argus.util.JmsMessageReader;
import com.redpill_linpro.argus.util.MessageDraftFactory;
import com.redpill_linpro.argus.util.TlsContextFactory;

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.Topic;

public final class CoreBrokerClient implements BrokerClient {

    private static final String MANAGEMENT_ADDRESS = "activemq.management";
    private static final long MANAGEMENT_TIMEOUT_MS = 15_000;

    private final ConnectionProfile profile;
    private final AtomicBoolean open = new AtomicBoolean(false);

    private final ServerLocator locator;
    private final ClientSessionFactory factory;
    private final ClientSession managementSession;
    private final jakarta.jms.Connection jmsConnection;
    private SimpleString replyQueueName;
    private ClientConsumer replyConsumer;
    private ClientProducer requestProducer;

    public CoreBrokerClient(ConnectionProfile profile) {
        this.profile = profile;
        String url = coreUrl();
        try {
            org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory jmsFactory =
                    new org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory(
                            url, blankToNull(profile.username()), blankToNull(profile.password()));
            this.jmsConnection = jmsFactory.createConnection();
            this.jmsConnection.start();
            this.locator = ActiveMQClient.createServerLocator(url);
            this.factory = locator.createSessionFactory();
            this.managementSession = factory.createSession(
                    profile.username(), profile.password(),
                    false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE);
            this.managementSession.start();
            open.set(true);
        } catch (Exception e) {
            closeResources();
            throw new BrokerException("Cannot connect to " + url + ": " + rootMessage(e), e);
        }
    }

    private String coreUrl() {
        String url = "tcp://" + profile.url();
        return profile.ssl() ? url + "?sslEnabled=true" + TlsContextFactory.coreUrlParams(profile) : url;
    }

    @Override
    public String brokerInfo() {
        try {
            Object version = attribute(ResourceNames.BROKER, "version");
            return version == null ? "ActiveMQ Artemis" : "ActiveMQ Artemis " + version;
        } catch (BrokerException e) {
            return "ActiveMQ Artemis";
        }
    }

    @Override
    public List<AddressInfo> listAddresses() {
        Object[] names = operation(ResourceNames.BROKER, "getAddressNames");
        List<AddressInfo> result = new ArrayList<>(names.length);
        for (Object name : names) {
            String address = String.valueOf(name);
            List<String> routingTypes = new ArrayList<>();
            try {
                Object routingTypesResult = attribute(ResourceNames.ADDRESS + address, "routingTypes");
                if (routingTypesResult instanceof Object[] array) {
                    for (Object rt : array) {
                        routingTypes.add(String.valueOf(rt));
                    }
                } else if (routingTypesResult != null) {
                    routingTypes.add(String.valueOf(routingTypesResult));
                }
            } catch (BrokerException ignored) {
            }
            result.add(new AddressInfo(address, routingTypes));
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    @Override
    public long messageCount(String address, String queueName) {
        return longOf(attributeOrNull(ResourceNames.QUEUE + queueName, "messageCount"));
    }

    @Override
    public List<QueueInfo> listQueues(String address) {
        List<QueueInfo> result = new ArrayList<>();
        Object queueNamesResult = attributeOrNull(ResourceNames.ADDRESS + address, "queueNames");
        Object[] queueNames = asArray(queueNamesResult);
        String queueResourcePrefix = ResourceNames.QUEUE;
        for (Object q : queueNames) {
            String queueName = String.valueOf(q);
            String resource = queueResourcePrefix + queueName;
            result.add(new QueueInfo(
                    queueName,
                    stringOf(attributeOrNull(resource, "address"), address),
                    stringOf(attributeOrNull(resource, "routingType"), "ANYCAST"),
                    longOf(attributeOrNull(resource, "messageCount")),
                    longOf(attributeOrNull(resource, "consumerCount")),
                    boolOf(attributeOrNull(resource, "durable"))));
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    @Override
    public List<MessageSnapshot> browse(String address, String queueName, Integer maxMessages, String selector) {
        try (Session session = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Queue queue = session.createQueue(fqqn(address, queueName));
            QueueBrowser browser = (selector == null || selector.isBlank())
                    ? session.createBrowser(queue)
                    : session.createBrowser(queue, selector);
            List<MessageSnapshot> messages = new ArrayList<>();
            var enumeration = browser.getEnumeration();
            while (enumeration.hasMoreElements()) {
                messages.add(JmsMessageReader.read((jakarta.jms.Message) enumeration.nextElement()));
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
        try (Session session = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            jakarta.jms.Destination target = destinationType == DestinationType.TOPIC
                    ? session.createTopic(destination)
                    : session.createQueue(destination);
            try (MessageProducer producer = session.createProducer(target)) {
                producer.send(MessageDraftFactory.create(session, draft));
            }
        } catch (JMSException e) {
            throw new BrokerException("Send to '" + destination + "' failed: " + rootMessage(e), e);
        }
    }

    @Override
    public java.util.Optional<DestinationType> resolveDestinationType(String destination) {
        try {
            String address = destination;
            boolean fqqn = destination.contains("::");
            if (fqqn) {
                address = destination.substring(0, destination.indexOf("::"));
            }
            Object result = attributeOrNull(ResourceNames.ADDRESS + address, "routingTypes");
            if (result == null) {
                return java.util.Optional.empty();
            }
            List<String> routingTypes = new ArrayList<>();
            for (Object rt : asArray(result)) {
                routingTypes.add(String.valueOf(rt));
            }
            boolean anycast = routingTypes.contains("ANYCAST");
            boolean multicast = routingTypes.contains("MULTICAST");
            if (fqqn && anycast) {
                return java.util.Optional.of(DestinationType.QUEUE);
            }
            if (anycast && !multicast) {
                return java.util.Optional.of(DestinationType.QUEUE);
            }
            if (multicast && !anycast) {
                return java.util.Optional.of(DestinationType.TOPIC);
            }
            return java.util.Optional.empty();
        } catch (BrokerException e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public Subscription subscribe(String address, String selector, java.util.function.Consumer<MessageSnapshot> listener) {
        Session session = null;
        try {
            session = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(address);
            String trimmed = blankToNull(selector);
            MessageConsumer consumer = trimmed == null
                    ? session.createConsumer(topic)
                    : session.createConsumer(topic, trimmed);
            consumer.setMessageListener(message -> {
                try {
                    listener.accept(JmsMessageReader.read(message));
                } catch (JMSException e) {
                    throw new BrokerException("Failed to read subscribed message: " + rootMessage(e), e);
                }
            });
            CoreSubscriptionImpl subscription = new CoreSubscriptionImpl(address, consumer, session);
            session = null;
            return subscription;
        } catch (JMSException e) {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
            throw new BrokerException("Subscribe to '" + address + "' failed: " + rootMessage(e), e);
        }
    }

    private static final class CoreSubscriptionImpl implements Subscription {

        private final String address;
        private final MessageConsumer consumer;
        private final Session session;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private CoreSubscriptionImpl(String address, MessageConsumer consumer, Session session) {
            this.address = address;
            this.consumer = consumer;
            this.session = session;
        }

        @Override
        public String address() {
            return address;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    consumer.close();
                } catch (Exception ignored) {
                }
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String fqqn(String address, String queueName) {
        if (address != null && !address.isBlank() && !address.equals(queueName)) {
            return address + "::" + queueName;
        }
        return queueName;
    }

    private Object[] operation(String resource, String operation, Object... parameters) {
        Object result = request(resource, () -> {
            ClientMessage message = managementSession.createMessage(false);
            ManagementHelper.putOperationInvocation(message, resource, operation, parameters);
            return message;
        });
        return asArray(result);
    }

    private Object attribute(String resource, String attributeName) {
        Object value = attributeOrNull(resource, attributeName);
        if (value == null) {
            throw new BrokerException("Attribute '" + attributeName + "' is not available on '" + resource + "'");
        }
        return value;
    }

    private Object attributeOrNull(String resource, String attributeName) {
        return request(resource, () -> {
            ClientMessage message = managementSession.createMessage(false);
            ManagementHelper.putAttribute(message, resource, attributeName);
            return message;
        });
    }

    private interface RequestSetup {
        ClientMessage message() throws Exception;
    }

    private Object request(String resource, RequestSetup setup) {
        try {
            ensureReplyInfrastructure();
            ClientMessage request = setup.message();
            request.putStringProperty(ClientMessageImpl.REPLYTO_HEADER_NAME, replyQueueName);
            requestProducer.send(request);
            ClientMessage reply = replyConsumer.receive(MANAGEMENT_TIMEOUT_MS);
            if (reply == null) {
                throw new BrokerException("Management request on '" + resource + "' timed out");
            }
            if (!ManagementHelper.hasOperationSucceeded(reply)) {
                Object failure = ManagementHelper.getResult(reply);
                String failureText = failure == null ? "management request failed" : String.valueOf(failure);
                boolean denied = failureText.contains("SecurityException")
                        || failureText.contains("is not authorized")
                        || failureText.contains("does not have permission")
                        || failureText.contains("AMQ229212");
                throw new BrokerException(failureText, new BrokerException(failureText, denied, null));
            }
            return ManagementHelper.getResult(reply);
        } catch (BrokerException e) {
            throw e;
        } catch (Exception e) {
            releaseReplyInfrastructure();
            throw new BrokerException("Management request on '" + resource + "' failed: " + rootMessage(e), e);
        }
    }

    private void ensureReplyInfrastructure() throws Exception {
        if (replyConsumer != null) {
            return;
        }
        replyQueueName = SimpleString.of("argus-reply-" + UUID.randomUUID());
        managementSession.createQueue(QueueConfiguration.of(replyQueueName).setDurable(false).setTemporary(true));
        replyConsumer = managementSession.createConsumer(replyQueueName);
        requestProducer = managementSession.createProducer(MANAGEMENT_ADDRESS);
    }

    private void releaseReplyInfrastructure() {
        try {
            if (replyConsumer != null) {
                replyConsumer.close();
            }
            if (requestProducer != null) {
                requestProducer.close();
            }
            if (replyQueueName != null) {
                managementSession.deleteQueue(replyQueueName);
            }
        } catch (Exception ignored) {
        }
        replyConsumer = null;
        requestProducer = null;
        replyQueueName = null;
    }

    private static Object[] asArray(Object result) {
        if (result instanceof Object[] array) {
            return array;
        }
        if (result == null) {
            return new Object[0];
        }
        return new Object[] {result};
    }

    private static String stringOf(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static boolean boolOf(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
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

    private void closeResources() {
        releaseReplyInfrastructure();
        try {
            jmsConnection.close();
        } catch (Exception ignored) {
        }
        try {
            managementSession.close();
        } catch (Exception ignored) {
        }
        try {
            factory.close();
        } catch (Exception ignored) {
        }
        try {
            locator.close();
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
        closeResources();
    }
}
