package org.crebs.coreui.menu.furniture;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.element.GlyphElement;
import org.crebs.coreui.menu.element.HotspotElement;
import org.crebs.coreui.menu.element.LabelElement;
import org.crebs.coreui.menu.furniture.FurnitureCatalog.Type;
import org.crebs.coreui.menu.furniture.FurnitureCatalog.Wood;
import org.crebs.coreui.menu.furniture.FurnitureMenu.State;
import org.crebs.coreui.menu.furniture.FurnitureMenu.Stock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Frames of the furniture workbench, built offline with stand-in glyphs. */
class FurnitureMenuTest {
    /** Furniture pictures and vanilla item icons only have the font provider for offset 2, like the real pack. */
    private static final Map<String, Glyph> GLYPHS = new HashMap<>() {
        @Override public Glyph get(Object key) {
            String id = (String) key;
            boolean single = id.startsWith("item/") || id.startsWith("furniture/icon/") || id.startsWith("furniture/preview/");
            return new Glyph(id, 16, 16, 8, 16, single ? 1 << 2 : Glyph.ALL_OFFSETS);
        }
        @Override public boolean containsKey(Object key) { return true; }
    };
    private static final Player PLAYER = (Player) Proxy.newProxyInstance(FurnitureMenuTest.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> null);
    private static final Stock RICH = new Stock(Map.of("oak_planks", 64, "oak_log", 64, "stick", 64), 10);
    private static final Stock EMPTY = new Stock(Map.of(), 0);

    private static final class Log implements FurnitureMenu.Actions {
        final List<String> calls = new ArrayList<>();
        public void wood(Player p, Wood wood) { calls.add("wood " + wood); }
        public void pick(Player p, Type type) { calls.add("pick " + type); }
        public void scroll(Player p, int row) { calls.add("scroll " + row); }
        public void craft(Player p, Wood wood, Type type) { calls.add("craft " + wood + " " + type); }
    }

    private static List<String> texts(CoreMenu menu) {
        return menu.getElements().stream().filter(LabelElement.class::isInstance)
                .map(e -> PlainTextComponentSerializer.plainText().serialize(e.properties().label())).toList();
    }

    private static List<String> images(CoreMenu menu) {
        return menu.getElements().stream().filter(GlyphElement.class::isInstance).map(e -> ((GlyphElement) e).glyph().id()).toList();
    }

    /** Colours of the labels showing {@code text}, in paint order (a shadowed label is its shadow, then the text). */
    private static List<Integer> colors(CoreMenu menu, String text) {
        return menu.getElements().stream().filter(LabelElement.class::isInstance).map(e -> e.properties().label())
                .filter(label -> PlainTextComponentSerializer.plainText().serialize(label).equals(text))
                .map(label -> label.color().value()).toList();
    }

    private static void click(CoreMenu menu, int x, int y) {
        for (var element : menu.getElements()) {
            if (!(element instanceof HotspotElement spot)) continue;
            var b = spot.properties().bounds();
            if (x >= b.x() && x < b.x() + b.width() && y >= b.y() && y < b.y() + b.height()) {
                spot.onClick().accept(PLAYER);
                return;
            }
        }
        fail("no hotspot at " + x + "," + y);
    }

    /** Centre of the recipe button in grid cell (col, row). */
    private static int[] recipe(int col, int row) {
        return new int[]{FurnitureMenu.RECIPE_X + col * FurnitureMenu.RECIPE_W + 8, FurnitureMenu.GRID_Y + row * FurnitureMenu.ROW + 9};
    }

    private static final int[] CRAFT = {FurnitureMenu.BUTTON_X + 25, FurnitureMenu.BUTTON_Y + 10};

