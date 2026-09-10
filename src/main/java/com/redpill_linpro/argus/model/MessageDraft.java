package com.redpill_linpro.argus.model;

import java.util.Map;

public record MessageDraft(Kind kind, String body, Map<String, String> properties) {

    public enum Kind {
        TEXT,
        BYTES_UTF8
    }
}
