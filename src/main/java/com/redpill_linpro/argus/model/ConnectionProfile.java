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
        boolean persistPassword,
        String trustStorePath,
        String trustStorePassword,
        String keyStorePath,
        String keyStorePassword) {

    public ConnectionProfile(String name,
                             Protocol protocol,
                             String host,
                             int port,
                             String username,
                             String password,
                             boolean ssl,
                             boolean persistPassword) {
        this(name, protocol, host, port, username, password, ssl, persistPassword,
                null, null, null, null);
    }

    @JsonIgnore
    public String url() {
        return host + ":" + port;
    }

    @JsonIgnore
    public String displayUrl() {
        return (ssl ? "ssl://" : "tcp://") + url();
    }

    public ConnectionProfile withoutSecret() {
        return new ConnectionProfile(name, protocol, host, port, username, null, ssl, persistPassword,
                trustStorePath, null, keyStorePath, null);
    }

    public boolean hasTrustStore() {
        return ssl && trustStorePath != null && !trustStorePath.isBlank();
    }

    public boolean hasKeyStore() {
        return ssl && keyStorePath != null && !keyStorePath.isBlank();
    }
}
