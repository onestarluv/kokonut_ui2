package org.crebs.coreui.menu.skill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.book.BookCanvas;
import org.crebs.coreui.menu.skill.SkillCatalog.Bonus;
import org.crebs.coreui.menu.skill.SkillCatalog.Branch;
import org.crebs.coreui.menu.skill.SkillCatalog.LevelReward;
import org.crebs.coreui.menu.skill.SkillCatalog.Node;
import org.crebs.coreui.menu.skill.SkillCatalog.Skill;
import org.crebs.coreui.menu.skill.SkillCatalog.Stat;
import org.crebs.coreui.menu.skill.SkillCatalog.XpSource;
import org.crebs.coreui.menu.skill.SkillStore.Progress;
import org.crebs.coreui.menu.skill.SkillStore.Status;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.crebs.coreui.menu.book.BookCanvas.*;

/**
 * Skill (숙련도) screens (prototype) in the book layout, {@code /coreui skill}. The left page always starts with the
 * skill's level, XP bar and points; three tabs share the book:
 * <ul>
 *   <li>{@link View#TREE}: the tech tree on the left page (hub medallion in the middle, 탐사 above, 채굴 below, 보조
 *       beside it). A node's frame shows its state (gold = learned, green = can be learned, grey = locked), the red
 *       ring the selection, gold connectors the learned path. The right page describes the selected node: effect,
 *       every requirement with a ✔/✘, the nodes it leads to, and the invest button.</li>
 *   <li>{@link View#REWARD}: level rewards (받기 / 모두 받기), the level-scaled passive bonuses, the point summary.</li>
 *   <li>{@link View#INFO}: XP per ore, statistics, points invested per branch, the point rules.</li>
 * </ul>
 * Layout rules of the tree: node frames are 20 px with a 16 px vanilla item inside, so node rows sit on y % 9 == 0;
 * the hub ends at y 152 and the row below starts after the book seam (y 153). Connector glyphs exist for the gaps
 * this layout uses only ({@code tools/dex_glyphs.py}, {@code skill_glyphs}).
 * PROTOTYPE: sample data ({@link SkillCatalog}), progress in server memory ({@link SkillStore}); learned nodes and
 * claimed rewards have no effect on the game.
 */
public final class SkillMenu {
    static final String ID = "kokonut-skill";
    /** Top of the node rows: 탐사 3단계, 탐사 1~2단계, beside the hub, 채굴 1단계, 2단계, 3단계. */
    private static final int[] ROW_Y = {63, 90, 126, 162, 189, 216};
    private static final int HUB_X = 97, HUB_Y = 117, HUB_CX = HUB_X + 18;
    private static final int[] REWARD_Y = {72, 99, 126, 153, 180};
    private static final TextColor GOLD = TextColor.color(0x9A6A12), LOCKED_TINT = TextColor.color(0x8C8270);

    enum View { TREE, REWARD, INFO }

    /** {@code selected}: node shown on the right page of TREE; {@code confirmReset}: 초기화 asked once. */
    record State(View view, String selected, boolean confirmReset) {
        static final State START = new State(View.TREE, "resonance", false);

        State view(View v) { return new State(v, selected, false); }
        State select(String id) { return new State(View.TREE, id, false); }
        State confirm(boolean ask) { return new State(view, selected, ask); }
    }

    interface Actions {
        void go(Player p, State next);
        void invest(Player p, Node node);
        void reset(Player p);
        void claim(Player p, LevelReward reward);
        void claimAll(Player p);
        void close(Player p);
    }

    private SkillMenu() {
    }

