package com.redpill_linpro.argus.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ConnectionProfile(
        String name,
        Protocol protocol,
        String host,
        int port,
        String username,
        String password,
        boolean ssl,
        boolean persistPassword) {

    @JsonIgnore
    public String url() {
        return host + ":" + port;
    }

    @JsonIgnore
    public String displayUrl() {
        return (ssl ? "ssl://" : "tcp://") + url();
    }

    public ConnectionProfile withoutSecret() {
        return new ConnectionProfile(name, protocol, host, port, username, null, ssl, persistPassword);
    }
}
