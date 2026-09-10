package com.redpill_linpro.argus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.redpill_linpro.argus.config.ProfileStore;
import com.redpill_linpro.argus.model.ConnectionProfile;
import com.redpill_linpro.argus.model.Protocol;
import com.redpill_linpro.argus.util.MessageDraftFactory;

class ProfileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsProfiles() throws IOException {
        ProfileStore store = new ProfileStore(tempDir.resolve("profiles.json"));
        ConnectionProfile profile = new ConnectionProfile(
                "local", Protocol.CORE, "localhost", 61616, "admin", "secret", true, true);
        store.upsert(profile);

        List<ConnectionProfile> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals("local", loaded.get(0).name());
        assertEquals(Protocol.CORE, loaded.get(0).protocol());
        assertEquals("admin", loaded.get(0).username());
        assertEquals("secret", loaded.get(0).password());
        assertTrue(loaded.get(0).ssl());
    }

    @Test
    void passwordIsNotPersistedWhenOptOut() throws IOException {
        ProfileStore store = new ProfileStore(tempDir.resolve("profiles.json"));
        ConnectionProfile profile = new ConnectionProfile(
                "remote", Protocol.OPENWIRE, "broker.example.com", 61616, "joe", null, false, false);
        store.upsert(profile);

        List<ConnectionProfile> loaded = store.load();
        assertEquals(1, loaded.size());
        assertNull(loaded.get(0).password());
    }

    @Test
    void upsertReplacesByName() throws IOException {
        ProfileStore store = new ProfileStore(tempDir.resolve("profiles.json"));
        store.upsert(new ConnectionProfile("local", Protocol.CORE, "localhost", 61616, "a", null, false, false));
        store.upsert(new ConnectionProfile("LOCAL", Protocol.CORE, "otherhost", 61616, "b", null, false, false));

        assertEquals(1, store.load().size());
        assertEquals("otherhost", store.load().get(0).host());
    }

    @Test
    void emptyWhenFileMissing() {
        ProfileStore store = new ProfileStore(tempDir.resolve("missing.json"));
        assertTrue(store.load().isEmpty());
    }

    @Test
    void coercesPropertyValues() {
        assertEquals(Boolean.TRUE, MessageDraftFactory.coerce("true"));
        assertEquals(42, MessageDraftFactory.coerce("42"));
        assertEquals(9999999999L, MessageDraftFactory.coerce("9999999999"));
        assertEquals(1.5, MessageDraftFactory.coerce("1.5"));
        assertEquals("hello world", MessageDraftFactory.coerce("hello world"));
    }
}
