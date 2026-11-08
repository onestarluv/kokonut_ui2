package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.title.TitleLookup;
import kr.gomsv.kokonutCore.api.util.CurrencyUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;

import java.util.Optional;
import java.util.UUID;

/** Kokonut-Core lookups for the main menu. Only load this class after confirming Kokonut-Core is enabled. */
public final class CoreBalance {
    /** Gold and kokonut from Core's non-blocking UI snapshot; empty while it is still loading. */
    public record Balance(long gold, long kokonut) {}

    private CoreBalance() {
    }

    public static Optional<Balance> of(UUID player) {
        return CurrencyUtil.snapshot(player).map(account -> new Balance(account.gold(), account.kokonut()));
    }

    /**
     * The equipped title from Core's {@link TitleLookup} service (memory only), or empty without one.
     * Titles that pick their own font fall back to plain text, because menu labels measure widths with the menu font.
     */
    public static Optional<Component> title(Server server, UUID player) {
        TitleLookup titles = server.getServicesManager().load(TitleLookup.class);
        if (titles == null) return Optional.empty();
        String rich = titles.placeholder(player, "title");
        if (rich == null || rich.isBlank()) return Optional.empty();
        if (rich.contains("<font")) return Optional.of(Component.text(titles.placeholder(player, "title_plain")));
        return Optional.of(MiniMessage.miniMessage().deserialize(rich));
    }
}
