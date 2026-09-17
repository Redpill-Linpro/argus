package com.redpill_linpro.argus.broker;

/**
 * Handle of a live subscription. Closing it stops message delivery and releases
 * the underlying session; implementations are idempotent and safe to call more
 * than once. The subscription is non-durable: the broker-side queue exists only
 * while the subscription is open and disappears on close.
 */
public interface Subscription extends AutoCloseable {

    /** The multicast address this subscription listens on. */
    String address();

    /**
     * Stops the subscription. Runs broker I/O and must not be called on the FX
     * application thread.
     */
    @Override
    void close();
}
