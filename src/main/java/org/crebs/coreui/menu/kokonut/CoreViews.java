package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.gui.MainMenuView;
import kr.gomsv.kokonutCore.api.title.TitleCatalog;
import kr.gomsv.kokonutCore.api.title.TitleView;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;

import java.util.function.Consumer;

/**
 * CoreUi screens offered to Kokonut-Core: the title screen for {@code /칭호}, its 색 닉네임 tab for {@code /별명색}
 * and the main menu for Shift+F.
 * Core shows a readiness message when the main menu returns false. Bukkit drops registrations when CoreUi is disabled.
 * Only load this class after confirming Kokonut-Core is enabled.
 */
public final class CoreViews {
    private CoreViews() {
    }

    /** Registers the views; throws {@link LinkageError} when the installed Kokonut-Core predates the first two. */
    public static void register(CoreUi coreUi) {
        var services = coreUi.getServer().getServicesManager();
        services.register(TitleView.class, player -> openTitles(coreUi, player), coreUi, ServicePriority.Normal);
        services.register(MainMenuView.class, player -> MainMenu.open(coreUi, player), coreUi, ServicePriority.Normal);
        try {
            ColorNicks.register(coreUi);
        } catch (LinkageError olderCore) {
            // Core without nickname colors: the title screen simply has no 색 닉네임 tab.
        }
        try {
            Villages.register(coreUi);
        } catch (LinkageError olderCore) {
            // Core without villages: the 마을 tab says the screen is not available.
        }
    }

    /** The title screen; false when Core's title service is missing or the glyphs are not loaded. */
    public static boolean openTitles(CoreUi coreUi, Player player) {
        TitleCatalog catalog = coreUi.getServer().getServicesManager().load(TitleCatalog.class);
        if (catalog == null) return false;
        try {
            return TitleMenu.open(coreUi, catalog, player, colorTab(coreUi));
        } catch (LinkageError olderCore) {
            return false;
        }
    }

    /** The nickname color screen; false when Core's color service is missing or the glyphs are not loaded. */
    public static boolean openColorNicks(CoreUi coreUi, Player player) {
        try {
            return ColorNicks.open(coreUi, player);
        } catch (LinkageError olderCore) {
            return false;
        }
    }

    /** What the title screen's 색 닉네임 tab does; null (no tab) when Core has no nickname colors. */
    private static Consumer<Player> colorTab(CoreUi coreUi) {
        try {
            if (!ColorNicks.available(coreUi)) return null;
        } catch (LinkageError olderCore) {
            return null;
        }
        return p -> {
            if (!openColorNicks(coreUi, p)) Messages.send(p, "색 닉네임 화면을 아직 불러오지 못했어요.");
        };
    }

    /** Plugin name of the damage skin fork (Kokonut_Damage). */
    static final String DAMAGE_PLUGIN = "kokonut_damage";

    /** Whether the 꾸미기 screens show the 데미지 스킨 tab. */
    static boolean damageSkinAvailable(CoreUi coreUi) {
        return coreUi.getServer().getPluginManager().isPluginEnabled(DAMAGE_PLUGIN);
    }

    /** The 데미지 스킨 tab: the book screen of {@link DamageSkinMenu}, with 칭호 and 색 닉네임 tabs back. */
    static void openDamageSkins(CoreUi coreUi, Player player) {
        if (!damageSkinAvailable(coreUi)) {
            Messages.send(player, "데미지 스킨 플러그인이 꺼져 있어요.");
            return;
        }
        boolean opened;
        try {
            opened = DamageSkinMenu.open(coreUi, DamageSkins.INSTANCE, player, p -> {
                if (!openTitles(coreUi, p)) Messages.send(p, "칭호 화면을 아직 불러오지 못했어요.");
            }, colorTab(coreUi));
        } catch (LinkageError olderDamagePlugin) {
            opened = false;
        }
        if (!opened) Messages.send(player, "데미지 스킨 화면을 아직 불러오지 못했어요.");
    }

    /** Plugin name of the move effect plugin (Kokonut_Particle). */
    static final String PARTICLE_PLUGIN = "kokonut_particle";

    /** Whether the 꾸미기 screens show the 잔상 tab. */
    static boolean afterimageAvailable(CoreUi coreUi) {
        if (!coreUi.getServer().getPluginManager().isPluginEnabled(PARTICLE_PLUGIN)) return false;
        try {
            return Afterimages.available();
        } catch (LinkageError olderParticlePlugin) {
            return false;
        }
    }

    /** What the other 꾸미기 screens' 잔상 tab does; null (no tab) when kokonut_particle is off. */
    static Consumer<Player> afterimageTab(CoreUi coreUi) {
        return afterimageAvailable(coreUi) ? p -> openAfterimages(coreUi, p) : null;
    }

    /** The 잔상 tab: the book screen of {@link AfterimageMenu}, with the other 꾸미기 tabs back. */
    static void openAfterimages(CoreUi coreUi, Player player) {
        if (!afterimageAvailable(coreUi)) {
            Messages.send(player, "잔상 플러그인이 꺼져 있어요.");
            return;
        }
        boolean opened;
        try {
            opened = AfterimageMenu.open(coreUi, Afterimages.INSTANCE, player, new AfterimageMenu.Tabs(
                    p -> { if (!openTitles(coreUi, p)) Messages.send(p, "칭호 화면을 아직 불러오지 못했어요."); },
                    colorTab(coreUi),
                    damageSkinAvailable(coreUi) ? p -> openDamageSkins(coreUi, p) : null));
        } catch (LinkageError olderParticlePlugin) {
            opened = false;
        }
        if (!opened) Messages.send(player, "잔상 화면을 아직 불러오지 못했어요.");
    }

    /** {@code /마을} without arguments opens the village screen. */
    private static final class Villages {
        static void register(CoreUi coreUi) {
            coreUi.getServer().getServicesManager().register(kr.gomsv.kokonutCore.api.village.VillageView.class,
                    player -> org.crebs.coreui.menu.village.VillageMenu.open(coreUi, player), coreUi, ServicePriority.Normal);
        }
    }

    /** Everything that names Core's nickname color API, so an older Core only fails inside this class. */
    private static final class ColorNicks {
        static void register(CoreUi coreUi) {
            coreUi.getServer().getServicesManager().register(kr.gomsv.kokonutCore.api.nickname.ColorNickView.class,
                    player -> openColorNicks(coreUi, player), coreUi, ServicePriority.Normal);
        }

        static boolean available(CoreUi coreUi) {
            return coreUi.getServer().getServicesManager().load(kr.gomsv.kokonutCore.api.nickname.ColorNickCatalog.class) != null;
        }

        static boolean open(CoreUi coreUi, Player player) {
            var catalog = coreUi.getServer().getServicesManager().load(kr.gomsv.kokonutCore.api.nickname.ColorNickCatalog.class);
            if (catalog == null) return false;
            return ColorNickMenu.open(coreUi, catalog, player, p -> {
                if (!openTitles(coreUi, p)) Messages.send(p, "칭호 화면을 아직 불러오지 못했어요.");
            });
        }
    }
}
