package com.wickedsik.personalworlds.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

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
    Component message,
    boolean broadcast
) {
    /** Brigadier return value for successful command execution. */
    public static final int SUCCESS = 1;

    /** Brigadier return value for failed command execution. */
    public static final int FAILURE = 0;
    /**
     * Create a successful result with a message.
     */
    public static CommandResult success(Component message) {
        return new CommandResult(true, message, false);
    }

    /**
     * Create a successful result with broadcast enabled.
     */
    public static CommandResult successBroadcast(Component message) {
        return new CommandResult(true, message, true);
    }

    /**
     * Create an error result with a message.
     */
    public static CommandResult error(Component message) {
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
    public int applyTo(CommandSourceStack source) {
        if (message != null) {
            if (success) {
                final Component msg = message;
                source.sendSuccess(() -> msg, broadcast);
            } else {
                source.sendFailure(message);
            }
        }
        return toCommandReturn();
    }
}
