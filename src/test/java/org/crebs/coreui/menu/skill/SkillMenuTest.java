package org.crebs.coreui.menu.skill;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.element.GlyphElement;
import org.crebs.coreui.menu.element.HotspotElement;
import org.crebs.coreui.menu.element.LabelElement;
import org.crebs.coreui.menu.skill.SkillCatalog.LevelReward;
import org.crebs.coreui.menu.skill.SkillCatalog.Node;
import org.crebs.coreui.menu.skill.SkillCatalog.Skill;
import org.crebs.coreui.menu.skill.SkillMenu.State;
import org.crebs.coreui.menu.skill.SkillMenu.View;
import org.crebs.coreui.menu.skill.SkillStore.Progress;
import org.crebs.coreui.menu.skill.SkillStore.Status;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Frames of the 숙련도 prototype and its sample data, built offline with stand-in glyphs. */
class SkillMenuTest {
    /** Every glyph exists (16 px, all text-line offsets), as with a fully loaded pack. */
    private static final Map<String, Glyph> GLYPHS = new HashMap<>() {
        @Override public Glyph get(Object key) { return new Glyph((String) key, 16, 16, 8, 16); }
        @Override public boolean containsKey(Object key) { return true; }
    };
    private static final Player PLAYER = (Player) Proxy.newProxyInstance(SkillMenuTest.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> null);
    private static final Skill MINING = SkillCatalog.MINING;

    /** Records what the clicked hotspots asked for. */
    private static final class Log implements SkillMenu.Actions {
        final List<String> calls = new ArrayList<>();
        public void go(Player p, State state) { calls.add("go " + state.view() + " " + state.selected() + " " + state.confirmReset()); }
        public void invest(Player p, Node node) { calls.add("invest " + node.id()); }
        public void reset(Player p) { calls.add("reset"); }
        public void claim(Player p, LevelReward reward) { calls.add("claim " + reward.level()); }
        public void claimAll(Player p) { calls.add("claimAll"); }
        public void close(Player p) { calls.add("close"); }
    }

    private static CoreMenu tree(Progress progress, String selected, boolean confirm, Log log) {
        return SkillMenu.build(GLYPHS, MINING, progress, new State(View.TREE, selected, confirm), log);
    }

    private static List<String> texts(CoreMenu menu) {
        return menu.getElements().stream().filter(LabelElement.class::isInstance)
                .map(e -> PlainTextComponentSerializer.plainText().serialize(e.properties().label())).toList();
    }

    private static List<String> images(CoreMenu menu) {
        return menu.getElements().stream().filter(GlyphElement.class::isInstance).map(e -> ((GlyphElement) e).glyph().id()).toList();
    }

    private static long count(CoreMenu menu, String glyph) {
        return images(menu).stream().filter(glyph::equals).count();
    }

