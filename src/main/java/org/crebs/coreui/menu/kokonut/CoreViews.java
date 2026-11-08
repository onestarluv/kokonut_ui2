package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.gui.MainMenuView;
import kr.gomsv.kokonutCore.api.title.TitleCatalog;
import kr.gomsv.kokonutCore.api.title.TitleView;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.crebs.coreui.CoreUi;

/**
 * CoreUi screens offered to Kokonut-Core: the title screen for {@code /칭호} and the main menu for Shift+F.
 * Core shows a readiness message when the main menu returns false. Bukkit drops registrations when CoreUi is disabled.
 * Only load this class after confirming Kokonut-Core is enabled.
 */
public final class CoreViews {
    private CoreViews() {
    }

    /** Registers both views; throws {@link LinkageError} when the installed Kokonut-Core predates them. */
    public static void register(CoreUi coreUi) {
        var services = coreUi.getServer().getServicesManager();
        services.register(TitleView.class, player -> openTitles(coreUi, player), coreUi, ServicePriority.Normal);
        services.register(MainMenuView.class, player -> MainMenu.open(coreUi, player), coreUi, ServicePriority.Normal);
    }

    /** The title screen; false when Core's title service is missing or the glyphs are not loaded. */
    public static boolean openTitles(CoreUi coreUi, Player player) {
        TitleCatalog catalog = coreUi.getServer().getServicesManager().load(TitleCatalog.class);
        if (catalog == null) return false;
        try {
            return TitleMenu.open(coreUi, catalog, player);
        } catch (LinkageError olderCore) {
            return false;
        }
    }
}
