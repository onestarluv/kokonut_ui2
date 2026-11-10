package org.crebs.coreui.menu.shop;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.CoreMenuBuilder;
import org.crebs.coreui.menu.kokonut.TitleSearch;
import org.crebs.coreui.menu.render.TextWidths;
import org.crebs.coreui.menu.shop.ShopCatalog.Currency;
import org.crebs.coreui.menu.shop.ShopCatalog.Mode;
import org.crebs.coreui.menu.shop.ShopCatalog.Product;
import org.crebs.coreui.menu.shop.ShopCatalog.Wallet;
import org.crebs.coreui.menu.style.ThemeColour;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Shop prototype laid out like the title screen ({@code TitleMenu}): book, title tab joined to the book, close
 * button; left page = search field + 7 product rows + pager, right page = the selected product with quantity,
 * total, wallet and the buy/sell button. A shop is either buy-only or sell-only ({@link Mode}); there are no
 * categories, search narrows the one list (name, Hangul initials, description; see {@link TitleSearch}).
 * Every click rebuilds the frame from an immutable {@link State}.
 *
 * Trades run through Kokonut-Core ({@code ShopTrades}: async currency calls, Core re-checks the balance). The
 * balances shown come from Core's non-blocking {@code CurrencyUtil.snapshot}, which is for display only. Vanilla item
 * icons only exist at text-line offset 2, so every icon sits 2 px below a 9 px line.
 */
