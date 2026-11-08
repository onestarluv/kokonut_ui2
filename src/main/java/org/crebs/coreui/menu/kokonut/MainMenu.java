package org.crebs.coreui.menu.kokonut;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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
    static final int WIDTH = 320, HEIGHT = 224;
    private static final int PANEL_Y = 20, CARD_Y = 66, CARD_W = 54, CARD_GAP = 6;
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            GOLD = TextColor.color(0xA8740A), GREEN = TextColor.color(0x3E7A22), RED = TextColor.color(0xA83A2A),
            BROWN = TextColor.color(0x6B4A2A), PURPLE = TextColor.color(0x7A4FB0), WHITE = TextColor.color(0xFFFFFF), FOOTER = TextColor.color(0xE8E8E8);

    /** What the profile row and status strip show; {@code null} balances mean "still loading / unavailable". */
    /** {@code mail} is the unclaimed mail count from Kokonut-Mail, null when unknown. */
    record Info(String name, Component title, String place, Long gold, Long kokonut, Long mail,
                int dexRegistered, int dexTotal, int rewards) {}

    record Button(String id, String label, String sub, String icon, Consumer<Player> action) {}

    private MainMenu() {
    }

    /** Returns false while the menu glyphs are not loaded. */
    public static boolean open(CoreUi coreUi, Player player) {
        if (!coreUi.getGlyphs().all().containsKey("menu/ui/panel_0")) return false;
        coreUi.open(player, build(coreUi, coreUi.getGlyphs().all(), info(coreUi, player)));
        return true;
    }

    private static Info info(CoreUi coreUi, Player player) {
        Long gold = null, kokonut = null;
        Component title = null;
        if (coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) {
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
        // The dex still runs on sample data; its numbers move here once real collection data exists.
        DexCatalog dex = DexCatalog.SAMPLE;
        return new Info(player.getName(), title, player.getWorld().getName(), gold, kokonut, mail,
                dex.registered(), dex.total(), claimable(dex));
    }

    private static int claimable(DexCatalog dex) {
        int count = 0;
        for (var category : dex.categories())
            for (var m : category.milestones()) if (m.state() == DexCatalog.MilestoneState.CLAIMABLE) count++;
        for (var m : dex.globalMilestones()) if (m.state() == DexCatalog.MilestoneState.CLAIMABLE) count++;
        return count;
    }

    /** Sub-labels must stay within the 54 px card (about 6 Hangul characters). */
    static List<Button> buttons(CoreUi coreUi, Info info) {
        return List.of(
                new Button("move", "이동", "워프·마을", "ender_pearl", p -> soon(p, "이동")),
                new Button("dex", "도감", info.dexRegistered() + " / " + info.dexTotal(), "knowledge_book", p -> {
                    if (!ItemDexMenu.open(coreUi, p)) p.sendMessage(Component.text("[메뉴] 도감을 아직 불러오지 못했어요.", RED));
                }),
                new Button("style", "꾸미기", "칭호·장신구", "armor_stand", p -> soon(p, "꾸미기")),
                new Button("mail", "우편함", info.mail() != null && info.mail() > 0 ? "새 우편 " + Math.min(info.mail(), 99) : "우편 확인", "menu/icon/post_32", p -> {
                    // Kokonut-Mail's MailboxService opens its own mailbox GUI; close this dialog first.
                    if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Mail")) {
                        p.sendMessage(Component.text("[메뉴] 우편함 플러그인이 꺼져 있어요.", RED));
                        return;
                    }
                    coreUi.close(p);
                    if (!MailBridge.open(p)) p.sendMessage(Component.text("[메뉴] 우편함이 아직 준비되지 않았어요.", RED));
                }),
                new Button("settings", "설정", "알림·화면", "comparator", p -> soon(p, "설정")));
    }

    private static void soon(Player player, String name) {
        player.sendMessage(Component.text("[메뉴] " + name + " 기능은 준비 중이에요.", GOLD));
    }

    /** Builds one frame; {@code coreUi} is only used inside click callbacks, so offline checks may pass null. */
    static CoreMenu build(CoreUi coreUi, Map<String, Glyph> g, Info info) {
        Canvas c = new Canvas(g);
        // Title tab and close button above the panel, then the panel (its top line is painted after the tab).
        c.image("menu/ui/tab", 12, 0);
        c.image("menu/item/cocoa_beans", 16, 2);
        c.text("메인 메뉴", 34, 6, WHITE);
        c.image("dex/ui/close", 300, 0);
        c.click(300, 0, 20, 20, p -> coreUi.close(p));
        c.image("menu/ui/panel_0", 0, PANEL_Y);
        c.image("menu/ui/panel_1", 160, PANEL_Y);
        c.image("menu/ui/tab_join", 13, PANEL_Y);

        // Profile row.
        c.image("dex/ui/slot_normal", 14, 32);
        c.image("menu/item/cocoa_beans", 16, 34);
        c.text(info.name(), 40, 33, INK);
        // Second line: the equipped Core title, or the current world when the player has none.
        if (info.title() != null) c.label(info.title().colorIfAbsent(PURPLE), 40, 44);
        else c.text("위치 · " + info.place(), 40, 44, DIM);
        c.chip(164, 34, "dex/item/gold_nugget", info.gold(), "골드");
        c.chip(238, 34, "menu/item/cocoa_beans", info.kokonut(), "코코넛");

        // Five buttons.
        List<Button> buttons = buttons(coreUi, info);
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            int x = 13 + i * (CARD_W + CARD_GAP);
            c.image("menu/ui/card", x, CARD_Y);
            c.image("menu/ui/well", x + 9, CARD_Y + 6);
            // Vanilla icons are "menu/item/<key>"; hand-drawn ones (art/icons) are given as full glyph ids.
            c.image(button.icon().contains("/") ? button.icon() : "menu/item/" + button.icon(), x + 11, CARD_Y + 8);
            c.centered(button.label(), x, CARD_W, CARD_Y + 47, INK);
            c.centered(button.sub(), x, CARD_W, CARD_Y + 59, DIM);
            if (button.id().equals("mail") && info.mail() != null && info.mail() > 0) {
                String count = info.mail() > 99 ? "99+" : String.valueOf(info.mail());
                int width = count.length() > 2 ? 18 : 12;
                // y = CARD_Y + 15 is a text line after the icon well's, so the badge paints over the icon corner.
                c.image("menu/ui/badge_" + width, x + CARD_W - width - 2, CARD_Y + 15);
                c.centered(count, x + CARD_W - width - 2, width, CARD_Y + 17, WHITE);
            }
            c.click(x, CARD_Y, CARD_W, 72, button.action());
        }

        // Status strip.
        c.image("menu/ui/chip_146", 14, 146);
        c.image("menu/ui/chip_146", 160, 146);
        c.text("도감", 20, 150, DIM);
        c.progress(44, 151, info.dexRegistered() / (double) Math.max(1, info.dexTotal()));
        c.text(info.dexRegistered() + " / " + info.dexTotal(), 150, 150, INK);
        c.right(info.rewards() > 0 ? "받을 도감 보상 " + info.rewards() + "개" : "받을 보상 없음", 300, 150, info.rewards() > 0 ? GREEN : DIM);
        c.centered("버튼을 누르면 해당 메뉴가 열려요", 0, WIDTH, 176, BROWN);
        c.centered("ESC 닫기", 0, WIDTH, 210, FOOTER);
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
            b.hotspot("tip" + ids++).position(x, y).size(70, 16).onClick(p -> p.sendMessage(
                    Component.text("[메뉴] " + unit + " " + value, GOLD))).add();
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
