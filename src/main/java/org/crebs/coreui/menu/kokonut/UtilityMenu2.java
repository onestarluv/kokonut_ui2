package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.gui.UtilityMenuService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.style.ThemeColour;

/**
 * CoreUi version of Kokonut-Core's utility menu (Shift+F), laid out like the client mod's KokonutScreen.
 * Only load this class after confirming Kokonut-Core is enabled.
 */
public final class UtilityMenu2 {
    private static final int WIDTH = 340;
    private static final int ROW_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 24;
    private static final TextColor TITLE = TextColor.color(0xFFFFFF);
    private static final TextColor SUBTITLE = TextColor.color(0xB7C9C5);

    private UtilityMenu2() {
    }

    /** Returns false when Kokonut-Core's menu service is unavailable. */
    public static boolean open(CoreUi coreUi, Player player) {
        UtilityMenuService service = coreUi.getServer().getServicesManager().load(UtilityMenuService.class);
        if (service == null) return false;
        coreUi.open(player, create(coreUi, service));
        return true;
    }

    private static CoreMenu create(CoreUi coreUi, UtilityMenuService service) {
        var items = service.items();
        int height = 82 + items.size() * ROW_HEIGHT;
        Component title = Component.text(service.title(), TITLE);
        Component subtitle = Component.text(service.subtitle(), SUBTITLE);
        CoreMenuBuilder builder = CoreMenu.builder("kokonut-utility")
                .title(title)
                .theme(ThemeColour.DARK)
                .size(WIDTH, height)
                .label("title", title).position(centered(title), 12).add()
                .label("subtitle", subtitle).position(centered(subtitle), 28).add();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            builder.button("entry-" + item.id())
                    .position(16, 48 + i * ROW_HEIGHT).size(WIDTH - 32, BUTTON_HEIGHT)
                    .label(Component.text(item.title()))
                    .onClick(player -> {
                        coreUi.close(player);
                        service.run(player, item.id());
                    }).add();
        }
        return builder.button("close")
                .position(WIDTH / 2 - 45, height - 29).size(90, BUTTON_HEIGHT)
                .label(Component.text("닫기"))
                .onClick(coreUi::close).add()
                .build();
    }

    private static int centered(Component text) {
        return Math.max(0, (WIDTH - TextWidths.width(text)) / 2);
    }
}
