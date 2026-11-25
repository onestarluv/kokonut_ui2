package org.crebs.coreui.menu.furniture;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.furniture.FurnitureCatalog.Type;
import org.crebs.coreui.menu.furniture.FurnitureCatalog.Wood;
import org.crebs.coreui.menu.vanilla.VanillaCanvas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static org.crebs.coreui.menu.vanilla.VanillaCanvas.HINT;
import static org.crebs.coreui.menu.vanilla.VanillaCanvas.LABEL;

/**
 * Furniture workbench: a stonecutter-like screen in the vanilla container style, opened by
 * {@code /coreui furniture [player]} — the Nexo furniture {@code tmf_worktable} runs that command on right-click.
 * <ul>
 *   <li>Left: the woods as slots (planks icons, four per row); the chosen one has a white frame.</li>
 *   <li>Middle: the 28 pieces of that wood, four per row with a scroll bar (click above or below the handle).</li>
 *   <li>Right: a 32 px picture of the chosen piece.</li>
 *   <li>Bottom: its name, the ingredients ({@link FurnitureCatalog#materials} plus kokonut) with the needed amount
 *       in each slot (red when the player has too few) and the 제작 button.</li>
 * </ul>
 * 제작 is real: {@code FurnitureCrafts} takes the kokonut through Kokonut-Core, the wood from the inventory and
 * gives the piece as its Nexo item. Only plain items count as ingredients (no custom name, enchantments, ...).
 * <p>Furniture pictures ({@code furniture/icon|preview/...}, tools/furniture_icons.py) and vanilla item icons have
 * one font provider each, so they sit at y % 9 == 2. The panel ({@code tools/dex_glyphs.py}) already contains the
 * wood slots, the recipe area, the scroll track and the preview well; coordinates here must match it.</p>
 */
public final class FurnitureMenu {
    static final String ID = "kokonut-furniture";
    static final int WIDTH = 226, HEIGHT = 135;
    static final String PANEL = "furniture/ui/panel_" + WIDTH;
    /** Top of the wood slots and of the recipe buttons; both grids use 18 px rows. */
    static final int GRID_Y = 19, ROW = 18;
    static final int WOOD_X = 8, WOOD_COLS = 4;
    static final int RECIPE_X = 88, RECIPE_W = 16, COLS = 4, ROWS = 4;
    static final int TRACK_X = 156, TRACK_H = ROWS * ROW, SCROLLER_W = 12, SCROLLER_H = 15;
    static final int WELL_X = 176, WELL_Y = 36, WELL = 36;
    static final int NAME_Y = 97, COST_X = 8, COST_Y = 109, COST_STEP = 20;
    static final int BUTTON_X = 168, BUTTON_Y = 108;
    /** "재료 부족" on the grey panel (no shadow there, so darker than the red of the slot counts). */
    static final TextColor SHORT = TextColor.color(0xA02020);

    /** What is chosen: the wood, the piece ({@code null} before the first pick) and the first visible recipe row. */
    public record State(Wood wood, Type type, int scroll) {
        public State {
            Objects.requireNonNull(wood);
        }
    }

    /** What the player holds: vanilla item key → amount, and kokonut ({@code -1} while the balance is unknown). */
    public record Stock(Map<String, Integer> items, long kokonut) {
        public int of(String item) {
            return items.getOrDefault(item, 0);
        }
    }

    public interface Actions {
        void wood(Player p, Wood wood);
        void pick(Player p, Type type);
        void scroll(Player p, int row);
        void craft(Player p, Wood wood, Type type);
    }

    private FurnitureMenu() {
    }

