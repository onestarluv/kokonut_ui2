package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/** Core's MessageUtil. Only load this class after confirming Kokonut-Core is enabled (see {@link org.crebs.coreui.Messages}). */
public final class CoreMessages {
    private CoreMessages() {
    }

    public static void send(CommandSender recipient, Component message) {
        MessageUtil.send(recipient, message);
    }
}
