package org.crebs.coreui.menu.gem;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.element.GlyphElement;
import org.crebs.coreui.menu.element.HotspotElement;
import org.crebs.coreui.menu.element.LabelElement;
import org.crebs.coreui.menu.gem.GemCutting.Grade;
import org.crebs.coreui.menu.gem.GemCutting.Line;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

/** Frames of the gem cutting screen, built offline with stand-in glyphs. */
class GemMenuTest {
    private static final Map<String, Glyph> GLYPHS = new HashMap<>() {
        @Override public Glyph get(Object key) { return new Glyph((String) key, 16, 16, 8, 16); }
        @Override public boolean containsKey(Object key) { return true; }
    };
    private static final Player PLAYER = (Player) Proxy.newProxyInstance(GemMenuTest.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> null);
    private static final RandomGenerator HIT = () -> 0L;
    private static final RandomGenerator MISS = new RandomGenerator() {
        @Override public long nextLong() { return -1L; }
        @Override public int nextInt(int bound) { return bound - 1; }
    };

    private static final class Log implements GemMenu.Actions {
        final List<String> calls = new ArrayList<>();
        public void cut(Player p, Line line) { calls.add("cut " + line); }
        public void reset(Player p) { calls.add("reset"); }
        public void claim(Player p, Grade grade, int score) { calls.add("claim " + grade + " " + score); }
        public void close(Player p) { calls.add("close"); }
    }

    private static List<String> texts(CoreMenu menu) {
        return menu.getElements().stream().filter(LabelElement.class::isInstance)
                .map(e -> PlainTextComponentSerializer.plainText().serialize(e.properties().label())).toList();
    }

    private static List<String> images(CoreMenu menu) {
        return menu.getElements().stream().filter(GlyphElement.class::isInstance).map(e -> ((GlyphElement) e).glyph().id()).toList();
    }

    private static boolean clickable(CoreMenu menu, int x, int y) {
        return menu.getElements().stream().filter(HotspotElement.class::isInstance).map(e -> e.properties().bounds())
                .anyMatch(b -> x >= b.x() && x < b.x() + b.width() && y >= b.y() && y < b.y() + b.height());
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

    /** Centre of a line's 세공 button. */
    private static int[] cutButton(Line line) {
        return new int[]{GemMenu.CUT_X + 23, GemMenu.LINE_Y[line.ordinal()] + 13};
    }

    @Test
    void freshGemShowsThirtyEmptyFacetsAndThreeCutButtons() {
        Log log = new Log();
        CoreMenu menu = GemMenu.build(GLYPHS, new GemCutting(), log);
        assertEquals(30, images(menu).stream().filter("gem/ui/facet_empty"::equals).count());
        assertTrue(texts(menu).containsAll(List.of("견고함", "아름다움", "결함", "남은 세공 30회", "75%", "점수 0 · 최대 20",
                "세공 버튼을 눌러 원석을 다듬어 보세요", "일반 · 지금")));
        for (Line line : Line.values()) click(menu, cutButton(line)[0], cutButton(line)[1]);
        assertEquals(List.of("cut STURDY", "cut BEAUTY", "cut FLAW"), log.calls);
        assertFalse(clickable(menu, 400, 262), "보석 받기 waits for a finished gem");
        click(menu, 280, 262);
        assertEquals("reset", log.calls.getLast());
    }

    @Test
    void gradeTableListsSixGradesFromMythicDown() {
        List<String> texts = texts(GemMenu.build(GLYPHS, new GemCutting(), new Log()));
        assertTrue(texts.containsAll(List.of("신화", "17점 이상", "전설", "14점 이상", "영웅", "11점 이상", "희귀", "8점 이상",
                "고급", "4점 이상", "3점 이하")));
        assertTrue(texts.indexOf("신화") < texts.indexOf("전설"));
    }

    @Test
    void facetsAndLogFollowTheCuts() {
        GemCutting gem = new GemCutting();
        gem.cut(Line.STURDY, HIT);
        gem.cut(Line.FLAW, HIT);
        gem.cut(Line.BEAUTY, MISS);
        CoreMenu menu = GemMenu.build(GLYPHS, gem, new Log());
        List<String> images = images(menu);
        assertEquals(1, images.stream().filter("gem/ui/facet_good"::equals).count(), "견고함 hit: blue");
        assertEquals(1, images.stream().filter("gem/ui/facet_bad"::equals).count(), "결함 hit: red");
        assertEquals(1, images.stream().filter("gem/ui/facet_miss"::equals).count(), "아름다움 miss: grey");
        // 75 → hit 65 → hit 55 → miss 65; newest first.
        List<String> texts = texts(menu);
        assertTrue(texts.containsAll(List.of("65%", "아름다움 실패", "결함 성공", "견고함 성공", "55%", "점수 0 · 최대 18")));
        assertTrue(texts.indexOf("아름다움 실패") < texts.indexOf("견고함 성공"), "newest cut first");
    }

    @Test
    void fullLineHasNoCutButton() {
        GemCutting gem = new GemCutting();
        for (int i = 0; i < GemCutting.SLOTS; i++) gem.cut(Line.BEAUTY, HIT);
        CoreMenu menu = GemMenu.build(GLYPHS, gem, new Log());
        int[] beauty = cutButton(Line.BEAUTY), sturdy = cutButton(Line.STURDY);
        assertFalse(clickable(menu, beauty[0], beauty[1]));
        assertTrue(clickable(menu, sturdy[0], sturdy[1]));
        assertTrue(texts(menu).contains("완료"));
    }

    @Test
    void finishedGemCanBeClaimedWithItsGrade() {
        GemCutting gem = new GemCutting();
        for (int i = 0; i < GemCutting.SLOTS; i++) gem.cut(Line.STURDY, HIT);
        for (int i = 0; i < GemCutting.SLOTS; i++) gem.cut(Line.BEAUTY, i < 4 ? HIT : MISS);
        for (int i = 0; i < GemCutting.SLOTS; i++) gem.cut(Line.FLAW, i < 2 ? HIT : MISS);
        assertEquals(12, gem.score());
        Log log = new Log();
        CoreMenu menu = GemMenu.build(GLYPHS, gem, log);
        assertTrue(texts(menu).containsAll(List.of("완성된 보석", "세공 완료", "영웅 보석", "영웅 · 결과", "영웅 보석 완성! (점수 12)")));
        assertTrue(images(menu).contains("village/icon/ender_pearl"));
        for (Line line : Line.values()) assertFalse(clickable(menu, cutButton(line)[0], cutButton(line)[1]));
        click(menu, 400, 262);
        assertEquals(List.of("claim EPIC 12"), log.calls);
    }

    @Test
    void noTextCrossesTheBookSeam() {
        GemCutting gem = new GemCutting();
        for (int i = 0; i < 6; i++) gem.cut(Line.STURDY, HIT);
        for (var element : GemMenu.build(GLYPHS, gem, new Log()).getElements()) {
            if (!(element instanceof LabelElement)) continue;
            var b = element.properties().bounds();
            assertFalse(b.y() < 153 && b.y() + 9 > 153, "text at y " + b.y() + " would be covered by the lower book tiles");
        }
    }
}
