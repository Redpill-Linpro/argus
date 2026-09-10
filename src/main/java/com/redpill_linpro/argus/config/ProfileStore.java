package com.redpill_linpro.argus.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.redpill_linpro.argus.model.ConnectionProfile;

public final class ProfileStore {

    public static final Path DEFAULT_FILE =
            Path.of(System.getProperty("user.home"), ".argus", "profiles.json");

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public ProfileStore() {
        this(DEFAULT_FILE);
    }

    public ProfileStore(Path file) {
        this.file = file;
    }

    public List<ConnectionProfile> load() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try (var reader = Files.newBufferedReader(file)) {
            return mapper.readValue(reader, new TypeReference<List<ConnectionProfile>>() {
            });
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void save(List<ConnectionProfile> profiles) throws IOException {
        Files.createDirectories(file.getParent());
        try (var writer = Files.newBufferedWriter(file)) {
            mapper.writeValue(writer, profiles);
        }
    }

    public void upsert(ConnectionProfile profile) throws IOException {
        List<ConnectionProfile> profiles = load();
        profiles.removeIf(p -> p.name().equalsIgnoreCase(profile.name()));
        profiles.add(profile);
        save(profiles);
    }

    public void remove(String name) throws IOException {
        List<ConnectionProfile> profiles = load();
        profiles.removeIf(p -> p.name().equalsIgnoreCase(name));
        save(profiles);
    }

    public Optional<ConnectionProfile> find(String name) {
        return load().stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst();
    }
}
