package com.redpill_linpro.argus.model;

import java.util.Map;

public record MessageSnapshot(
        String messageId,
        String correlationId,
        long timestamp,
        long expiration,
        int priority,
        int deliveryCount,
        boolean redelivered,
        String type,
        Map<String, Object> properties,
        String body) {
}
