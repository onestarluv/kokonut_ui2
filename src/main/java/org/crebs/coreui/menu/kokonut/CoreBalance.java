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

    /**
     * What other players see: the server nickname (/닉네임) when set, else the Minecraft name. Core's cache never
     * blocks; while the first lookup is in flight the Minecraft name is shown and the next redraw picks the nickname up.
     */
    public static String displayName(org.bukkit.entity.Player player) {
        try {
            Optional<String> nickname = kr.gomsv.kokonutCore.api.util.ServerNicknameUtil.cachedNickname(player.getUniqueId());
            if (nickname != null && nickname.isPresent() && !nickname.get().isBlank()) return nickname.get();
        } catch (LinkageError olderCore) {
            // An older Kokonut-Core without the cache: keep the Minecraft name.
        }
        return player.getName();
    }

    public static Optional<Balance> of(UUID player) {
        return CurrencyUtil.snapshot(player).map(account -> new Balance(account.gold(), account.kokonut()));
    }

    /** All three balances for the shop from the same non-blocking snapshot; empty while it is still loading. */
    public static Optional<org.crebs.coreui.menu.shop.ShopCatalog.Wallet> wallet(UUID player) {
        return CurrencyUtil.snapshot(player).map(account ->
                new org.crebs.coreui.menu.shop.ShopCatalog.Wallet(account.silver(), account.gold(), account.kokonut()));
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
