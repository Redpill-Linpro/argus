package com.redpill_linpro.argus.model;

public enum Protocol {
    CORE("Artemis Core"),
    OPENWIRE("OpenWire");

    private final String displayName;

    Protocol(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
