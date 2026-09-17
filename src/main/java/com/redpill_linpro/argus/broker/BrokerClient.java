package com.redpill_linpro.argus.broker;

import com.redpill_linpro.argus.model.AddressInfo;
import com.redpill_linpro.argus.model.MessageDraft;
import com.redpill_linpro.argus.model.MessageSnapshot;
import com.redpill_linpro.argus.model.QueueInfo;
import com.redpill_linpro.argus.model.DestinationType;

import java.util.List;

/**
 * Protocol-native broker access. Implementations must not use JMX.
 * All methods throw {@link BrokerException} on failure.
 */
public interface BrokerClient extends AutoCloseable {

    /** Short broker description, e.g. version string. */
    String brokerInfo();

    List<AddressInfo> listAddresses();

    /** Queues bound to the given address. */
    List<QueueInfo> listQueues(String address);

    /**
     * Current message count of a queue, or a negative number when it cannot be
     * determined (e.g. OpenWire mode). Used by the browse auto-refresh.
     */
    long messageCount(String address, String queueName);

    /**
     * Browse up to {@code maxMessages} messages (null = unlimited) using an optional
     * JMS selector (null/blank = none). Purely read-only.
     */
    List<MessageSnapshot> browse(String address, String queueName, Integer maxMessages, String selector);

    void send(String destination, DestinationType destinationType, MessageDraft draft);

    /**
     * Resolves the routing type of an existing destination: QUEUE for an
     * anycast-only address, TOPIC for a multicast-only address. Empty if the
     * destination is unknown (e.g. not created yet) or ambiguous.
     */
    java.util.Optional<DestinationType> resolveDestinationType(String destination);

    /**
     * Starts a live, non-durable subscription on the (multicast) address and
     * delivers every received message to {@code listener}, which runs on a
     * broker delivery thread and must marshal UI updates itself. Null/blank
     * selector means no filtering. {@code createNonDurableQueue} and
     * {@code consume} permissions are required on the address.
     */
    Subscription subscribe(String address, String selector,
            java.util.function.Consumer<com.redpill_linpro.argus.model.MessageSnapshot> listener);

    boolean isOpen();

    @Override
    void close();
}