public final class ShopMenu {
    static final String ID = "kokonut-shop";
    static final int WIDTH = 480, HEIGHT = 292, ROWS = 7, MAX_QUANTITY = 64;
    private static final int LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258, SEARCH_W = 176;
    private static final int[] ROW_Y = {72, 99, 126, 153, 180, 207, 234};
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), RED = TextColor.color(0xA83A2A), GREEN = TextColor.color(0x3E7A22),
            WHITE = TextColor.color(0xFFFFFF), PAPER = TextColor.color(0xF6ECD0), HINT = TextColor.color(0xA8946A);

    record State(String query, int page, String selected, int quantity) {
        static final State START = new State("", 0, null, 1);

        State page(int p) { return new State(query, p, null, 1); }
        State select(Product p) { return new State(query, page, p.id(), p.id().equals(selected) ? quantity : 1); }
        State quantity(int q) { return new State(query, page, selected, Math.clamp(q, 1, MAX_QUANTITY)); }
        State search(String q) { return new State(q, 0, null, 1); }
    }

    interface Actions {
        void go(Player p, State next);
        void search(Player p);
        void trade(Player p, Product product, int quantity);
        void close(Player p);
    }

    private ShopMenu() {
    }

    /** Returns false while the shop glyphs are not loaded (pack still generating or an older pack on the server). */
    public static boolean open(CoreUi coreUi, Player player, ShopCatalog shop) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("dex/ui/page_0_0") || !g.containsKey("menu/ui/field_176") || !g.containsKey("shop/ui/chip_64")
                || !g.containsKey("menu/icon/gold") || !g.containsKey("dex/ui/btn_green_98")) return false;
        player.playSound(player, Sound.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.5f, 1f);
        show(coreUi, player, shop, State.START);
        return true;
    }

    private static void show(CoreUi coreUi, Player player, ShopCatalog shop, State state) {
        if (!player.isOnline()) return;
        Map<String, Integer> held = shop.mode() == Mode.SELL ? held(player, shop) : Map.of();
        coreUi.open(player, build(coreUi.getGlyphs().all(), shop, state, wallet(coreUi, player), held, new Actions() {
            public void go(Player p, State next) { show(coreUi, p, shop, next); }
            public void search(Player p) { askQuery(coreUi, p, shop, state); }
            public void trade(Player p, Product product, int quantity) { ShopMenu.trade(coreUi, p, shop, product, quantity, state); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    private static Wallet wallet(CoreUi coreUi, Player player) {
        if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) return null;
        try {
            return org.crebs.coreui.menu.kokonut.CoreBalance.wallet(player.getUniqueId()).orElse(null);
        } catch (LinkageError olderCore) {
            return null;
        }
    }

    /** How many plain (no custom meta) items of each product the player carries; a sell shop only takes those. */
    private static Map<String, Integer> held(Player player, ShopCatalog shop) {
        Map<String, Integer> held = new HashMap<>();
        for (Product p : shop.products()) {
            Material type = p.material();
            if (type == null || !type.isItem()) continue;
            ItemStack plain = new ItemStack(type);
            int count = 0;
            for (ItemStack item : player.getInventory().getStorageContents())
                if (item != null && item.isSimilar(plain)) count += item.getAmount();
            held.put(p.id(), count);
        }
        return held;
    }

    /** Native dialog with a text field, as on the title screen; "검색" reopens the list filtered. */
    private static void askQuery(CoreUi coreUi, Player player, ShopCatalog shop, State state) {
        coreUi.removeSession(player);
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton search = ActionButton.builder(Component.text("검색")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi, () -> {
                    String query = Objects.requireNonNullElse(response.getText("query"), "").strip();
                    show(coreUi, player, shop, state.search(query));
                }), once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("취소")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi,
                        () -> show(coreUi, player, shop, state)), once)).build();
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("상품 검색"))
                        .canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "상품 이름이나 초성, 설명으로 찾을 수 있어요.\n예) 벽돌 · ㅂㄷ — 비우고 검색하면 전체 목록"), 240)))
                        .inputs(List.of(DialogInput.text("query", Component.text("검색어"))
                                .initial(state.query()).maxLength(32).width(200).build()))
                        .build())
                .type(DialogType.confirmation(search, cancel))));
    }

    private static void onMain(CoreUi coreUi, Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(coreUi, task);
    }

    /** Real trade through Kokonut-Core ({@code ShopTrades}); redraws with fresh balances if the shop is still open. */
    private static void trade(CoreUi coreUi, Player player, ShopCatalog shop, Product product, int units, State state) {
        if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) {
            Messages.send(player, "재화 플러그인이 꺼져 있어서 지금은 거래할 수 없어요.");
            return;
        }
        try {
            org.crebs.coreui.menu.kokonut.ShopTrades.trade(coreUi, player, shop, product, units, () -> {
                if (stillOpen(coreUi, player)) show(coreUi, player, shop, state);
            });
        } catch (LinkageError olderCore) {
            Messages.send(player, "재화 플러그인 버전이 맞지 않아 거래할 수 없어요.");
        }
    }

    private static boolean stillOpen(CoreUi coreUi, Player player) {
        return player.isOnline() && coreUi.getSession(player).map(s -> s.getMenu().getId().equals(ID)).orElse(false);
    }

    /**
     * One frame; pure apart from the callbacks, so offline checks and the preview tool can build it with sample data.
     * {@code wallet} is null while Core's snapshot is loading (or Core is off); {@code held} is only used by sell shops.
     */
    static CoreMenu build(Map<String, Glyph> g, ShopCatalog shop, State state, Wallet wallet, Map<String, Integer> held, Actions act) {
        Canvas c = new Canvas(g);
        boolean sell = shop.mode() == Mode.SELL;
        List<Product> all = shop.products();
        List<Product> shown = TitleSearch.filter(all, state.query(),
                p -> new TitleSearch.Target(-1, p.name(), String.join(" ", p.description())));
        int pages = Math.max(1, (shown.size() + ROWS - 1) / ROWS), page = Math.clamp(state.page(), 0, pages - 1);
        List<Product> rows = shown.subList(Math.min(shown.size(), page * ROWS), Math.min(shown.size(), (page + 1) * ROWS));
        Product selected = shown.stream().filter(p -> p.id().equals(state.selected())).findFirst()
                .orElse(rows.isEmpty() ? null : rows.getFirst());

        // Chrome: book, title tab (joined to the book), close — as on the mailbox and title screens.
        for (int col = 0; col < 2; col++) {
            c.image("dex/ui/page_0_" + col, col * 228, 20);
            c.image("dex/ui/page_1_" + col, col * 228, 153);
        }
        c.image("menu/ui/tab", 16, 0);
        c.item(shop.mode().icon, 20, 2);
        c.text(shop.mode().title, 38, 6, WHITE);
        c.image("menu/ui/tab_join", 17, 20);
        c.image("dex/ui/close", 432, 0);
        c.click(432, 0, 20, 20, act::close, null);

        // Left page: shop name, search field, list, pager.
        boolean searching = !state.query().isEmpty();
        c.fitted(shop.name(), LEFT, TOP, PAGE - 60, INK);
        c.right(searching ? shown.size() + " / " + all.size() + "개" : all.size() + "개", LEFT + PAGE, TOP, DIM);
        c.image("menu/ui/field_176", LEFT, 44);
        if (searching) c.fitted(state.query(), LEFT + 5, 49, SEARCH_W - 10, PAPER);
        else c.text("이름 · 초성으로 검색", LEFT + 5, 49, HINT);
        c.click(LEFT, 44, SEARCH_W, 18, act::search, HoverEvent.showText(Component.text("눌러서 검색어 입력")));
        if (searching) c.button("paper", 20, "x", LEFT + PAGE - 20, 43, INK, p -> act.go(p, state.search("")));

        if (all.isEmpty()) c.centered("준비된 상품이 없어요", LEFT, PAGE, 140, DIM);
        else if (shown.isEmpty()) {
            c.centered("검색 결과가 없어요", LEFT, PAGE, 137, DIM);
            c.centered("다른 이름이나 초성으로 찾아보세요", LEFT, PAGE, 160, DIM);
        }
        for (int i = 0; i < rows.size(); i++) {
            Product p = rows.get(i);
            int y = ROW_Y[i];
            boolean picked = selected != null && selected.id().equals(p.id());
            c.image(picked ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            c.item(p.item(), LEFT + 2, y + 2);
            // Price on the right with the 16 px currency icon (on the row's own text line). Buy rows: the name alone,
            // centred; sell rows: the name and how many the player carries.
            int have = held.getOrDefault(p.id(), 0);
            boolean shortOf = sell ? have < 1 : wallet != null && wallet.of(p.currency()) < p.price();
            int priceW = c.priceRight(p.currency(), p.price(), LEFT + PAGE, y + 2, !sell && shortOf ? RED : INK);
            if (sell) {
                c.fitted(p.name(), LEFT + 25, y + 1, PAGE - 29 - priceW, picked ? GREEN : INK);
                c.fitted("보유 " + have + "개", LEFT + 25, y + 11, PAGE - 29 - priceW, shortOf ? RED : DIM);
            } else c.fitted(p.name(), LEFT + 25, y + 6, PAGE - 29 - priceW, picked ? GREEN : INK);
            c.click(LEFT, y, PAGE, 20, pl -> act.go(pl, state.select(p)),
                    HoverEvent.showText(Component.text(p.name() + " · 1개 " + p.currency().format(p.price()) + " " + p.currency().label)));
        }
        // Pager where the title screen has it.
        c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, page > 0 ? pl -> act.go(pl, state.page(page - 1)) : null);
        c.centered((page + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, page + 1 < pages ? pl -> act.go(pl, state.page(page + 1)) : null);

        // Right page: the selected product.
        if (selected == null) {
            c.centered("상품을 고르면 내용이 보여요", RIGHT, PAGE, 140, DIM);
            return c.b.build();
        }
        int have = held.getOrDefault(selected.id(), 0);
        c.fitted(selected.name(), RIGHT, TOP, PAGE - 50, INK);
        c.right(selected.currency().label + " " + shop.mode().verb, RIGHT + PAGE, TOP, DIM);
        // Label text stays off a line's last pixel row (y % 9 == 8): the client clips labels placed there.
        c.image("menu/ui/well", RIGHT, 45);
        c.item(selected.item(), RIGHT + 10, 56);
        c.text(sell ? "1개 매입가" : "1개 가격", RIGHT + 44, 49, DIM);
        c.priceRight(selected.currency(), selected.price(), RIGHT + PAGE, 45, INK);
        c.text(sell ? "보유" : "재고", RIGHT + 44, 67, DIM);
        if (sell) c.right(have + "개", RIGHT + PAGE, 67, have < 1 ? RED : INK);
        else c.right("제한 없음", RIGHT + PAGE, 67, INK);
        for (int i = 0; i < selected.description().size() && i < 2; i++)
            c.fitted(selected.description().get(i), RIGHT, 92 + i * 11, PAGE, BODY);

        // Quantity = item count: -10 · -1 · [n] · +1 · +10. A sell shop cannot go past what the player carries.
        int max = sell ? Math.clamp(have, 1, MAX_QUANTITY) : MAX_QUANTITY;
        int q = Math.min(state.quantity(), max), y = 126;
        c.image("shop/ui/divider_200", RIGHT, 121);
        c.text("수량", RIGHT, y + 6, DIM);
        int x = RIGHT + PAGE - 140;
        c.button("paper", 24, "-10", x, y, INK, q > 1 ? pl -> act.go(pl, state.quantity(q - 10)) : null);
        c.button("paper", 20, "-", x + 28, y, INK, q > 1 ? pl -> act.go(pl, state.quantity(q - 1)) : null);
        c.image("shop/ui/qty_36", x + 52, y);
        c.centered(String.valueOf(q), x + 52, 36, y + 6, INK);
        c.button("paper", 20, "+", x + 92, y, INK, q < max ? pl -> act.go(pl, state.quantity(Math.min(max, q + 1))) : null);
        c.button("paper", 24, "+10", x + 116, y, INK, q < max ? pl -> act.go(pl, state.quantity(Math.min(max, q + 10))) : null);

        double total = selected.price() * q;
        double balance = wallet == null ? Double.NaN : wallet.of(selected.currency());
        boolean ok = sell ? have >= q : wallet != null && balance >= total;
        c.text(sell ? "받는 금액" : "합계", RIGHT, 158, DIM);
        c.priceRight(selected.currency(), total, RIGHT + PAGE, 154, !sell && wallet != null && !ok ? RED : sell ? GREEN : INK);
        c.text(sell ? "판매 후 잔액" : "구매 후 잔액", RIGHT, 176, DIM);
        if (wallet == null) c.right("확인 중", RIGHT + PAGE, 176, DIM);
        else if (sell) c.priceRight(selected.currency(), balance + total, RIGHT + PAGE, 172, INK);
        else if (ok) c.priceRight(selected.currency(), balance - total, RIGHT + PAGE, 172, INK);
        else c.right(selected.currency().format(total - balance) + " " + selected.currency().label + " 부족", RIGHT + PAGE, 176, RED);

        // Wallet: the three balances on chips (the main menu's chip colour); the product's currency in INK.
        c.image("shop/ui/divider_200", RIGHT, 197);
        c.text("내 지갑", RIGHT, 203, DIM);
        Currency[] currencies = Currency.values();
        for (int i = 0; i < currencies.length; i++) {
            int cx = RIGHT + i * 68;
            c.image("shop/ui/chip_64", cx, 216);
            c.image(currencies[i].icon, cx, 216);
            String value = wallet == null ? "-" : currencies[i].format(wallet.of(currencies[i]));
            c.fitted(value, cx + 18, 220, 44, currencies[i] == selected.currency() ? INK : DIM);
        }

        // Buy shops: one 100 px button. Sell shops: "전체 판매" and "판매하기" side by side, 98 px each, where
        // "전체 판매" sells every plain item of this product the player carries (not limited to the quantity field).
        int bw = sell ? 98 : 100, bx = RIGHT + PAGE - bw;
        if (!sell && wallet == null) c.button("off", bw, "잔액 확인 중", bx, BUTTON_Y, DIM, null);
        else if (!ok) c.button("off", bw, sell ? "보유 부족" : "잔액 부족", bx, BUTTON_Y, RED, null);
        else c.button("green", bw, shop.mode().button, bx, BUTTON_Y, WHITE, pl -> act.trade(pl, selected, q));
        if (sell) {
            if (have < 1) c.button("off", bw, "전체 판매", RIGHT, BUTTON_Y, DIM, null);
            else {
                c.button("green", bw, "전체 판매", RIGHT, BUTTON_Y, WHITE, null);
                c.click(RIGHT, BUTTON_Y, bw, 20, pl -> act.trade(pl, selected, have), HoverEvent.showText(Component.text(
                        "보유한 " + selected.name() + " " + have + "개 모두 판매\n+" + selected.currency().format(selected.price() * have)
                                + " " + selected.currency().label)));
            }
        }
        return c.b.build();
    }

    /** One frame under construction; the mailbox canvas helpers plus prices with a 16 px currency icon. */
    private static final class Canvas {
        final Map<String, Glyph> g;
        final CoreMenuBuilder b = CoreMenu.builder(ID).title(Component.text("상점"))
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

        /** 16 px currency icon with its top at {@code iconY}, the amount beside it (text 4 px lower); returns the width. */
        int priceRight(Currency currency, double amount, int rightX, int iconY, TextColor color) {
            String value = currency.format(amount);
            int width = 18 + TextWidths.width(Component.text(value));
            image(currency.icon, rightX - width, iconY);
            text(value, rightX - width + 18, iconY + 4, color);
            return width;
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
