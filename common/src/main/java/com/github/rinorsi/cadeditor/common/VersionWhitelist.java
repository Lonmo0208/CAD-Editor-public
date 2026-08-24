package com.github.rinorsi.cadeditor.common;

import com.github.rinorsi.cadeditor.PlatformUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Version exemption whitelist: after an OP adds a player via command,
 * that player can join the server even with an old client (≤0.1.1) and won't be kicked.
 * Data is persisted to config/cadeditor-version-whitelist.json.
 */
public final class VersionWhitelist {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = PlatformUtil.getConfigDir().resolve("cadeditor-version-whitelist.json");
    private static final Set<UUID> WHITELISTED = new HashSet<>();
    private static boolean loaded = false;

    private VersionWhitelist() {
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                Data data = GSON.fromJson(reader, Data.class);
                WHITELISTED.clear();
                if (data != null && data.whitelistedPlayers != null) {
                    for (String value : data.whitelistedPlayers) {
                        try {
                            WHITELISTED.add(UUID.fromString(value));
                        } catch (IllegalArgumentException ignored) {
                            LOGGER.warn("Skipping invalid UUID in version whitelist: {}", value);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load version whitelist", e);
            }
        }
        loaded = true;
    }

    public static boolean isWhitelisted(UUID uuid) {
        ensureLoaded();
        return WHITELISTED.contains(uuid);
    }

    public static boolean add(UUID uuid) {
        ensureLoaded();
        boolean added = WHITELISTED.add(uuid);
        if (added) {
            save();
        }
        return added;
    }

    public static boolean remove(UUID uuid) {
        ensureLoaded();
        boolean removed = WHITELISTED.remove(uuid);
        if (removed) {
            save();
        }
        return removed;
    }

    public static Set<UUID> getWhitelisted() {
        ensureLoaded();
        return new HashSet<>(WHITELISTED);
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static void save() {
        Data data = new Data();
        data.whitelistedPlayers = WHITELISTED.stream().map(UUID::toString).toList();
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save version whitelist", e);
        }
    }

    private static class Data {
        public List<String> whitelistedPlayers;
    }
}