    @Test
    void freshScreenListsThirteenWoodsAndSixteenRecipes() {
        Log log = new Log();
        CoreMenu menu = FurnitureMenu.build(GLYPHS, new State(Wood.OAK, null, 0), EMPTY, log);
        List<String> images = images(menu);
        assertEquals(FurnitureMenu.PANEL, images.getFirst());
        assertEquals(13, images.stream().filter(id -> id.startsWith("item/") && id.endsWith("_planks")).count());
        assertEquals(1, images.stream().filter("furniture/ui/wood_on"::equals).count());
        assertEquals(16, images.stream().filter("furniture/ui/recipe"::equals).count());
        assertEquals(16, images.stream().filter(id -> id.startsWith("furniture/icon/oak_")).count());
        assertTrue(images.contains("furniture/ui/button_50_off"));
        assertTrue(images.stream().noneMatch(id -> id.startsWith("furniture/preview/")));
        assertTrue(texts(menu).containsAll(List.of("가구 제작대", "만들 가구를 골라 주세요", "제작")));

        click(menu, CRAFT[0], CRAFT[1]);
        assertEquals(List.of(), log.calls, "제작 does nothing before a piece is chosen");
        click(menu, recipe(1, 0)[0], recipe(1, 0)[1]);
        click(menu, FurnitureMenu.WOOD_X + 18 + 9, FurnitureMenu.GRID_Y + 9);
        click(menu, FurnitureMenu.WOOD_X + 9, FurnitureMenu.GRID_Y + 9);
        assertEquals(List.of("pick MODERN_STOOL", "wood SPRUCE"), log.calls, "the chosen wood's own slot does nothing");
    }

    @Test
    void chosenPieceShowsItsPictureNameAndCost() {
        Log log = new Log();
        CoreMenu menu = FurnitureMenu.build(GLYPHS, new State(Wood.DARK_OAK, Type.FANCY_LONG_TABLE, 0),
                new Stock(Map.of("dark_oak_planks", 6, "dark_oak_log", 2), 3), log);
        List<String> images = images(menu);
        assertTrue(images.containsAll(List.of("furniture/preview/dark_oak_fancy_long_table", "furniture/ui/recipe_on",
                "item/dark_oak_log", "menu/icon/kokonut", "furniture/ui/button_50")));
        assertEquals(15, images.stream().filter("furniture/ui/recipe"::equals).count());
        assertEquals(3, images.stream().filter("furniture/ui/slot"::equals).count(), "planks, logs, kokonut");
        assertFalse(images.contains("item/stick"));
        assertTrue(texts(menu).containsAll(List.of("짙은 참나무 고급 긴 탁자", "6", "2", "3")));
        assertFalse(texts(menu).contains("재료 부족"));
        assertEquals(List.of(0x3F3F3F, 0xFFFFFF), colors(menu, "6"), "enough: white with a shadow");
        click(menu, CRAFT[0], CRAFT[1]);
        assertEquals(List.of("craft DARK_OAK FANCY_LONG_TABLE"), log.calls);
    }

    @Test
    void missingIngredientsTurnRedAndBlockCrafting() {
        Log log = new Log();
        CoreMenu menu = FurnitureMenu.build(GLYPHS, new State(Wood.OAK, Type.LONG_TABLE, 0),
                new Stock(Map.of("oak_planks", 6, "stick", 3), 2), log);
        assertTrue(texts(menu).containsAll(List.of("참나무 긴 탁자", "재료 부족")));
        assertTrue(images(menu).contains("furniture/ui/button_50_off"));
        assertEquals(List.of(0x3F3F3F, 0xFFFFFF), colors(menu, "6"), "planks 6/6");
        assertEquals(List.of(0x3F1515, 0xFF5555), colors(menu, "4"), "sticks 3/4");
        assertEquals(List.of(0x3F1515, 0xFF5555), colors(menu, "3"), "kokonut 2/3");
        click(menu, CRAFT[0], CRAFT[1]);
        assertEquals(List.of(), log.calls);
        assertFalse(FurnitureMenu.affordable(Wood.OAK, Type.LONG_TABLE, new Stock(Map.of("oak_planks", 64, "stick", 64), -1)),
                "an unknown kokonut balance cannot pay");
    }

