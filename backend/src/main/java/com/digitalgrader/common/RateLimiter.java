package com.digitalgrader.common;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window rate limiter keyed by an arbitrary string such as a client IP. */
public class RateLimiter {
    private static final int MAX_TRACKED_KEYS = 10_000;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final int capacity;
    private final Duration window;
    private final Clock clock;

    public RateLimiter(int capacity, Duration window, Clock clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be at least 1");
        this.capacity = capacity;
        this.window = window;
        this.clock = clock;
    }

    /** Records one hit for the key and reports whether it fits under the capacity. */
    public boolean allow(String key) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();
        sweep(cutoff);
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) times.pollFirst();
            if (times.size() >= capacity) return false;
            times.addLast(now);
            return true;
        }
    }

    public long retryAfterSeconds() { return window.toSeconds(); }

    // Opportunistic sweep so abandoned keys (for example spoofed addresses) cannot grow the map without bound.
    private void sweep(long cutoff) {
        if (hits.size() <= MAX_TRACKED_KEYS) return;
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) { return entry.getValue().peekLast() != null && entry.getValue().peekLast() < cutoff; }
        });
    }
}
