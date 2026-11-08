package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutMail.api.MailboxService;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.OptionalLong;
import java.util.UUID;

/** Kokonut-Mail mailbox access. Only load this class after confirming Kokonut-Mail is enabled. */
public final class MailBridge {
    private MailBridge() {
    }

    /**
     * Makes CoreUi's dialog the mailbox screen: Kokonut-Mail asks a registered {@link kr.gomsv.kokonutMail.api.MailboxView}
     * first and falls back to its chest GUI when this returns false (glyphs not loaded, or an older Kokonut-Mail).
     * Bukkit drops the registration when CoreUi is disabled.
     */
    public static void registerView(org.crebs.coreui.CoreUi coreUi) {
        coreUi.getServer().getServicesManager().register(kr.gomsv.kokonutMail.api.MailboxView.class, player -> {
            MailboxService mail = player.getServer().getServicesManager().load(MailboxService.class);
            if (mail == null) return false;
            try {
                return MailMenu.open(coreUi, mail, player);
            } catch (LinkageError olderMailPlugin) {
                return false;
            }
        }, coreUi, org.bukkit.plugin.ServicePriority.Normal);
    }

    /** Opens the player's mailbox through Kokonut-Mail's public service; false when the service is not registered. */
    public static boolean open(Player player) {
        MailboxService mail = player.getServer().getServicesManager().load(MailboxService.class);
        if (mail == null) return false;
        mail.open(player);
        return true;
    }

    /**
     * Last known unclaimed mail count (memory only, may lag one poll); empty when unknown or when the installed
     * Kokonut-Mail predates {@code unclaimedCount}.
     */
    public static OptionalLong unclaimed(Server server, UUID player) {
        MailboxService mail = server.getServicesManager().load(MailboxService.class);
        if (mail == null) return OptionalLong.empty();
        try {
            return mail.unclaimedCount(player);
        } catch (LinkageError olderMailPlugin) {
            return OptionalLong.empty();
        }
    }
}
