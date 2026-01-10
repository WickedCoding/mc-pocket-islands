package com.wickedsik.personalworlds.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Result value object for command execution.
 * Encapsulates success/failure, feedback message, and broadcast flag.
 *
 * @param success Whether the command succeeded
 * @param message Feedback message to send to the command source
 * @param broadcast Whether to broadcast this message to other operators
 */
public record CommandResult(
    boolean success,
    Text message,
    boolean broadcast
) {
    /** Brigadier return value for successful command execution. */
    public static final int SUCCESS = 1;

    /** Brigadier return value for failed command execution. */
    public static final int FAILURE = 0;
    /**
     * Create a successful result with a message.
     */
    public static CommandResult success(Text message) {
        return new CommandResult(true, message, false);
    }

    /**
     * Create a successful result with broadcast enabled.
     */
    public static CommandResult successBroadcast(Text message) {
        return new CommandResult(true, message, true);
    }

    /**
     * Create an error result with a message.
     */
    public static CommandResult error(Text message) {
        return new CommandResult(false, message, false);
    }

    /**
     * Create a silent success (no message).
     */
    public static CommandResult silent() {
        return new CommandResult(true, null, false);
    }

    /**
     * Convert to Brigadier command return value.
     */
    public int toCommandReturn() {
        return success ? SUCCESS : FAILURE;
    }

    /**
     * Apply this result to a command source - sends feedback/error and returns command value.
     */
    public int applyTo(ServerCommandSource source) {
        if (message != null) {
            if (success) {
                final Text msg = message;
                source.sendFeedback(() -> msg, broadcast);
            } else {
                source.sendError(message);
            }
        }
        return toCommandReturn();
    }
}