    /** False while the glyphs are not loaded (pack still generating or an older pack on the server). */
    public static boolean open(CoreUi coreUi, Player player) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey(PANEL) || !g.containsKey("furniture/icon/oak_stool")
                || !g.containsKey("furniture/preview/oak_stool") || !g.containsKey("menu/icon/kokonut")) return false;
        player.playSound(player, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.6f, 1.1f);
        show(coreUi, player, new State(Wood.OAK, null, 0));
        return true;
    }

    private static void show(CoreUi coreUi, Player player, State state) {
        if (!player.isOnline()) return;
        coreUi.open(player, build(coreUi.getGlyphs().all(), state, stock(coreUi, player, state.wood()), new Actions() {
            public void wood(Player p, Wood wood) { show(coreUi, p, new State(wood, state.type(), state.scroll())); }
            public void pick(Player p, Type type) { show(coreUi, p, new State(state.wood(), type, state.scroll())); }
            public void scroll(Player p, int row) { show(coreUi, p, new State(state.wood(), state.type(), row)); }
            public void craft(Player p, Wood wood, Type type) { FurnitureMenu.craft(coreUi, p, wood, type, state); }
        }));
    }

    /** Real craft through Kokonut-Core and Nexo ({@code FurnitureCrafts}); redraws with fresh amounts if still open. */
    private static void craft(CoreUi coreUi, Player player, Wood wood, Type type, State state) {
        if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) {
            Messages.send(player, "재화 플러그인이 꺼져 있어서 지금은 제작할 수 없어요.");
            return;
        }
        try {
            org.crebs.coreui.menu.kokonut.FurnitureCrafts.craft(coreUi, player, wood, type, () -> {
                if (player.isOnline() && coreUi.getSession(player).map(s -> s.getMenu().getId().equals(ID)).orElse(false)) {
                    show(coreUi, player, state);
                }
            });
        } catch (LinkageError olderCore) {
            Messages.send(player, "재화 플러그인 버전이 맞지 않아 제작할 수 없어요.");
        }
    }

    /** Counts the wood's plain ingredients in the storage slots and reads the kokonut balance from Core's snapshot. */
    private static Stock stock(CoreUi coreUi, Player player, Wood wood) {
        Map<String, Integer> items = new HashMap<>();
        for (String key : List.of(wood.planks(), wood.log, FurnitureCatalog.STICK)) {
            Material material = Material.matchMaterial(key);
            int amount = 0;
            if (material != null) {
                ItemStack plain = new ItemStack(material);
                for (ItemStack item : player.getInventory().getStorageContents()) {
                    if (item != null && item.isSimilar(plain)) amount += item.getAmount();
                }
            }
            items.put(key, amount);
        }
        return new Stock(items, kokonut(coreUi, player));
    }

    private static long kokonut(CoreUi coreUi, Player player) {
        if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) return -1;
        try {
            return org.crebs.coreui.menu.kokonut.CoreBalance.of(player.getUniqueId())
                    .map(org.crebs.coreui.menu.kokonut.CoreBalance.Balance::kokonut).orElse(-1L);
        } catch (LinkageError olderCore) {
            return -1;
        }
    }

    static boolean affordable(Wood wood, Type type, Stock stock) {
        for (FurnitureCatalog.Material m : FurnitureCatalog.materials(wood, type)) {
            if (stock.of(m.item()) < m.amount()) return false;
        }
        return stock.kokonut() >= type.kokonut;
    }

    /** Rows the recipe list can be scrolled down by. */
    static int maxScroll() {
        return Math.max(0, (Type.values().length + COLS - 1) / COLS - ROWS);
    }

    /** One frame; pure apart from the callbacks, so tests and the preview tool can build it. */
    public static CoreMenu build(Map<String, Glyph> g, State state, Stock stock, Actions act) {
        VanillaCanvas c = new VanillaCanvas(g, ID, "가구 제작대", WIDTH, HEIGHT);
        Wood wood = state.wood();
        Type chosen = state.type();
        c.image(PANEL, 0, 0);
        c.text("가구 제작대", 8, 6, LABEL);

        // Woods: slots baked into the panel (one per wood); the chosen one gets a white frame over its icon (1 px outside the slot).
        Wood[] woods = Wood.values();
        for (int i = 0; i < woods.length; i++) {
            Wood w = woods[i];
            int x = WOOD_X + i % WOOD_COLS * ROW, y = GRID_Y + i / WOOD_COLS * ROW;
            c.item(w.planks(), x + 1, y + 1);
            if (w == wood) c.image("furniture/ui/wood_on", x - 1, y - 1);
            c.click(x, y, ROW, ROW, w == wood ? p -> { } : p -> act.wood(p, w), tip(w.label + " 가구"));
        }

        // Recipes of the chosen wood: 4 x 4 buttons from the scrolled row.
        Type[] types = Type.values();
        int maxScroll = maxScroll(), scroll = Math.clamp(state.scroll(), 0, maxScroll);
        for (int i = scroll * COLS; i < types.length && i < (scroll + ROWS) * COLS; i++) {
            Type type = types[i];
            int x = RECIPE_X + i % COLS * RECIPE_W, y = GRID_Y + (i / COLS - scroll) * ROW;
            c.image(type == chosen ? "furniture/ui/recipe_on" : "furniture/ui/recipe", x, y);
            c.image("furniture/icon/" + FurnitureCatalog.art(wood, type), x, y + 1);
            c.click(x, y, RECIPE_W, ROW, type == chosen ? p -> { } : p -> act.pick(p, type), recipeTip(wood, type));
        }
        // Scroll bar: the handle shows the position; clicking the track above or below it moves a page.
        int handleY = GRID_Y + (maxScroll == 0 ? 0 : Math.round((TRACK_H - SCROLLER_H) * scroll / (float) maxScroll));
        c.image("furniture/ui/scroller", TRACK_X, handleY);
        if (scroll > 0) {
            c.click(TRACK_X, GRID_Y, SCROLLER_W, handleY - GRID_Y, p -> act.scroll(p, Math.max(0, scroll - ROWS)), tip("위로"));
        }
        if (scroll < maxScroll) {
            int below = handleY + SCROLLER_H;
            c.click(TRACK_X, below, SCROLLER_W, GRID_Y + TRACK_H - below, p -> act.scroll(p, Math.min(maxScroll, scroll + ROWS)), tip("아래로"));
        }

        if (chosen == null) {
            c.text("만들 가구를 골라 주세요", 8, NAME_Y, HINT);
            button(c, null, "먼저 만들 가구를 골라 주세요");
            return c.b.build();
        }

        // The chosen piece: picture, name, ingredient slots with the needed amount, 제작.
        c.image("furniture/preview/" + FurnitureCatalog.art(wood, chosen), WELL_X + 2, WELL_Y + 2);
        c.click(WELL_X, WELL_Y, WELL, WELL, p -> { }, recipeTip(wood, chosen));
        c.text(FurnitureCatalog.name(wood, chosen), 8, NAME_Y, LABEL);
        int x = COST_X;
        for (FurnitureCatalog.Material m : FurnitureCatalog.materials(wood, chosen)) {
            int have = stock.of(m.item());
            c.image("furniture/ui/slot", x, COST_Y);
            c.item(m.item(), x + 1, COST_Y + 1);
            c.count(m.amount(), x, COST_Y, have >= m.amount());
            c.click(x, COST_Y, ROW, ROW, p -> { }, tip(m.name() + " " + m.amount() + "개 필요 · 보유 " + have + "개"));
            x += COST_STEP;
        }
        boolean paid = stock.kokonut() >= chosen.kokonut;
        c.image("furniture/ui/slot", x, COST_Y);
        c.image("menu/icon/kokonut", x + 1, COST_Y + 1);
        c.count(chosen.kokonut, x, COST_Y, paid);
        c.click(x, COST_Y, ROW, ROW, p -> { }, tip("코코넛 " + chosen.kokonut + "개 필요 · "
                + (stock.kokonut() < 0 ? "보유량 확인 중" : "보유 " + stock.kokonut() + "개")));

        if (affordable(wood, chosen, stock)) {
            button(c, p -> act.craft(p, wood, chosen), FurnitureCatalog.name(wood, chosen) + " 제작");
        } else {
            c.right("재료 부족", WIDTH - 8, NAME_Y, SHORT);
            button(c, null, "재료나 코코넛이 부족해요");
        }
        return c.b.build();
    }

    /** The 제작 button; dark and without effect when {@code onClick} is null. */
    private static void button(VanillaCanvas c, Consumer<Player> onClick, String hover) {
        c.button("제작", BUTTON_X, BUTTON_Y, onClick, tip(hover));
    }

    private static HoverEvent<Component> tip(String text) {
        return HoverEvent.showText(Component.text(text));
    }

    /** "참나무 긴 탁자" over its cost line, like an item tooltip. */
    private static HoverEvent<Component> recipeTip(Wood wood, Type type) {
        StringBuilder cost = new StringBuilder();
        for (FurnitureCatalog.Material m : FurnitureCatalog.materials(wood, type)) {
            cost.append(m.name()).append(' ').append(m.amount()).append(" · ");
        }
        cost.append("코코넛 ").append(type.kokonut);
        return HoverEvent.showText(Component.text(FurnitureCatalog.name(wood, type)).append(Component.newline())
                .append(Component.text(cost.toString(), TextColor.color(0xAAAAAA))));
    }
}