    /** False while the glyphs are not loaded (pack still generating or an older pack on the server). */
    public static boolean open(CoreUi coreUi, Player player) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("dex/ui/page_0_0") || !g.containsKey("skill/ui/node_on") || !g.containsKey("skill/ui/tab_gold")
                || !g.containsKey("skill/icon/" + SkillCatalog.MINING.icon()) || !g.containsKey("dex/ui/btn_green_100")) return false;
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        show(coreUi, player, State.START);
        return true;
    }

    private static void show(CoreUi coreUi, Player player, State state) {
        if (!player.isOnline()) return;
        Skill skill = SkillCatalog.MINING;
        coreUi.open(player, build(coreUi.getGlyphs().all(), skill, SkillStore.of(player.getUniqueId()), state, new Actions() {
            public void go(Player p, State next) { show(coreUi, p, next); }
            public void invest(Player p, Node node) {
                if (SkillStore.learn(p.getUniqueId(), skill, node)) {
                    feedback(p, "「" + node.name() + "」 기술을 배웠어요. 남은 포인트 " + SkillStore.of(p.getUniqueId()).points(skill) + " P", true);
                } else feedback(p, "지금은 배울 수 없는 기술이에요.", false);
                show(coreUi, p, state.select(node.id()));
            }
            public void reset(Player p) {
                int back = SkillStore.reset(p.getUniqueId(), skill);
                feedback(p, "기술을 모두 초기화하고 " + back + " P를 돌려받았어요.", false);
                show(coreUi, p, state.confirm(false));
            }
            public void claim(Player p, LevelReward reward) {
                if (SkillStore.claim(p.getUniqueId(), reward)) feedback(p, reward.title() + " 보상 「" + reward.reward() + "」을(를) 받았어요. (샘플)", true);
                show(coreUi, p, state);
            }
            public void claimAll(Player p) {
                long count = skill.rewards().stream().filter(r -> SkillStore.claim(p.getUniqueId(), r)).count();
                feedback(p, count > 0 ? "레벨 보상 " + count + "개를 받았어요. (샘플)" : "받을 수 있는 보상이 없어요.", count > 0);
                show(coreUi, p, state);
            }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    private static void feedback(Player p, String message, boolean good) {
        Messages.send(p, "[프로토타입] " + message);
        if (good) p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
        else p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 0.8f);
    }

    /** One frame; pure apart from the callbacks, so offline checks and the preview tool can build it. */
    static CoreMenu build(Map<String, Glyph> g, Skill skill, Progress pr, State state, Actions act) {
        BookCanvas c = new BookCanvas(g, ID, skill.name() + " 숙련도");
        c.pages();
        long waiting = skill.rewards().stream().filter(pr::claimable).count();
        tab(c, 16, "menu/ui/tab", skill.icon(), "테크트리", state.view() == View.TREE, p -> act.go(p, state.view(View.TREE)));
        tab(c, 108, "skill/ui/tab_gold", "gold_ingot", waiting > 0 ? "보상 (" + waiting + ")" : "보상", state.view() == View.REWARD,
                p -> act.go(p, state.view(View.REWARD)));
        tab(c, 200, "skill/ui/tab_blue", "book", "정보", state.view() == View.INFO, p -> act.go(p, state.view(View.INFO)));
        c.close(act::close);
        header(c, skill, pr);
        switch (state.view()) {
            case TREE -> tree(c, skill, pr, state, act);
            case REWARD -> rewards(c, skill, pr, act);
            case INFO -> info(c, skill, pr);
        }
        return c.build();
    }

    /** An 88 px tab at {@code x}: the active one is joined to the book, the others are darker and clickable. */
    private static void tab(BookCanvas c, int x, String glyph, String icon, String label, boolean active, Consumer<Player> open) {
        c.image(active ? glyph : glyph + "_off", x, 0);
        c.item(icon, x + 4, 2);
        c.text(label, x + 22, 6, WHITE);
        if (active) c.image(glyph + "_join", x + 1, 20);
        else c.click(x, 0, 88, 20, open, null);
    }

    /** Left page, every view: skill and level, points to invest, XP bar, XP numbers and the point rule. */
    private static void header(BookCanvas c, Skill skill, Progress pr) {
        String title = skill.name() + " 숙련도";
        boolean max = pr.level() >= skill.maxLevel();
        int points = pr.points(skill);
        c.text(title, LEFT, TOP, INK);
        c.text("Lv. " + pr.level() + " / " + skill.maxLevel(), LEFT + c.width(title) + 6, TOP, GOLD);
        c.right("보유 " + points + " P", LEFT + PAGE, TOP, points > 0 ? GREEN : DIM);
        c.image("dex/ui/bar_200", LEFT, 43);
        int amount = max ? 198 : (int) Math.round(198 * Math.clamp(pr.xpNeed() <= 0 ? 0 : (double) pr.xp() / pr.xpNeed(), 0, 1));
        for (int piece = 128, cursor = LEFT + 1; piece >= 1; piece /= 2) {
            while (amount >= piece) {
                c.image("skill/ui/xp_" + piece, cursor, 44);
                cursor += piece;
                amount -= piece;
            }
        }
        c.text(max ? "최고 레벨" : NUMBER.format(pr.xp()) + " / " + NUMBER.format(pr.xpNeed()) + " XP", LEFT, 51, DIM);
        c.right(skill.levelsPerPoint() + "레벨당 1 P · 투자 " + pr.spent(skill) + " P", LEFT + PAGE, 51, DIM);
    }

    private static int nodeX(Node n) { return 21 + 14 * n.h(); }
    private static int nodeY(Node n) { return ROW_Y[n.row()]; }

    private static String label(Status s) {
        return switch (s) { case LEARNED -> "습득"; case OPEN -> "습득 가능"; case LOCKED -> "잠김"; };
    }

    private static void tree(BookCanvas c, Skill skill, Progress pr, State state, Actions act) {
        Node selected = skill.node(state.selected()) != null ? skill.node(state.selected()) : skill.nodes().getFirst();
        // Ring, connectors (the learned path last, so it lies on top where two meet), then the nodes: on one text
        // line later elements cover earlier ones, so connectors cross the ring the same way on every row.
        c.image("skill/ui/ring", nodeX(selected) - 2, nodeY(selected) - 2);
        for (boolean lit : new boolean[]{false, true}) {
            for (Node n : skill.nodes()) {
                for (String req : n.requires()) {
                    if ((pr.has(n.id()) && pr.has(req)) == lit) edge(c, skill, n, req, lit ? "on" : "off");
                }
            }
        }
        c.image("skill/ui/hub", HUB_X, HUB_Y);
        c.image("skill/icon/" + skill.icon(), HUB_X + 2, HUB_Y + 2);
        c.click(HUB_X, HUB_Y, 36, 36, p -> { }, HoverEvent.showText(Component.text(skill.name() + " 숙련도 · Lv. " + pr.level())));
        for (Node n : skill.nodes()) {
            Status s = pr.status(n);
            int x = nodeX(n), y = nodeY(n);
            c.image("skill/ui/node_" + switch (s) { case LEARNED -> "on"; case OPEN -> "open"; case LOCKED -> "locked"; }, x, y);
            if (s == Status.LOCKED) lockedIcon(c, n, x + 2, y + 2);
            else c.item(n.icon(), x + 2, y + 2);
            c.click(x, y, 20, 20, p -> act.go(p, state.select(n.id())), HoverEvent.showText(Component.text(n.name() + " · " + label(s))));
        }

        // Bottom row: the legend and 초기화 (asks once more).
        int spent = pr.spent(skill);
        if (state.confirmReset() && spent > 0) {
            c.fitted(spent + " P를 모두 돌려받을까요?", LEFT, BUTTON_Y + 6, PAGE - 84, RED);
            c.button("paper", 36, "취소", LEFT + PAGE - 76, BUTTON_Y, INK, p -> act.go(p, state.confirm(false)));
            c.button("paper", 36, "확인", LEFT + PAGE - 36, BUTTON_Y, RED, act::reset);
        } else {
            int x = LEFT;
            for (String[] key : new String[][]{{"on", "습득"}, {"open", "가능"}, {"locked", "잠김"}}) {
                c.image("skill/ui/key_" + key[0], x, BUTTON_Y + 6);
                c.text(key[1], x + 11, BUTTON_Y + 6, DIM);
                x += 11 + c.width(key[1]) + 8;
            }
            c.button("paper", 52, "초기화", LEFT + PAGE - 52, BUTTON_Y, spent > 0 ? INK : DIM, spent > 0 ? p -> act.go(p, state.confirm(true)) : null);
        }
        detail(c, skill, pr, selected, state, act);
    }

    /** The connector between {@code n} and its requirement; gold ({@code on}) when both ends are learned. */
    private static void edge(BookCanvas c, Skill skill, Node n, String req, String state) {
        String base = "skill/ui/edge_" + state + "_";
        int cx = nodeX(n) + 10, y = nodeY(n);
        String name;
        int x, top;
        if (SkillCatalog.HUB.equals(req)) {
            if (n.row() == 2) {  // beside the hub
                name = "h14";
                x = cx < HUB_CX ? nodeX(n) + 20 : HUB_X + 36;
                top = y + 9;
            } else if (n.row() < 2) {  // above it
                name = "v7";
                x = cx - 1;
                top = y + 20;
            } else {  // below it, across the book seam: from the hub's left, middle or right foot
                int from = HUB_CX + 14 * Integer.signum(cx - HUB_CX), dx = cx - from;
                name = dx == 0 ? "v9" : (dx > 0 ? "r" : "l") + Math.abs(dx) + "_9";
                x = Math.min(cx, from) - 1;
                top = HUB_Y + 36;
            }
        } else {
            Node parent = skill.node(req);
            if (parent == null) return;
            if (parent.row() == n.row()) {
                name = "h8";
                x = Math.min(nodeX(parent), nodeX(n)) + 20;
                top = y + 9;
            } else {
                Node upper = parent.row() < n.row() ? parent : n, lower = upper == parent ? n : parent;
                int dx = nodeX(lower) - nodeX(upper);
                name = dx == 0 ? "v7" : (dx > 0 ? "r" : "l") + Math.abs(dx);
                x = Math.min(nodeX(upper), nodeX(lower)) + 9;
                top = nodeY(upper) + 20;
            }
        }
        if (c.g.containsKey(base + name)) c.image(base + name, x, top);
    }

    /** A locked node's item icon, darkened. */
    private static void lockedIcon(BookCanvas c, Node n, int x, int y) {
        Glyph icon = c.g.get("item/" + n.icon());
        if (icon == null || !icon.supportsOffset(Math.floorMod(y, 9))) c.image("menu/ui/unknown_item", x, y);
        else c.b.glyph("skill-locked-" + n.id(), icon).position(x, y).tint(LOCKED_TINT).add();
    }

    /** ✔ or ✘ and one requirement line; returns the next line's y. */
    private static int requirement(BookCanvas c, boolean met, String text, int y) {
        c.image(met ? "skill/ui/check" : "skill/ui/cross", RIGHT, y);
        c.fitted(text, RIGHT + 11, y, PAGE - 11, met ? GREEN : RED);
        return y + 11;
    }

    /** Right page of TREE: the selected node. */
    private static void detail(BookCanvas c, Skill skill, Progress pr, Node n, State state, Actions act) {
        Status s = pr.status(n);
        boolean affordable = pr.points(skill) >= n.cost();
        c.image("menu/ui/well", RIGHT, TOP);
        if (c.g.containsKey("skill/icon/" + n.icon())) c.image("skill/icon/" + n.icon(), RIGHT + 2, TOP + 2);
        else c.item(n.icon(), RIGHT + 10, TOP + 7);
        c.fitted(n.name(), RIGHT + 42, 33, PAGE - 42, INK);
        c.text(n.branch().label + " · " + n.tier() + "단계 · 비용 " + n.cost() + " P", RIGHT + 42, 45, DIM);
        switch (s) {
            case LEARNED -> c.text("습득 완료", RIGHT + 42, 57, GOLD);
            case OPEN -> c.text(affordable ? "습득 가능" : "포인트 부족", RIGHT + 42, 57, affordable ? GREEN : RED);
            case LOCKED -> c.text("잠김", RIGHT + 42, 57, RED);
        }
        c.image("shop/ui/divider_200", RIGHT, 72);
        List<String> lines = c.wrap(n.description(), PAGE);
        for (int i = 0; i < lines.size() && i < 3; i++) c.text(lines.get(i), RIGHT, 78 + i * 11, BODY);
        c.text("효과", RIGHT, 115, INK);
        lines = c.wrap(n.effect(), PAGE);
        for (int i = 0; i < lines.size() && i < 2; i++) c.text(lines.get(i), RIGHT, 127 + i * 11, GREEN);

        // Every condition of the invest button, each with its own mark.
        c.text("요구 조건", RIGHT, 156, INK);
        int y = 168;
        for (String req : n.requires()) {
            Node parent = skill.node(req);
            if (parent != null) y = requirement(c, pr.has(req), "「" + parent.name() + "」 습득", y);
        }
        if (n.level() > 1) y = requirement(c, pr.level() >= n.level(), "숙련도 Lv. " + n.level() + " 이상 (현재 " + pr.level() + ")", y);
        if (s == Status.LEARNED) requirement(c, true, "포인트 " + n.cost() + " P 투자함", y);
        else requirement(c, affordable, "포인트 " + n.cost() + " P (보유 " + pr.points(skill) + " P)", y);

        c.text("이어지는 기술", RIGHT, 214, INK);
        List<Node> next = skill.children(n);
        if (next.isEmpty()) c.text("이 가지의 마지막 기술이에요.", RIGHT, 231, DIM);
        for (int i = 0; i < next.size() && i < 2; i++) {
            Node child = next.get(i);
            int x = RIGHT + i * 102;
            c.image("dex/ui/slot_normal", x, 225);
            c.item(child.icon(), x + 2, 227);
            c.fitted(child.name(), x + 24, 231, 74, pr.status(child) == Status.LEARNED ? GOLD : BODY);
            c.click(x, 225, 98, 20, p -> act.go(p, state.select(child.id())),
                    HoverEvent.showText(Component.text(child.name() + " · " + label(pr.status(child)))));
        }

        int bx = RIGHT + PAGE - 100;
        String price = n.cost() + " P 투자";
        if (s == Status.LEARNED) {
            c.text("이미 배운 기술이에요", RIGHT, BUTTON_Y + 6, DIM);
            c.button("off", 100, "습득 완료", bx, BUTTON_Y, DIM, null);
        } else if (s == Status.LOCKED) {
            c.text("요구 조건이 남았어요", RIGHT, BUTTON_Y + 6, RED);
            c.button("off", 100, "잠김", bx, BUTTON_Y, DIM, null);
        } else if (!affordable) {
            c.text("포인트가 부족해요", RIGHT, BUTTON_Y + 6, RED);
            c.button("off", 100, price, bx, BUTTON_Y, DIM, null);
        } else {
            c.text("보유 " + pr.points(skill) + " P → " + (pr.points(skill) - n.cost()) + " P", RIGHT, BUTTON_Y + 6, DIM);
            c.button("green", 100, price, bx, BUTTON_Y, WHITE, p -> act.invest(p, n));
        }
    }

    private static String percent(double value) {
        return "+" + (value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value)) + "%";
    }

    private static void rewards(BookCanvas c, Skill skill, Progress pr, Actions act) {
        c.image("shop/ui/divider_200", LEFT, 62);
        List<LevelReward> rewards = skill.rewards();
        for (int i = 0; i < rewards.size() && i < REWARD_Y.length; i++) {
            LevelReward r = rewards.get(i);
            int y = REWARD_Y[i];
            boolean reached = pr.level() >= r.level();
            c.image("dex/ui/slot_normal", LEFT, y);
            c.item(r.icon(), LEFT + 2, y + 2);
            c.text(r.title(), LEFT + 26, y + 1, reached ? INK : DIM);
            c.fitted(r.reward(), LEFT + 26, y + 11, PAGE - 26 - 50, reached ? BODY : DIM);
            if (pr.claimed().contains(r.level())) c.button("off", 46, "완료", LEFT + PAGE - 46, y, DIM, null);
            else if (reached) c.button("green", 46, "받기", LEFT + PAGE - 46, y, WHITE, p -> act.claim(p, r));
            else c.button("off", 46, "잠김", LEFT + PAGE - 46, y, DIM, null);
        }
        long waiting = rewards.stream().filter(pr::claimable).count();
        c.text(waiting > 0 ? "받을 수 있는 보상 " + waiting + "개" : "받을 보상이 없어요", LEFT, BUTTON_Y + 6, waiting > 0 ? GREEN : DIM);
        if (waiting > 0) c.button("green", 98, "모두 받기", LEFT + PAGE - 98, BUTTON_Y, WHITE, act::claimAll);
        else c.button("off", 98, "모두 받기", LEFT + PAGE - 98, BUTTON_Y, DIM, null);

        // Right page: passive bonuses that grow with the level, the point summary, what comes next.
        c.text("숙련 보너스", RIGHT, TOP, INK);
        c.right("레벨에 따라 자동 적용", RIGHT + PAGE, TOP, DIM);
        c.image("shop/ui/divider_200", RIGHT, 43);
        for (int i = 0; i < skill.bonuses().size() && i < 3; i++) {
            Bonus b = skill.bonuses().get(i);
            c.text(b.name(), RIGHT, 49 + i * 12, BODY);
            c.right("레벨당 " + percent(b.perLevel()), RIGHT + PAGE - 48, 49 + i * 12, DIM);
            c.right(percent(b.perLevel() * pr.level()), RIGHT + PAGE, 49 + i * 12, GREEN);
        }
        c.image("shop/ui/divider_200", RIGHT, 91);
        c.text("포인트", RIGHT, 97, INK);
        String[][] rows = {{"획득한 포인트", pr.earned(skill) + " P"}, {"투자한 포인트", pr.spent(skill) + " P"}, {"보유 포인트", pr.points(skill) + " P"}};
        for (int i = 0; i < rows.length; i++) {
            c.text(rows[i][0], RIGHT, 109 + i * 12, BODY);
            c.right(rows[i][1], RIGHT + PAGE, 109 + i * 12, i == 2 && pr.points(skill) > 0 ? GREEN : INK);
        }
        c.text("다음 목표", RIGHT, 156, INK);
        int y = 168;
        if (pr.level() < skill.maxLevel()) {
            c.text("다음 레벨까지 " + NUMBER.format(Math.max(0, pr.xpNeed() - pr.xp())) + " XP", RIGHT, y, BODY);
            y += 12;
            int pointLevel = (pr.level() / skill.levelsPerPoint() + 1) * skill.levelsPerPoint();
            if (pointLevel <= skill.maxLevel()) {
                c.text("Lv. " + pointLevel + " 달성 시 포인트 +1 P", RIGHT, y, BODY);
                y += 12;
            }
        } else {
            c.text("최고 레벨에 도달했어요", RIGHT, y, GOLD);
            y += 12;
        }
        LevelReward coming = rewards.stream().filter(r -> r.level() > pr.level()).findFirst().orElse(null);
        if (coming != null) c.fitted("Lv. " + coming.level() + " 보상 · " + coming.reward(), RIGHT, y, PAGE, BODY);
    }

    private static void info(BookCanvas c, Skill skill, Progress pr) {
        c.text("경험치 획득", LEFT, 67, INK);
        c.right("광석 1개당", LEFT + PAGE, 67, DIM);
        List<XpSource> sources = skill.sources();
        for (int i = 0; i < sources.size() && i < 8; i++) {
            XpSource src = sources.get(i);
            int y = 83 + i * 18;
            c.item(src.icon(), LEFT, y);
            c.text(src.name(), LEFT + 22, y + 4, BODY);
            c.right("+" + NUMBER.format(src.xp()) + " XP", LEFT + PAGE, y + 4, GOLD);
        }
        c.text("섬세한 손길로 캔 광석은 경험치를 주지 않아요.", LEFT, BUTTON_Y + 6, DIM);

        c.text("통계", RIGHT, TOP, INK);
        c.image("shop/ui/divider_200", RIGHT, 43);
        for (int i = 0; i < skill.stats().size() && i < 4; i++) {
            Stat stat = skill.stats().get(i);
            c.text(stat.label(), RIGHT, 49 + i * 12, BODY);
            c.right(stat.value(), RIGHT + PAGE, 49 + i * 12, INK);
        }
        c.image("shop/ui/divider_200", RIGHT, 97);
        c.text("가지별 투자", RIGHT, 103, INK);
        Map<Branch, Integer> total = skill.branchCost(), spent = pr.branchSpent(skill);
        int row = 0;
        for (Branch b : Branch.values()) {
            int y = 116 + row++ * 12, all = total.getOrDefault(b, 0), mine = spent.getOrDefault(b, 0);
            c.text(b.label, RIGHT, y, BODY);
            c.bar(RIGHT + 36, y + 1, all == 0 ? 0 : (double) mine / all);
            c.right(mine + " / " + all + " P", RIGHT + PAGE, y, mine > 0 ? INK : DIM);
        }
        c.text("포인트 규칙", RIGHT, 156, INK);
        String[] rules = {skill.levelsPerPoint() + "레벨마다 포인트 1 P를 얻어요.", "요구 조건을 모두 채운 기술만 배울 수 있어요.",
                "마지막 단계 기술은 2 P가 필요해요.", "초기화하면 투자한 포인트를 모두 돌려받아요."};
        for (int i = 0; i < rules.length; i++) c.fitted("· " + rules[i], RIGHT, 168 + i * 12, PAGE, BODY);
    }
}
