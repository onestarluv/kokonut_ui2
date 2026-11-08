package org.crebs.coreui.menu.kokonut;

import org.crebs.coreui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.dex.DexCatalog;
import org.crebs.coreui.menu.dex.ItemDexMenu;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.style.ThemeColour;

import java.util.*;
import java.util.function.Consumer;

/**
 * Main menu (이동 · 도감 · 꾸미기 · 우편함 · 설정) in the dex's leather-and-parchment style. All sizes are GUI px.
 * Glyphs: {@code menu/ui/...} (bundled PNGs), {@code menu/item/...} and {@code dex/...} (vanilla item icons, dex kit).
 * Like the dex, every glyph hangs from the text line its top is on, so later (lower) elements paint on top.
 */
public final class MainMenu {
    static final int WIDTH = 320, HEIGHT = 136;
    private static final int PANEL_Y = 20, CARD_Y = 66, CARD_W = 40, CARD_H = 54, CARD_GAP = 4;
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            GOLD = TextColor.color(0xA8740A), RED = TextColor.color(0xA83A2A), PURPLE = TextColor.color(0x7A4FB0),
            WHITE = TextColor.color(0xFFFFFF);

    /**
     * What the profile row shows; {@code null} balances mean "still loading / unavailable" and {@code mail} is the
     * unclaimed mail count from Kokonut-Mail (null when unknown). The dex numbers are kept for callers and checks.
     */
    record Info(String name, Component title, String place, Long gold, Long kokonut, Long mail,
                int dexRegistered, int dexTotal, int rewards, PlayerHeadObjectContents head) {}

    /** {@code icon} is a full glyph id: hand-drawn art ({@code menu/icon/<name>_32}) or a vanilla icon ({@code menu/item/<key>}). */
    record Button(String id, String label, String icon, Consumer<Player> action) {}

    private MainMenu() {
    }

    /** Returns false while the menu glyphs are not loaded. */
    public static boolean open(CoreUi coreUi, Player player) {
        var glyphs = coreUi.getGlyphs().all();
        if (!glyphs.containsKey("menu/ui/panel_116_0") || !glyphs.containsKey("menu/icon/deco_32") || !glyphs.containsKey("menu/icon/setting_32")
                || !glyphs.containsKey("menu/ui/profile_148") || !glyphs.containsKey("menu/ui/tag_128")) return false;
        coreUi.open(player, build(coreUi, coreUi.getGlyphs().all(), info(coreUi, player)));
        return true;
    }

    private static Info info(CoreUi coreUi, Player player) {
        Long gold = null, kokonut = null;
        Component title = null;
        String name = player.getName();
        if (coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) {
            name = CoreBalance.displayName(player);
            var balance = CoreBalance.of(player.getUniqueId());
            if (balance.isPresent()) {
                gold = balance.get().gold();
                kokonut = balance.get().kokonut();
            }
            title = CoreBalance.title(coreUi.getServer(), player.getUniqueId()).orElse(null);
        }
        Long mail = null;
        if (coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Mail")) {
            var count = MailBridge.unclaimed(coreUi.getServer(), player.getUniqueId());
            if (count.isPresent()) mail = count.getAsLong();
        }
        DexCatalog dex = DexCatalog.SAMPLE;
        return new Info(name, title, player.getWorld().getName(), gold, kokonut, mail, dex.registered(), dex.total(), 0, head(player));
    }

    /** The player's own skin face; the profile's signed textures are included so the client can draw it right away. */
    static PlayerHeadObjectContents head(Player player) {
        var head = ObjectContents.playerHead().id(player.getUniqueId()).hat(true);
        if (PlayerHeadObjectContents.isValidName(player.getName())) head.name(player.getName());
        for (var property : player.getPlayerProfile().getProperties()) {
            if (!property.getName().equals("textures")) continue;
            head.profileProperty(property.getSignature() == null
                    ? PlayerHeadObjectContents.property(property.getName(), property.getValue())
                    : PlayerHeadObjectContents.property(property.getName(), property.getValue(), property.getSignature()));
        }
        return head.build();
    }

