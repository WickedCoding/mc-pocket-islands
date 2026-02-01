package com.wickedsik.personalworlds.compat;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

/**
 * Compatibility layer for Text component API differences.
 * <p>
 * MC 1.20.x uses: new ClickEvent(Action, value), new HoverEvent(Action, content)
 * MC 1.21.x uses: new ClickEvent.RunCommand(command), new HoverEvent.ShowText(text)
 * (ClickEvent and HoverEvent are now sealed interfaces with record implementations)
 * <p>
 * This class centralizes text event creation to simplify version migration.
 */
public final class TextCompat {

    private TextCompat() {
        // Utility class
    }

    /**
     * Create a ClickEvent for running a command.
     */
    public static ClickEvent runCommand(String command) {
        //? if >=1.21 {
        return new ClickEvent.RunCommand(command);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
    }

    /**
     * Create a ClickEvent for suggesting a command.
     */
    public static ClickEvent suggestCommand(String command) {
        //? if >=1.21 {
        return new ClickEvent.SuggestCommand(command);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
        *///?}
    }

    /**
     * Create a ClickEvent for opening a URL.
     */
    public static ClickEvent openUrl(String url) {
        //? if >=1.21 {
        return new ClickEvent.OpenUrl(java.net.URI.create(url));
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        *///?}
    }

    /**
     * Create a HoverEvent that shows text on hover.
     */
    public static HoverEvent showText(Text text) {
        //? if >=1.21 {
        return new HoverEvent.ShowText(text);
        //?} else {
        /*return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        *///?}
    }
}