    /** Clicks the hotspot that covers ({@code x}, {@code y}); fails when there is none. */
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
        return menu.getElements().stream().filter(HotspotElement.class::isInstance).map(e -> (HotspotElement) e)
                .filter(e -> e.onClick() != null).map(e -> e.properties().bounds())
                .anyMatch(b -> x >= b.x() && x < b.x() + b.width() && y >= b.y() && y < b.y() + b.height());
    }

    // ---- sample data ----

    @Test
    void catalogIsAConsistentTree() {
        Set<String> ids = new HashSet<>();
        Set<String> cells = new HashSet<>();
        for (Node n : MINING.nodes()) {
            assertTrue(ids.add(n.id()), "duplicate id " + n.id());
            assertTrue(cells.add(n.row() + ":" + n.h()), "two nodes share a cell: " + n.id());
            assertFalse(n.requires().isEmpty(), n.id() + " hangs in the air");
            for (String req : n.requires())
                assertTrue(SkillCatalog.HUB.equals(req) || MINING.node(req) != null, n.id() + " requires unknown " + req);
            assertTrue(n.level() <= MINING.maxLevel());
        }
        assertEquals(22, ids.size());
        // Every point of a maxed skill can be spent, but not on everything: the tree forces a choice.
        int all = MINING.nodes().stream().mapToInt(Node::cost).sum();
        assertTrue(all > MINING.maxLevel() / MINING.levelsPerPoint());
    }

    @Test
    void sampleProgressCountsPoints() {
        Progress p = SkillStore.sample();
        assertEquals(9, p.earned(MINING));
        assertEquals(6, p.spent(MINING));
        assertEquals(3, p.points(MINING));
        assertEquals(Status.LEARNED, p.status(MINING.node("sense")));
        assertEquals(Status.OPEN, p.status(MINING.node("resonance")));
        assertEquals(Status.LOCKED, p.status(MINING.node("frenzy")));
        assertEquals(6, p.branchSpent(MINING).values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void storeLearnsResetsAndClaimsOnce() {
        UUID id = UUID.randomUUID();
        assertFalse(SkillStore.learn(id, MINING, MINING.node("frenzy")), "locked");
        assertTrue(SkillStore.learn(id, MINING, MINING.node("resonance")));
        assertFalse(SkillStore.learn(id, MINING, MINING.node("resonance")), "already learned");
        assertEquals(2, SkillStore.of(id).points(MINING));

        LevelReward lv30 = MINING.rewards().get(2), lv50 = MINING.rewards().get(4);
        assertTrue(SkillStore.claim(id, lv30));
        assertFalse(SkillStore.claim(id, lv30), "claimed twice");
        assertFalse(SkillStore.claim(id, lv50), "level not reached");

        assertEquals(7, SkillStore.reset(id, MINING));
        assertEquals(9, SkillStore.of(id).points(MINING));
        assertTrue(SkillStore.of(id).claimed().contains(30), "a reset keeps claimed rewards");
    }

    // ---- 테크트리 ----

    @Test
    void treeShowsEveryNodeByStatusAndTheSelection() {
        Log log = new Log();
        CoreMenu menu = tree(SkillStore.sample(), "resonance", false, log);
        assertEquals(SkillMenu.ID, menu.getId());
        assertEquals(6, count(menu, "skill/ui/node_on"));
        assertEquals(22, count(menu, "skill/ui/node_on") + count(menu, "skill/ui/node_open") + count(menu, "skill/ui/node_locked"));
        assertEquals(1, count(menu, "skill/ui/ring"));
        assertEquals(1, count(menu, "skill/ui/hub"));
        assertTrue(texts(menu).containsAll(List.of("채광 숙련도", "Lv. 46 / 50", "보유 3 P", "1,840 / 2,600 XP", "습득", "가능", "잠김", "초기화")));
    }

    @Test
    void clickingANodeSelectsIt() {
        Log log = new Log();
        CoreMenu menu = tree(SkillStore.sample(), "resonance", false, log);
        click(menu, 95, 95);    // 광부의 감각 (h5, row 1)
        click(menu, 70, 220);   // 광란의 채굴 (h3, row 5): locked nodes can be inspected too
        assertEquals(List.of("go TREE sense false", "go TREE frenzy false"), log.calls);
    }

    @Test
    void openNodeListsRequirementsAndInvests() {
        Log log = new Log();
        CoreMenu menu = tree(SkillStore.sample(), "resonance", false, log);
        assertTrue(texts(menu).containsAll(List.of("광맥 공명", "습득 가능", "효과", "추가 광맥 발견 확률 +25%", "요구 조건",
                "「광부의 감각」 습득", "「지질 분석」 습득", "숙련도 Lv. 30 이상 (현재 46)", "포인트 1 P (보유 3 P)", "1 P 투자")));
        assertEquals(4, count(menu, "skill/ui/check"));
        assertEquals(0, count(menu, "skill/ui/cross"));
        click(menu, 380, 262);
        assertEquals(List.of("invest resonance"), log.calls);
    }

    @Test
    void lockedNodeSaysWhatIsMissingAndCannotBeBought() {
        Log log = new Log();
        CoreMenu menu = tree(SkillStore.sample(), "frenzy", false, log);
        assertTrue(texts(menu).containsAll(List.of("광란의 채굴", "「폭파 채굴」 습득", "「신속한 손놀림」 습득",
                "숙련도 Lv. 50 이상 (현재 46)", "이 가지의 마지막 기술이에요.")));
        assertEquals(3, count(menu, "skill/ui/cross"));
        assertEquals(1, count(menu, "skill/ui/check"));
        assertFalse(clickable(menu, 380, 262));
    }

    @Test
    void learnedNodeLinksToTheNextOnes() {
        Log log = new Log();
        CoreMenu menu = tree(SkillStore.sample(), "sense", false, log);
        assertTrue(texts(menu).containsAll(List.of("습득 완료", "포인트 1 P 투자함", "이어지는 기술")));
        assertFalse(clickable(menu, 380, 262), "nothing to buy");
        click(menu, 250, 230);
        click(menu, 350, 230);
        assertEquals(2, log.calls.size());
        assertEquals(Set.of("go TREE resonance false", "go TREE hawkeye false"), new HashSet<>(log.calls));
    }

    @Test
    void openNodeWithoutPointsIsNotBuyable() {
        Progress broke = new Progress(46, 0, 100, Set.of("sense", "geology", "hawkeye", "arm", "focus", "whet", "lamp", "stamina", "swift"), Set.of());
        assertEquals(0, broke.points(MINING));
        Log log = new Log();
        CoreMenu menu = tree(broke, "resonance", false, log);
        assertTrue(texts(menu).contains("포인트 1 P (보유 0 P)"));
        assertEquals(1, count(menu, "skill/ui/cross"));
        assertFalse(clickable(menu, 380, 262));
    }

    @Test
    void resetAsksFirst() {
        Log log = new Log();
        CoreMenu menu = tree(SkillStore.sample(), "resonance", false, log);
        click(menu, 190, 262);
        assertEquals(List.of("go TREE resonance true"), log.calls);

        CoreMenu confirm = tree(SkillStore.sample(), "resonance", true, log);
        assertTrue(texts(confirm).containsAll(List.of("6 P를 모두 돌려받을까요?", "취소", "확인")));
        click(confirm, 145, 262);
        click(confirm, 190, 262);
        assertEquals(List.of("go TREE resonance true", "go TREE resonance false", "reset"), log.calls);

        Progress fresh = new Progress(46, 0, 100, Set.of(), Set.of());
        assertFalse(clickable(tree(fresh, "resonance", false, log), 190, 262), "nothing to give back");
    }

    // ---- tabs ----

    @Test
    void tabsSwitchViewsAndTheActiveOneIsNotAButton() {
        Log log = new Log();
        CoreMenu menu = tree(SkillStore.sample(), "resonance", false, log);
        assertTrue(texts(menu).containsAll(List.of("테크트리", "보상 (2)", "정보")));
        assertFalse(clickable(menu, 30, 5));
        click(menu, 120, 5);
        click(menu, 210, 5);
        assertEquals(List.of("go REWARD resonance false", "go INFO resonance false"), log.calls);
    }

    // ---- 보상 ----

    @Test
    void rewardsOfferOnlyReachedUnclaimedLevels() {
        Log log = new Log();
        CoreMenu menu = SkillMenu.build(GLYPHS, MINING, SkillStore.sample(), new State(View.REWARD, "resonance", false), log);
        List<String> texts = texts(menu);
        assertEquals(2, texts.stream().filter("완료"::equals).count());
        assertEquals(2, texts.stream().filter("받기"::equals).count());
        assertEquals(1, texts.stream().filter("잠김"::equals).count());
        assertTrue(texts.containsAll(List.of("받을 수 있는 보상 2개", "모두 받기", "숙련 보너스", "+9.2%", "다음 레벨까지 760 XP")));
        assertFalse(clickable(menu, 190, 76), "Lv. 10 is claimed");
        click(menu, 190, 130);  // Lv. 30
        click(menu, 190, 157);  // Lv. 40
        assertFalse(clickable(menu, 190, 184), "Lv. 50 is not reached");
        click(menu, 160, 262);
        assertEquals(List.of("claim 30", "claim 40", "claimAll"), log.calls);

        Progress done = new Progress(46, 1840, 2600, Set.of(), Set.of(10, 20, 30, 40));
        CoreMenu none = SkillMenu.build(GLYPHS, MINING, done, new State(View.REWARD, "resonance", false), log);
        assertTrue(texts(none).contains("보상"), "no count on the tab when nothing waits");
        assertFalse(clickable(none, 160, 262));
    }

    // ---- 정보 ----

    @Test
    void infoListsSourcesStatsAndBranches() {
        CoreMenu menu = SkillMenu.build(GLYPHS, MINING, SkillStore.sample(), new State(View.INFO, "resonance", false), new Log());
        assertTrue(texts(menu).containsAll(List.of("경험치 획득", "석탄", "+5 XP", "에메랄드", "+50 XP", "통계", "가지별 투자", "탐사", "채굴", "보조", "포인트 규칙")));
    }

    @Test
    void maxLevelHidesTheXpCount() {
        Progress max = new Progress(50, 0, 0, Set.of("sense"), Set.of());
        List<String> texts = texts(tree(max, "sense", false, new Log()));
        assertTrue(texts.containsAll(List.of("Lv. 50 / 50", "최고 레벨", "보유 9 P")));
    }
}
