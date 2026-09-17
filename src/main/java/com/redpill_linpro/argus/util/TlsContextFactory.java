package com.redpill_linpro.argus.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import com.redpill_linpro.argus.model.ConnectionProfile;

public final class TlsContextFactory {

    private TlsContextFactory() {
    }

    public static SSLContext create(ConnectionProfile profile) throws GeneralSecurityException, IOException {
        Managers managers = managers(profile);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.keyManagers(), managers.trustManagers(), null);
        return context;
    }

    public static org.apache.activemq.broker.SslContext openWireContext(ConnectionProfile profile)
            throws GeneralSecurityException, IOException {
        Managers managers = managers(profile);
        return new org.apache.activemq.broker.SslContext(
                managers.keyManagers(), managers.trustManagers(), null);
    }

    public static String coreUrlParams(ConnectionProfile profile) {
        StringBuilder params = new StringBuilder();
        if (profile.hasTrustStore()) {
            params.append("&trustStorePath=").append(urlEncode(profile.trustStorePath()));
        }
        if (hasText(profile.trustStorePassword())) {
            params.append("&trustStorePassword=").append(urlEncode(profile.trustStorePassword()));
        }
        if (profile.hasKeyStore()) {
            params.append("&keyStorePath=").append(urlEncode(profile.keyStorePath()));
        }
        if (hasText(profile.keyStorePassword())) {
            params.append("&keyStorePassword=").append(urlEncode(profile.keyStorePassword()));
        }
        return params.toString();
    }

    private static Managers managers(ConnectionProfile profile)
            throws GeneralSecurityException, IOException {
        KeyManager[] keyManagers = null;
        if (profile.hasKeyStore()) {
            KeyStore keyStore = loadStore(profile.keyStorePath(), profile.keyStorePassword());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, storePassword(profile.keyStorePassword()));
            keyManagers = kmf.getKeyManagers();
        }
        TrustManager[] trustManagers = null;
        if (profile.hasTrustStore()) {
            KeyStore trustStore = loadStore(profile.trustStorePath(), profile.trustStorePassword());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        }
        return new Managers(keyManagers, trustManagers);
    }

    private record Managers(KeyManager[] keyManagers, TrustManager[] trustManagers) {
    }

    private static KeyStore loadStore(String path, String password)
            throws IOException, GeneralSecurityException {
        Path file = Path.of(path.trim());
        try {
            return load(file, KeyStore.getInstance("PKCS12"), password);
        } catch (IOException e) {
            return load(file, KeyStore.getInstance("JKS"), password);
        }
    }

    private static KeyStore load(Path file, KeyStore store, String password)
            throws IOException, GeneralSecurityException {
        try (InputStream in = Files.newInputStream(file)) {
            store.load(in, storePassword(password));
        } catch (IOException e) {
            throw new IOException("Cannot load " + file + " (" + store.getType() + "): " + e.getMessage(), e);
        }
        return store;
    }

    private static char[] storePassword(String password) {
        return password == null || password.isBlank() ? null : password.toCharArray();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
