package com.wickedsik.personalworlds.command.executor;

import com.wickedsik.personalworlds.command.CommandResult;
import com.wickedsik.personalworlds.util.PerformanceMonitor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

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
            Component.translatable("pocketislands.command.perf.enabled")
                .withStyle(ChatFormatting.GREEN)
        );
    }

    /**
     * Disable performance monitoring.
     */
    public CommandResult disablePerf() {
        PerformanceMonitor.disable();
        return CommandResult.successBroadcast(
            Component.translatable("pocketislands.command.perf.disabled")
                .withStyle(ChatFormatting.YELLOW)
        );
    }

    /**
     * Show performance monitoring status.
     * Returns null result as this sends multiple lines directly.
     *
     * @param source Command source for sending multi-line output
     * @param server Server for getting status
     */
    public void showStatus(CommandSourceStack source, MinecraftServer server) {
        String status = PerformanceMonitor.getStatusSummary(server);
        for (String line : status.split("\n")) {
            final String finalLine = line;
            source.sendSuccess(() -> Component.literal(finalLine), false);
        }
    }

    /**
     * Reset performance counters.
     */
    public CommandResult resetCounters() {
        PerformanceMonitor.resetCounters();
        return CommandResult.successBroadcast(
            Component.translatable("pocketislands.command.perf.reset")
                .withStyle(ChatFormatting.YELLOW)
        );
    }
}