    /** Seven compact buttons: icon and name only (names up to about 4 Hangul characters fit the 40 px card). */
    static List<Button> buttons(CoreUi coreUi, Info info) {
        return List.of(
                new Button("move", "이동", "menu/icon/warp_32", p -> soon(p, "이동")),
                new Button("dex", "도감", "menu/icon/collect_32", p -> {
                    if (!ItemDexMenu.open(coreUi, p)) Messages.send(p, "도감을 아직 불러오지 못했어요.");
                }),
                new Button("style", "꾸미기", "menu/icon/deco_32", p -> {
                    // 꾸미기 = titles for now (Kokonut-Core); later it can become a hub for titles and cosmetics.
                    if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")
                            || !CoreViews.openTitles(coreUi, p)) soon(p, "꾸미기");
                }),
                new Button("accessory", "장신구", "menu/item/totem_of_undying", p -> run(coreUi, p, "장신구")),
                new Button("quest", "퀘스트", "menu/icon/quest_32", p -> soon(p, "퀘스트")),
                new Button("mail", "우편함", "menu/icon/post_32", p -> {
                    // Kokonut-Mail's MailboxService opens its own mailbox screen; close this dialog first.
                    if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Mail")) {
                        Messages.send(p, "우편함 플러그인이 꺼져 있어요.");
                        return;
                    }
                    coreUi.close(p);
                    if (!MailBridge.open(p)) Messages.send(p, "우편함이 아직 준비되지 않았어요.");
                }),
                new Button("settings", "설정", "menu/icon/setting_32", p -> soon(p, "설정")));
    }

    /** Closes the menu and runs another plugin's command (e.g. /장신구 opens the accessory inventory). */
    private static void run(CoreUi coreUi, Player player, String command) {
        if (coreUi.getServer().getPluginCommand(command) == null) {
            Messages.send(player, command + " 플러그인이 꺼져 있어요.");
            return;
        }
        coreUi.close(player);
        player.performCommand(command);
    }

    private static void soon(Player player, String name) {
        Messages.send(player, name + " 기능은 준비 중이에요.");
    }

    /** Builds one frame; {@code coreUi} is only used inside click callbacks, so offline checks may pass null. */
    static CoreMenu build(CoreUi coreUi, Map<String, Glyph> g, Info info) {
        Canvas c = new Canvas(g);
        // Title tab and close button above the panel, then the panel (its top line is painted after the tab).
        c.image("menu/ui/tab", 12, 0);
        c.image("menu/icon/ego", 16, 2);
        c.text("메인 메뉴", 34, 6, WHITE);
        c.image("dex/ui/close", 300, 0);
        c.click(300, 0, 20, 20, p -> coreUi.close(p));
        c.image("menu/ui/panel_116_0", 0, PANEL_Y);
        c.image("menu/ui/panel_116_1", 160, PANEL_Y);
        c.image("menu/ui/tab_join", 13, PANEL_Y);

        // Profile row: the player's skin face (8x8 at y 36, a line boundary), name, title (or world), gold and kokonut.
        // A backing in the chip colour first (line 3), so the head frame, name and title paint over it.
        c.image("menu/ui/profile_148", 12, 32);
        c.image("menu/ui/head_frame", 14, 34);
        if (info.head() != null) c.b.playerHead("head", info.head()).position(16, 36).add();
        else c.image("menu/icon/kokonut", 16, 34);
        c.text(info.name(), 32, 33, INK);
        if (info.title() != null) c.title(info.title(), 30, 44, 128);
        else c.text("위치 · " + info.place(), 32, 44, DIM);
        c.chip(164, 34, "menu/icon/gold", info.gold(), "골드");
        c.chip(238, 34, "menu/icon/kokonut", info.kokonut(), "코코넛");

        // One row of seven buttons: icon and name.
        List<Button> buttons = buttons(coreUi, info);
        int left = (WIDTH - (buttons.size() * CARD_W + (buttons.size() - 1) * CARD_GAP)) / 2;
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            int x = left + i * (CARD_W + CARD_GAP);
            c.image("menu/ui/card_40", x, CARD_Y);
            c.image("menu/ui/well", x + 2, CARD_Y + 3);
            c.image(button.icon(), x + 4, CARD_Y + 5);
            c.centered(button.label(), x, CARD_W, CARD_Y + 42, INK);
            if (button.id().equals("mail") && info.mail() != null && info.mail() > 0) {
                String count = info.mail() > 99 ? "99+" : String.valueOf(info.mail());
                int width = count.length() > 2 ? 18 : 12;
                // y = CARD_Y + 15 is a text line after the icon well's, so the badge paints over the icon corner.
                c.image("menu/ui/badge_" + width, x + CARD_W - width - 1, CARD_Y + 15);
                c.centered(count, x + CARD_W - width - 1, width, CARD_Y + 17, WHITE);
            }
            c.click(x, CARD_Y, CARD_W, CARD_H, button.action());
        }
        return c.b.build();
    }

    private static final class Canvas {
        final Map<String, Glyph> g;
        final CoreMenuBuilder b = CoreMenu.builder("kokonut-main").title(Component.text("메인 메뉴"))
                .theme(ThemeColour.DARK).noBackground().size(WIDTH, HEIGHT);
        int ids;

        Canvas(Map<String, Glyph> g) {
            this.g = g;
        }

        void image(String id, int x, int y) {
            b.glyph("img" + ids++, Objects.requireNonNull(g.get(id), "missing glyph " + id)).position(x, y).add();
        }

        void click(int x, int y, int w, int h, Consumer<Player> onClick) {
            b.hotspot("hit" + ids++).position(x, y).size(w, h).onClick(onClick).add();
        }

        void text(String value, int x, int y, TextColor color) {
            label(Component.text(value, color), x, y);
        }

        void label(Component text, int x, int y) {
            b.label("txt" + ids++, text).position(x, y)
                    .size(Math.max(1, Math.min(TextWidths.width(text), WIDTH - x)), 9).add();
        }

        /**
         * The equipped title on the dark backing of the title screen ({@code menu/ui/tag_<w>}, drawn at y - 2): titles
         * use chat colours meant for a dark background. Bold adds about a pixel per character to the measured width.
         */
        void title(Component title, int x, int y, int max) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title);
            int width = TextWidths.width(title) + (bold(title) ? plain.codePointCount(0, plain.length()) : 0);
            int tag = Math.min(max / 8 * 8, Math.max(24, (width + 6 + 7) / 8 * 8));
            image("menu/ui/tag_" + tag, x, y - 2);
            b.label("txt" + ids++, title.colorIfAbsent(WHITE)).position(x + 3, y)
                    .size(Math.max(1, Math.min(width, tag - 6)), 9).add();
        }

        private static boolean bold(Component c) {
            return c.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD) == net.kyori.adventure.text.format.TextDecoration.State.TRUE
                    || c.children().stream().anyMatch(Canvas::bold);
        }

        void right(String value, int rightX, int y, TextColor color) {
            text(value, rightX - TextWidths.width(Component.text(value)), y, color);
        }

        void centered(String value, int x, int width, int y, TextColor color) {
            text(value, x + (width - TextWidths.width(Component.text(value))) / 2, y, color);
        }

        /** Currency chip: icon + amount, "…" while Core's snapshot is loading or Core is absent. */
        void chip(int x, int y, String icon, Long amount, String unit) {
            image("menu/ui/chip_70", x, y);
            image(icon, x + 1, y);
            String value = amount == null ? "…" : String.format("%,d", amount);
            right(value, x + 66, y + 4, GOLD);
            b.hotspot("tip" + ids++).position(x, y).size(70, 16).onClick(p -> Messages.send(p, unit + " " + value)).add();
        }

        /** 100 px bar with power-of-two fill pieces from the dex kit. */
        void progress(int x, int y, double ratio) {
            image("dex/ui/bar_100", x, y);
            int amount = (int) Math.round(98 * Math.clamp(ratio, 0, 1)), cursor = x + 1;
            for (int piece = 64; piece >= 1; piece /= 2) {
                while (amount >= piece) {
                    image("dex/ui/fill_" + piece, cursor, y + 1);
                    cursor += piece;
                    amount -= piece;
                }
            }
        }
    }
}
