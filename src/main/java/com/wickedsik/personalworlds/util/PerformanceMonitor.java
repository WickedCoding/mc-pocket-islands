package com.wickedsik.personalworlds.util;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.dimension.DimensionManager;
import net.minecraft.server.MinecraftServer;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitoring utilities for development and testing.
 *
 * Tracks:
 * - Dimension load times
 * - Teleportation latency
 * - Memory usage trends
 * - Active dimension count
 */
public class PerformanceMonitor {

    private static final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> timers = new ConcurrentHashMap<>();

    private static boolean enabled = false;

    /**
     * Enable performance monitoring (for debugging/testing).
     */
    public static void enable() {
        enabled = true;
        PersonalWorldsMod.LOGGER.info("Performance monitoring ENABLED");
    }

    /**
     * Disable performance monitoring.
     */
    public static void disable() {
        enabled = false;
        counters.clear();
        timers.clear();
        PersonalWorldsMod.LOGGER.info("Performance monitoring DISABLED");
    }

    /**
     * Check if performance monitoring is enabled.
     *
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Start a timer for an operation.
     *
     * @param operation The operation name
     */
    public static void startTimer(String operation) {
        if (!enabled) return;
        timers.put(operation, System.nanoTime());
    }

    /**
     * Stop a timer and log the elapsed time.
     *
     * @param operation The operation name
     * @return Elapsed time in milliseconds
     */
    public static long stopTimer(String operation) {
        if (!enabled) return 0;

        Long start = timers.remove(operation);
        if (start == null) return 0;

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        PersonalWorldsMod.LOGGER.info("[PERF] {}: {}ms", operation, elapsedMs);

        return elapsedMs;
    }

    /**
     * Increment a counter.
     *
     * @param counter The counter name
     */
    public static void increment(String counter) {
        if (!enabled) return;
        counters.computeIfAbsent(counter, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Get the current value of a counter.
     *
     * @param counter The counter name
     * @return The counter value, or 0 if not found
     */
    public static long getCounter(String counter) {
        AtomicLong value = counters.get(counter);
        return value != null ? value.get() : 0;
    }

    /**
     * Log current status.
     *
     * @param server The Minecraft server
     */
    public static void logStatus(MinecraftServer server) {
        if (!enabled) return;

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);

        int loadedDimensions = DimensionManager.getLoadedDimensionCount();
        int onlinePlayers = server.getCurrentPlayerCount();

        PersonalWorldsMod.LOGGER.info("[PERF] Status: {} dims loaded, {} players, heap {}/{}MB",
            loadedDimensions, onlinePlayers, heapUsed, heapMax);

        // Log counters
        counters.forEach((name, count) ->
            PersonalWorldsMod.LOGGER.info("[PERF] Counter {}: {}", name, count.get()));
    }

    /**
     * Get a status summary string for command output.
     *
     * @param server The Minecraft server
     * @return Status summary string
     */
    public static String getStatusSummary(MinecraftServer server) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);

        int loadedDimensions = DimensionManager.getLoadedDimensionCount();
        int onlinePlayers = server.getCurrentPlayerCount();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Performance Status ===\n");
        sb.append(String.format("Loaded dimensions: %d\n", loadedDimensions));
        sb.append(String.format("Online players: %d\n", onlinePlayers));
        sb.append(String.format("Heap memory: %d/%dMB (%.1f%%)\n", heapUsed, heapMax,
            (double) heapUsed / heapMax * 100));
        sb.append(String.format("Memory pressure: %s\n", isMemoryPressureHigh() ? "HIGH" : "Normal"));

        if (enabled && !counters.isEmpty()) {
            sb.append("\n=== Counters ===\n");
            counters.forEach((name, count) ->
                sb.append(String.format("%s: %d\n", name, count.get())));
        }

        return sb.toString();
    }

    /**
     * Check if memory usage is concerning.
     *
     * @return true if memory pressure is high
     */
    public static boolean isMemoryPressureHigh() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();

        double usage = (double) heapUsed / heapMax;
        return usage > 0.85; // 85% threshold
    }

    /**
     * Reset all counters.
     */
    public static void resetCounters() {
        counters.clear();
        PersonalWorldsMod.LOGGER.info("[PERF] Counters reset");
    }
}
