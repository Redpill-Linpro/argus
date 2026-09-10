package com.redpill_linpro.argus.model;

public record QueueInfo(
        String name,
        String address,
        String routingType,
        long messageCount,
        long consumerCount,
        boolean durable) {

    @Override
    public String toString() {
        return name + " [" + routingType + "] (" + messageCount + " msg)";
    }
}
