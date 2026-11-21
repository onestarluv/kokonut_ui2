package org.crebs.coreui.menu.kokonut;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.element.GlyphElement;
import org.crebs.coreui.menu.element.HotspotElement;
import org.crebs.coreui.menu.element.LabelElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Frames of the 데미지 스킨 book screen, built offline with stand-in glyphs. */
class DamageSkinMenuTest {
    private static final Player PLAYER = (Player) Proxy.newProxyInstance(DamageSkinMenuTest.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> null);
    private static final List<DamageSkinMenu.Skin> SKINS = List.of(new DamageSkinMenu.Skin("breeze", "브리즈"),
            new DamageSkinMenu.Skin("default", "기본"), new DamageSkinMenu.Skin("heart", "하트"));

    /** Every UI glyph exists; damage digits are 10 px wide, and only the skins in {@code withDigits} have them. */
    private static Map<String, Glyph> glyphs(Set<String> withDigits) {
        return new HashMap<>() {
            @Override public Glyph get(Object key) {
                String id = (String) key;
                if (id.startsWith("damage/digit/")) return withDigits.contains(id.split("/")[2]) ? new Glyph(id, 10, 16, 8, 11) : null;
                if (id.startsWith("damage/icon/")) return null;
                return new Glyph(id, 16, 16, 8, 16);
            }
            @Override public boolean containsKey(Object key) { return get(key) != null; }
        };
    }

    private static final class Log implements DamageSkinMenu.Actions {
        final List<String> calls = new ArrayList<>();
        public void select(Player p, DamageSkinMenu.Skin skin) { calls.add("select " + skin.id()); }
        public void page(Player p, int page) { calls.add("page " + page); }
        public void equip(Player p, DamageSkinMenu.Skin skin) { calls.add("equip " + skin.id()); }
        public void unequip(Player p) { calls.add("unequip"); }
        public void titles(Player p) { calls.add("titles"); }
        public void colorNick(Player p) { calls.add("colorNick"); }
        public void close(Player p) { calls.add("close"); }
    }

    private static List<String> texts(CoreMenu menu) {
        return menu.getElements().stream().filter(LabelElement.class::isInstance)
                .map(e -> PlainTextComponentSerializer.plainText().serialize(e.properties().label())).toList();
    }

    private static List<GlyphElement> images(CoreMenu menu) {
        return menu.getElements().stream().filter(GlyphElement.class::isInstance).map(e -> (GlyphElement) e).toList();
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

    private static boolean clickable(CoreMenu menu, int x, int y) {
        return menu.getElements().stream().filter(HotspotElement.class::isInstance).map(e -> e.properties().bounds())
                .anyMatch(b -> x >= b.x() && x < b.x() + b.width() && y >= b.y() && y < b.y() + b.height());
    }

    @Test
    void tabsLeadBackAndTheSkinTabIsActive() {
        Log log = new Log();
        CoreMenu menu = DamageSkinMenu.build(glyphs(Set.of("breeze")),
                new DamageSkinMenu.View(true, SKINS, 9, null, SKINS.get(0), 0, true), log);
        assertEquals(DamageSkinMenu.ID, menu.getId());
        assertTrue(texts(menu).containsAll(List.of("칭호", "색 닉네임", "데미지 스킨", "보유 스킨", "3 / 9개")));
        assertTrue(images(menu).stream().anyMatch(i -> i.glyph().id().equals("menu/ui/tab_join") && i.properties().bounds().x() == 201));
        click(menu, 20, 5);
        click(menu, 120, 5);
        assertFalse(clickable(menu, 210, 5), "the active tab is not a button");
        assertEquals(List.of("titles", "colorNick"), log.calls);

        CoreMenu noColor = DamageSkinMenu.build(glyphs(Set.of()), new DamageSkinMenu.View(true, SKINS, 9, null, null, 0, false), log);
        assertFalse(texts(noColor).contains("색 닉네임"));
        assertTrue(images(noColor).stream().anyMatch(i -> i.glyph().id().equals("menu/ui/tab") && i.properties().bounds().x() == 108));
    }

    @Test
    void previewDrawsOneToZeroInTheSkinDigitsCentred() {
        CoreMenu menu = DamageSkinMenu.build(glyphs(Set.of("breeze")),
                new DamageSkinMenu.View(true, SKINS, 9, "breeze", SKINS.get(0), 0, false), new Log());
        List<GlyphElement> digits = images(menu).stream().filter(i -> i.glyph().id().startsWith("damage/digit/breeze/")).toList();
        // Row preview "123" for the breeze row, then the frame's "1234567890".
        assertEquals(3 + 10, digits.size());
        List<GlyphElement> big = digits.subList(3, 13);
        assertEquals("damage/digit/breeze/1", big.getFirst().glyph().id());
        assertEquals("damage/digit/breeze/0", big.getLast().glyph().id());
        assertEquals(240 + (200 - 100) / 2, big.getFirst().properties().bounds().x());
        assertTrue(texts(menu).containsAll(List.of("브리즈 데미지 스킨", "장착 중")));
        // Skins without digit glyphs say so instead.
        CoreMenu missing = DamageSkinMenu.build(glyphs(Set.of()),
                new DamageSkinMenu.View(true, SKINS, 9, null, SKINS.get(2), 0, false), new Log());
        assertTrue(texts(missing).contains("미리보기 이미지가 없어요"));
    }

    @Test
    void equipAndUnequipFollowTheSelection() {
        Log log = new Log();
        CoreMenu other = DamageSkinMenu.build(glyphs(Set.of()),
                new DamageSkinMenu.View(true, SKINS, 9, "heart", SKINS.get(0), 0, false), log);
        click(other, 400, 262);
        click(other, 330, 262);
        click(other, 20, 75);
        assertEquals(List.of("equip breeze", "unequip", "select default"), log.calls);

        CoreMenu on = DamageSkinMenu.build(glyphs(Set.of()),
                new DamageSkinMenu.View(true, SKINS, 9, "breeze", SKINS.get(0), 0, false), log);
        assertFalse(clickable(on, 400, 262));
        CoreMenu nothing = DamageSkinMenu.build(glyphs(Set.of()),
                new DamageSkinMenu.View(true, SKINS, 9, null, SKINS.get(0), 0, false), log);
        assertFalse(clickable(nothing, 330, 262), "nothing to unequip");
    }

    @Test
    void loadingAndEmptyFramesExplainThemselves() {
        CoreMenu loading = DamageSkinMenu.build(glyphs(Set.of()), new DamageSkinMenu.View(false, List.of(), 9, null, null, 0, false), new Log());
        assertTrue(texts(loading).containsAll(List.of("데미지 스킨 정보를 불러오는 중이에요", "잠시만 기다려 주세요")));
        CoreMenu empty = DamageSkinMenu.build(glyphs(Set.of()), new DamageSkinMenu.View(true, List.of(), 9, null, null, 0, false), new Log());
        assertTrue(texts(empty).containsAll(List.of("0 / 9개", "보유한 데미지 스킨이 없어요")));
    }

    @Test
    void pickKeepsSelectionThenEquippedThenFirstRow() {
        assertEquals("heart", DamageSkinMenu.pick(SKINS, 0, "heart", "default").id());
        assertEquals("default", DamageSkinMenu.pick(SKINS, 0, null, "default").id());
        assertEquals("breeze", DamageSkinMenu.pick(SKINS, 0, "gone", null).id());
        assertNull(DamageSkinMenu.pick(List.of(), 0, "heart", "heart"));
    }
}
