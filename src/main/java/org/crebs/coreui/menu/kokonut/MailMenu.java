package org.crebs.coreui.menu.kokonut;

import org.crebs.coreui.Messages;
import kr.gomsv.kokonutMail.api.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.style.ThemeColour;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Kokonut-Mail mailbox as a CoreUi dialog (book layout of the dex). Data and claiming stay in Kokonut-Mail's
 * {@link MailboxService}; this class only draws. Only load it after confirming Kokonut-Mail is enabled.
 *
 * Item names are never drawn as text (vanilla names are translated by the client, so their width is unknown);
 * the icon shows the vanilla texture and hovering shows the item's real tooltip.
 * Vanilla item icons ({@code item/<id>}) only exist at text-line offset 2, so every icon sits 2 px below a slot whose
 * top is on a 9 px line (row and grid y values are multiples of 9).
 */
public final class MailMenu {
    static final int WIDTH = 480, HEIGHT = 292, ROWS = 7, GRID = 32;
    private static final int LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258;
    private static final int[] ROW_Y = {45, 72, 99, 126, 153, 180, 207};
    private static final int[] GRID_Y = {99, 126, 153, 180};
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            RED = TextColor.color(0xA83A2A), GREEN = TextColor.color(0x3E7A22), PAPER = TextColor.color(0xF6ECD0),
            WHITE = TextColor.color(0xFFFFFF);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));
    /** Players with a claim in flight; their clicks are ignored until it finishes. */
    private static final Set<UUID> BUSY = new HashSet<>();

    /** An item as the screen needs it: vanilla icon key, stack size and the client-rendered tooltip. */
    record Slot(String key, int amount, net.kyori.adventure.text.event.HoverEventSource<?> hover) {
        static Slot of(ItemStack item) {
            return item == null ? null : new Slot(item.getType().getKey().getKey(), item.getAmount(), item.asHoverEvent());
        }
    }

    /** One list row: the mail plus its first attachment as a {@link Slot} (null if unreadable). */
    record Row(MailEntry mail, Slot preview) {}

    /** What one frame shows. {@code attachments} belong to {@code selected} (null when nothing is selected). */
    record View(MailPage page, List<Row> rows, MailEntry selected, int attachPage, List<Slot> attachments) {
        static View of(MailPage page, MailEntry selected, int attachPage, List<ItemStack> items) {
            return new View(page, page.entries().stream().map(e -> new Row(e, Slot.of(e.preview()))).toList(),
                    selected, attachPage, items.stream().map(Slot::of).toList());
        }
    }

    private MailMenu() {
    }

    /** {@link MailboxView} entry point: false (chest GUI fallback) while the menu glyphs are not loaded. */
    public static boolean open(CoreUi coreUi, MailboxService mail, Player player) {
        if (!coreUi.getGlyphs().all().containsKey("menu/ui/unknown_item") || !coreUi.getGlyphs().all().containsKey("dex/ui/page_0_0"))
            return false;
        player.playSound(player, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5f, 1f);
        load(coreUi, mail, player, 0, null, 0);
        return true;
    }

    /** Level-up chime when something was received, the villager's "no" when nothing could be (space, busy, error). */
    private static void claimSound(Player player, boolean received) {
        if (!player.isOnline()) return;
        if (received) player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1f);
        else player.playSound(player, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1f);
    }

    /** Fetches the page (and the selected mail's attachments), then draws. Futures complete on the server thread. */
    private static void load(CoreUi coreUi, MailboxService mail, Player player, int page, UUID selected, int attachPage) {
        mail.page(player.getUniqueId(), page, ROWS).whenComplete((loaded, error) -> {
            if (error != null || loaded == null) { failed(player); return; }
            MailEntry pick = loaded.entries().stream().filter(e -> e.id().equals(selected)).findFirst()
                    .orElse(loaded.entries().isEmpty() ? null : loaded.entries().getFirst());
            if (pick == null) { show(coreUi, mail, player, View.of(loaded, null, 0, List.of())); return; }
            int pages = Math.max(1, (pick.attachments() + GRID - 1) / GRID);
            int ap = Math.clamp(pick.id().equals(selected) ? attachPage : 0, 0, pages - 1);
            mail.attachments(player.getUniqueId(), pick.id(), ap * GRID, GRID).whenComplete((items, failure) -> {
                if (failure != null || items == null) { failed(player); return; }
                show(coreUi, mail, player, View.of(loaded, pick, ap, items));
            });
        });
    }

    private static void failed(Player player) {
        if (player.isOnline()) Messages.send(player, "우편을 불러오지 못했어요. 잠시 후 다시 열어 주세요.");
    }

    private static void show(CoreUi coreUi, MailboxService mail, Player player, View view) {
        if (!player.isOnline()) return;
        coreUi.open(player, build(coreUi.getGlyphs().all(), view, new Actions() {
            public void select(Player p, MailEntry entry) { if (!BUSY.contains(p.getUniqueId())) load(coreUi, mail, p, view.page().page(), entry.id(), 0); }
            public void page(Player p, int page) { if (!BUSY.contains(p.getUniqueId())) load(coreUi, mail, p, page, null, 0); }
            public void attachPage(Player p, int page) { if (!BUSY.contains(p.getUniqueId())) load(coreUi, mail, p, view.page().page(), view.selected().id(), page); }
            public void claim(Player p, MailEntry entry) {
                if (!BUSY.add(p.getUniqueId())) return;
                mail.claim(p, entry.id()).whenComplete((result, error) -> {
                    BUSY.remove(p.getUniqueId());
                    if (error == null) Messages.send(p, mail.describe(result));
                    claimSound(p, error == null && result.status() == MailResult.Status.CLAIMED);
                    if (p.isOnline()) load(coreUi, mail, p, view.page().page(), null, 0);
                });
            }
            public void claimAll(Player p) {
                if (!BUSY.add(p.getUniqueId())) return;
                mail.claimAll(p).whenComplete((count, error) -> {
                    BUSY.remove(p.getUniqueId());
                    claimSound(p, error == null && count != null && count > 0);
                    if (p.isOnline()) load(coreUi, mail, p, 0, null, 0);
                });
            }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    interface Actions {
        void select(Player p, MailEntry entry);
        void page(Player p, int page);
        void attachPage(Player p, int page);
        void claim(Player p, MailEntry entry);
        void claimAll(Player p);
        void close(Player p);
    }

    /** One frame; pure apart from the callbacks, so offline checks can build it with sample data. */
    static CoreMenu build(Map<String, Glyph> g, View view, Actions act) {
        Canvas c = new Canvas(g);
        MailPage page = view.page();
        // Chrome: book, title tab (joined to the book), close.
        for (int col = 0; col < 2; col++) {
            c.image("dex/ui/page_0_" + col, col * 228, 20);
            c.image("dex/ui/page_1_" + col, col * 228, 153);
        }
        c.image("menu/ui/tab", 16, 0);
        c.image("menu/icon/post", 20, 2);
        c.text("우편함", 38, 6, WHITE);
        c.image("menu/ui/tab_join", 17, 20);
        c.image("dex/ui/close", 432, 0);
        c.click(432, 0, 20, 20, act::close, null);

        // Left page: the mail list.
        c.text("받은 우편", LEFT, TOP, INK);
        c.right("미수령 " + page.total() + "통", LEFT + PAGE, TOP, DIM);
        if (page.entries().isEmpty()) c.centered("받을 우편이 없어요", LEFT, PAGE, 120, DIM);
        for (int i = 0; i < view.rows().size() && i < ROWS; i++) {
            MailEntry e = view.rows().get(i).mail();
            Slot preview = view.rows().get(i).preview();
            int y = ROW_Y[i];
            boolean selected = view.selected() != null && view.selected().id().equals(e.id());
            c.image(selected ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            if (e.kind() == MailKind.BUNDLE) c.item("bundle", LEFT + 2, y + 2);
            else c.item(preview == null ? null : preview.key(), LEFT + 2, y + 2);
            if (e.kind() == MailKind.SINGLE && preview != null && preview.amount() > 1) c.count(preview.amount(), LEFT, y);
            boolean bundle = e.kind() == MailKind.BUNDLE;
            c.fitted(bundle && e.name() != null ? e.name() : "보낸이 " + e.sender(), LEFT + 25, y + 1, PAGE - 25, selected ? GREEN : INK);
            String sub = (bundle ? e.sender() + " · 첨부 " + e.attachments() + "개 · " : "") + SHORT.format(Instant.ofEpochMilli(e.created()));
            c.fitted(e.locked() ? sub + " · 처리 중" : sub, LEFT + 25, y + 11, PAGE - 25, e.locked() ? RED : DIM);
            net.kyori.adventure.text.event.HoverEventSource<?> hover = !bundle && preview != null ? preview.hover()
                    : HoverEvent.showText(Component.text((e.name() == null ? "묶음 우편" : e.name()) + "\n첨부 " + e.attachments() + "개 · 보낸이 " + e.sender()));
            c.click(LEFT, y, PAGE, 20, p -> act.select(p, e), hover);
        }
        int pages = page.pages();
        c.button("paper", 20, "<", LEFT, BUTTON_Y, INK, page.page() > 0 ? p -> act.page(p, page.page() - 1) : null);
        c.centered((page.page() + 1) + "/" + pages, LEFT + 20, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 48, BUTTON_Y, INK, page.page() + 1 < pages ? p -> act.page(p, page.page() + 1) : null);
        if (page.total() > 0) c.button("green", 60, "모두 받기", LEFT + PAGE - 60, BUTTON_Y, WHITE, act::claimAll);
        else c.button("off", 60, "모두 받기", LEFT + PAGE - 60, BUTTON_Y, DIM, null);

        // Right page: the selected mail.
        MailEntry e = view.selected();
        if (e == null) {
            c.centered("우편을 고르면 내용이 보여요", RIGHT, PAGE, 120, DIM);
        } else {
            boolean bundle = e.kind() == MailKind.BUNDLE;
            c.fitted(bundle && e.name() != null ? e.name() : "아이템 우편", RIGHT, TOP, PAGE - 50, INK);
            c.right(e.locked() ? "처리 중" : bundle ? "묶음" : "단일", RIGHT + PAGE, TOP, e.locked() ? RED : DIM);
            String[][] rows = {{"보낸이", e.sender()}, {"보낸 날짜", LONG.format(Instant.ofEpochMilli(e.created()))}, {"첨부", e.attachments() + "개"}};
            for (int i = 0; i < rows.length; i++) {
                c.text(rows[i][0], RIGHT, 45 + i * 12, DIM);
                c.fitted(rows[i][1], RIGHT + 48, 45 + i * 12, PAGE - 48, INK);
            }
            int attachPages = Math.max(1, (e.attachments() + GRID - 1) / GRID);
            c.text("첨부 아이템", RIGHT, 84, DIM);
            if (attachPages > 1) c.right((view.attachPage() + 1) + " / " + attachPages, RIGHT + PAGE, 84, DIM);
            for (int i = 0; i < GRID; i++) {
                int x = RIGHT + (i % 8) * 24, y = GRID_Y[i / 8];
                if (i >= view.attachments().size()) {
                    c.image("dex/ui/slot_locked", x, y);
                    continue;
                }
                Slot item = view.attachments().get(i);
                c.image("dex/ui/slot_normal", x, y);
                c.item(item.key(), x + 2, y + 2);
                if (item.amount() > 1) c.count(item.amount(), x, y);
                c.click(x, y, 20, 20, p -> { }, item.hover());
            }
            if (attachPages > 1) {
                c.button("paper", 20, "<", RIGHT, BUTTON_Y, INK, view.attachPage() > 0 ? p -> act.attachPage(p, view.attachPage() - 1) : null);
                c.button("paper", 20, ">", RIGHT + 24, BUTTON_Y, INK, view.attachPage() + 1 < attachPages ? p -> act.attachPage(p, view.attachPage() + 1) : null);
            }
            if (e.locked()) c.button("off", 60, "처리 중", RIGHT + PAGE - 60, BUTTON_Y, DIM, null);
            else c.button("green", 60, "받기", RIGHT + PAGE - 60, BUTTON_Y, WHITE, p -> act.claim(p, e));
        }
        return c.b.build();
    }

    private static final class Canvas {
        final Map<String, Glyph> g;
        final CoreMenuBuilder b = CoreMenu.builder("kokonut-mail").title(Component.text("우편함"))
                .theme(ThemeColour.DARK).noBackground().size(WIDTH, HEIGHT);
        int ids;

        Canvas(Map<String, Glyph> g) {
            this.g = g;
        }

        void image(String id, int x, int y) {
            b.glyph("img" + ids++, Objects.requireNonNull(g.get(id), "missing glyph " + id)).position(x, y).add();
        }

        /** Vanilla item icon at offset-2 position, or the "?" icon when there is none. */
        void item(String key, int x, int y) {
            Glyph icon = key == null ? null : g.get("item/" + key);
            if (icon != null && !icon.supportsOffset(Math.floorMod(y, 9))) icon = null;
            image(icon == null ? "menu/ui/unknown_item" : icon.id(), x, y);
        }

        /** Stack size in the slot's bottom-right corner, drawn on a later text line than the icon. */
        void count(int amount, int slotX, int slotY) {
            String value = amount > 999 ? "999+" : String.valueOf(amount);
            right(value, slotX + 20, slotY + 11, WHITE);
        }

        void click(int x, int y, int w, int h, Consumer<Player> onClick, net.kyori.adventure.text.event.HoverEventSource<?> hover) {
            var spot = b.hotspot("hit" + ids++).position(x, y).size(w, h).onClick(onClick);
            if (hover != null) spot.hover(hover);
            spot.add();
        }

        void text(String value, int x, int y, TextColor color) {
            Component text = Component.text(value, color);
            b.label("txt" + ids++, text).position(x, y).size(Math.max(1, Math.min(TextWidths.width(text), WIDTH - x)), 9).add();
        }

        /** Text cut with "…" to {@code max} px. Mail senders and bundle names come from other plugins and can be long. */
        void fitted(String value, int x, int y, int max, TextColor color) {
            String shown = value == null ? "" : value;
            if (TextWidths.width(Component.text(shown)) > max) {
                while (!shown.isEmpty() && TextWidths.width(Component.text(shown + "…")) > max) shown = shown.substring(0, shown.length() - 1);
                shown += "…";
            }
            text(shown, x, y, color);
        }

        void right(String value, int rightX, int y, TextColor color) {
            text(value, rightX - TextWidths.width(Component.text(value)), y, color);
        }

        void centered(String value, int x, int width, int y, TextColor color) {
            text(value, x + (width - TextWidths.width(Component.text(value))) / 2, y, color);
        }

        void button(String kind, int width, String label, int x, int y, TextColor color, Consumer<Player> onClick) {
            image("dex/ui/btn_" + kind + "_" + width, x, y);
            centered(label, x, width, y + 6, color);
            if (onClick != null) click(x, y, width, 20, onClick, null);
        }
    }
}
