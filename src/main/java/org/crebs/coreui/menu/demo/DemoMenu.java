package org.crebs.coreui.menu.demo;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.style.ButtonStyle;
import org.crebs.coreui.menu.style.ThemeColour;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class DemoMenu {
    private DemoMenu() {
    }

    public static CoreMenu create(Glyph example, ThemeColour theme, Consumer<Player> close,
                                  BiConsumer<Player, ThemeColour> changeTheme) {
        return CoreMenu.builder("coreui-demo")
                .title("CoreUi Demo")
                .header()
                .theme(theme)
                .size(360, 300)
                .glyph("example-image", example)
                    .position(12, 12).add()
                .label("heading", "<gold>CoreUi reference</gold>")
                    .position(28, 12).add()
                .badge("version")
                    .position(256, 8).size(92, 18).label("Paper 26.3").add()
                .toggle("notifications")
                    .position(10, 36).label("Notifications")
                    .description("Toggle animation").initialValue(true)
                    .onChange((player, value) -> player.sendMessage("Notifications: " + value)).add()
                .checkbox("sound")
                    .position(10, 74).label("Sound effects")
                    .description("Checkbox example").initialValue(true)
                    .onChange((player, value) -> player.sendMessage("Sound effects: " + value)).add()
                .slider("volume")
                    .position(10, 112).label("Volume")
                    .range(0, 100).step(5).initialValue(50)
                    .onChange((player, value) -> player.sendMessage("Volume: " + value)).add()
                .dropdown("theme")
                    .position(10, 150).label("Theme")
                    .description("Try both palettes")
                    .options(Component.text("Dark"), Component.text("Light"))
                    .initialValue(theme == ThemeColour.DARK ? 0 : 1)
                    .onChange((player, value) -> changeTheme.accept(player,
                            value == 0 ? ThemeColour.DARK : ThemeColour.LIGHT)).add()
                .progress("progress")
                    .position(12, 196).size(336, 30).label("Progress example").value(0.65).add()
                .divider("divider")
                    .position(12, 236).size(336, 1).add()
                .button("reset")
                    .position(12, 250).size(104, 24).label("Reset values")
                    .resetValues("notifications", "sound", "volume", "theme").add()
                .button("test")
                    .position(124, 250).size(104, 24).style(ButtonStyle.PRIMARY).label("Test click")
                    .onClick(player -> player.sendMessage("CoreUi demo button clicked!")).add()
                .button("close")
                    .position(236, 250).size(112, 24).label("Close")
                    .onClick(close).add()
                .label("hint", "Apply CoreUi.zip to see the custom UI.")
                    .position(12, 286).add()
                .build();
    }
}
