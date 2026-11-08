package org.crebs.coreui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * System messages to players and the console. With Kokonut-Core they go through Core's MessageUtil (system heading,
 * the client chat's system channel, the same look as every other Kokonut message); without Core they are plain chat.
 */
public final class Messages {
    private Messages() {
    }

    public static void send(CommandSender recipient, String message) {
        send(recipient, Component.text(message));
    }

    public static void send(CommandSender recipient, Component message) {
        if (Bukkit.getPluginManager().isPluginEnabled("Kokonut-Core")) {
            try {
                org.crebs.coreui.menu.kokonut.CoreMessages.send(recipient, message);
                return;
            } catch (LinkageError olderCore) {
                // Fall through to plain chat.
            }
        }
        recipient.sendMessage(message);
    }
}