    @Test
    void seatsAndStorageCostFiveKokonutTheRestThree() {
        for (Type type : Type.values()) {
            assertEquals(type.use == FurnitureCatalog.Use.NONE ? 3 : 5, type.kokonut, type.key);
        }
        assertEquals(8, java.util.Arrays.stream(Type.values()).filter(t -> t.use == FurnitureCatalog.Use.SEAT).count());
        assertEquals(9, java.util.Arrays.stream(Type.values()).filter(t -> t.use == FurnitureCatalog.Use.STORAGE).count());
        assertEquals(List.of(5, 5, 5, 5, 3, 3), List.of(Type.CHAIR.kokonut, Type.LONG_BENCH.kokonut, Type.BIG_CRATE.kokonut,
                Type.MODERN_SHELVING.kokonut, Type.FANCY_BIG_TABLE.kokonut, Type.DESKTOP_CORNER.kokonut));

        Stock four = new Stock(Map.of("oak_planks", 64, "stick", 64), 4);
        assertTrue(FurnitureMenu.affordable(Wood.OAK, Type.TABLE, four));
        assertFalse(FurnitureMenu.affordable(Wood.OAK, Type.CHAIR, four));
        Log log = new Log();
        CoreMenu menu = FurnitureMenu.build(GLYPHS, new State(Wood.OAK, Type.CHAIR, 0), four, log);
        assertEquals(List.of(0x3F1515, 0xFF5555), colors(menu, "5"), "kokonut 4/5");
        assertTrue(texts(menu).contains("재료 부족"));
        click(menu, CRAFT[0], CRAFT[1]);
        assertEquals(List.of(), log.calls);
    }

    @Test
    void scrollBarPagesThroughAllTwentyEightPieces() {
        assertEquals(3, FurnitureMenu.maxScroll());
        Log log = new Log();
        int x = FurnitureMenu.TRACK_X + 6, top = FurnitureMenu.GRID_Y + 2, bottom = FurnitureMenu.GRID_Y + FurnitureMenu.TRACK_H - 2;
        CoreMenu first = FurnitureMenu.build(GLYPHS, new State(Wood.OAK, null, 0), RICH, log);
        assertTrue(images(first).contains("furniture/icon/oak_stool"));
        assertFalse(images(first).contains("furniture/icon/oak_long_modern_shelving"));
        click(first, x, bottom);
        CoreMenu last = FurnitureMenu.build(GLYPHS, new State(Wood.OAK, null, 3), RICH, log);
        assertTrue(images(last).contains("furniture/icon/oak_long_modern_shelving"));
        assertFalse(images(last).contains("furniture/icon/oak_stool"));
        assertEquals(16, images(last).stream().filter(id -> id.startsWith("furniture/icon/")).count());
        click(last, x, top);
        assertEquals(List.of("scroll 3", "scroll 0"), log.calls);
        // A scroll value past the end (e.g. from an older frame) is clamped instead of showing an empty list.
        assertEquals(images(last), images(FurnitureMenu.build(GLYPHS, new State(Wood.OAK, null, 99), RICH, log)));
    }

    @Test
    void everyGlyphSitsWhereItsFontCanDrawIt() {
        for (State state : List.of(new State(Wood.OAK, null, 0), new State(Wood.WARPED, Type.BIG_CRATE, 3),
                new State(Wood.BAMBOO, Type.FANCY_CHAIR, 1))) {
            for (var element : FurnitureMenu.build(GLYPHS, state, RICH, new Log()).getElements()) {
                if (!(element instanceof GlyphElement image)) continue;
                int y = image.properties().bounds().y();
                assertTrue(image.glyph().supportsOffset(Math.floorMod(y, 9)), image.glyph().id() + " at y " + y);
            }
        }
    }

    @Test
    void everyWoodAndPieceHasItsPictures() {
        Path glyphs = Path.of("src/main/resources/bundled-glyphs/furniture");
        for (Wood wood : Wood.values()) {
            for (Type type : Type.values()) {
                String art = FurnitureCatalog.art(wood, type);
                assertTrue(Files.exists(glyphs.resolve("icon/" + art + ".png")), "icon of " + art);
                assertTrue(Files.exists(glyphs.resolve("preview/" + art + ".png")), "preview of " + art);
                assertFalse(FurnitureCatalog.materials(wood, type).isEmpty());
            }
        }
        assertEquals("tmf_crimson_long_bench", FurnitureCatalog.itemId(Wood.CRIMSON, Type.LONG_BENCH));
    }
}
