package com.wickedsik.personalworlds.command.executor;

import com.wickedsik.personalworlds.command.CommandResult;
import com.wickedsik.personalworlds.util.PerformanceMonitor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Executor for debug/testing commands.
 * Handles performance monitoring operations (OP level 4 only).
 *
 * Commands:
 * - /pi debug perf enable
 * - /pi debug perf disable
 * - /pi debug perf status
 * - /pi debug perf reset
 */
public class DebugCommandExecutor {

    /**
     * Enable performance monitoring.
     */
    public CommandResult enablePerf() {
        PerformanceMonitor.enable();
        return CommandResult.successBroadcast(
            Text.translatable("personalworlds.command.perf.enabled")
                .formatted(Formatting.GREEN)
        );
    }

    /**
     * Disable performance monitoring.
     */
    public CommandResult disablePerf() {
        PerformanceMonitor.disable();
        return CommandResult.successBroadcast(
            Text.translatable("personalworlds.command.perf.disabled")
                .formatted(Formatting.YELLOW)
        );
    }

    /**
     * Show performance monitoring status.
     * Returns null result as this sends multiple lines directly.
     *
     * @param source Command source for sending multi-line output
     * @param server Server for getting status
     */
    public void showStatus(ServerCommandSource source, MinecraftServer server) {
        String status = PerformanceMonitor.getStatusSummary(server);
        for (String line : status.split("\n")) {
            final String finalLine = line;
            source.sendFeedback(() -> Text.literal(finalLine), false);
        }
    }

    /**
     * Reset performance counters.
     */
    public CommandResult resetCounters() {
        PerformanceMonitor.resetCounters();
        return CommandResult.successBroadcast(
            Text.translatable("personalworlds.command.perf.reset")
                .formatted(Formatting.YELLOW)
        );
    }
}
