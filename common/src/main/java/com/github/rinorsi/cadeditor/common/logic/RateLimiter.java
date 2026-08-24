package com.github.rinorsi.cadeditor.common.logic;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
    private static final Map<UUID, PlayerRateTracker> trackers = new ConcurrentHashMap<>();
    private static final long REQUEST_WINDOW_MS = 1000;
    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long UPDATE_WINDOW_MS = 5000;
    private static final int MAX_UPDATES_PER_WINDOW = 3;

    private RateLimiter() {
    }

    public static boolean allowRequest(ServerPlayer player) {
        return checkAndConsume(player, REQUEST_WINDOW_MS, MAX_REQUESTS_PER_WINDOW, "request");
    }

    public static boolean allowUpdate(ServerPlayer player) {
        return checkAndConsume(player, UPDATE_WINDOW_MS, MAX_UPDATES_PER_WINDOW, "update");
    }

    private static synchronized boolean checkAndConsume(ServerPlayer player, long windowMs, int maxCount, String operation) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        PlayerRateTracker tracker = trackers.computeIfAbsent(uuid, k -> new PlayerRateTracker());

        tracker.cleanExpired(now);
        int count = tracker.getCount(now, windowMs);

        if (count >= maxCount) {
            SecurityAuditLog.logRateLimitExceeded(player, operation);
            return false;
        }

        tracker.record(now);
        return true;
    }

    public static void removePlayer(UUID uuid) {
        trackers.remove(uuid);
    }

    private static class PlayerRateTracker {
        private final java.util.List<Long> timestamps = new java.util.concurrent.CopyOnWriteArrayList<>();

        int getCount(long now, long windowMs) {
            long cutoff = now - windowMs;
            return (int) timestamps.stream().filter(t -> t > cutoff).count();
        }

        void record(long now) {
            timestamps.add(now);
        }

        void cleanExpired(long now) {
            long cutoff = now - Math.max(REQUEST_WINDOW_MS, UPDATE_WINDOW_MS) * 2;
            timestamps.removeIf(t -> t < cutoff);
        }
    }
}
