package org.crebs.coreui.menu.gem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.book.BookCanvas;
import org.crebs.coreui.menu.gem.GemCutting.Attempt;
import org.crebs.coreui.menu.gem.GemCutting.Cell;
import org.crebs.coreui.menu.gem.GemCutting.Grade;
import org.crebs.coreui.menu.gem.GemCutting.Line;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.crebs.coreui.menu.book.BookCanvas.*;

/**
 * Gem cutting screen (prototype, {@code /coreui gem}) in the book layout. Rules live in {@link GemCutting}; each
 * player's rough gem is kept in server memory only and nothing is taken or given yet.
 * <ul>
 *   <li>Left page: the gem (32 px icon of the grade it would get now), score and best reachable score, then the
 *       three lines 견고함 · 아름다움 · 결함 with ten facets each and a 세공 button per line.</li>
 *   <li>Right page: the shared success chance (bar), the grade table with the current grade marked and the last
 *       cuts; 새 원석 starts over, 보석 받기 finishes a fully cut gem.</li>
 * </ul>
 * Nothing starts at y 145..152 and reaches past y 153 (the lower book tiles start there and would cover it).
 */
public final class GemMenu {
    static final String ID = "kokonut-gem";
    /** Top of each line block: label at y, facets visible from y + 11, the button at y + 3. Line 3 starts below the seam. */
    static final int[] LINE_Y = {88, 122, 160};
    /**
     * Facet x step: the 16 px facet glyphs have a 1 px clear margin (14 px visible), so a 15 px step leaves a 1 px gap.
     * Ten facets end at LEFT + 149; the 세공 button sits after them.
     */
    static final int FACET = 15, CUT_X = LEFT + 154;
    /** Grade table rows on the right page (six grades; the last row ends before the seam). */
    static final int GRADE_Y = 86, GRADE_STEP = 11;
    private static final int LOG_LINES = 5;
    private static final Map<UUID, GemCutting> GEMS = new ConcurrentHashMap<>();

    interface Actions {
        void cut(Player p, Line line);
        void reset(Player p);
        void claim(Player p, Grade grade, int score);
        void close(Player p);
    }

    private GemMenu() {
    }

    /** False while the glyphs are not loaded (pack still generating or an older pack on the server). */
    public static boolean open(CoreUi coreUi, Player player) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("gem/ui/facet_empty") || !g.containsKey("dex/ui/page_0_0") || !g.containsKey("menu/ui/well")
                || !g.containsKey("dex/ui/btn_green_46") || !g.containsKey("shop/ui/divider_200")) return false;
        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.7f, 1f);
        show(coreUi, player);
        return true;
    }

    private static void show(CoreUi coreUi, Player player) {
        if (!player.isOnline()) return;
        GemCutting gem = GEMS.computeIfAbsent(player.getUniqueId(), id -> new GemCutting());
        coreUi.open(player, build(coreUi.getGlyphs().all(), gem, new Actions() {
            public void cut(Player p, Line line) {
                Grade before = gem.grade();
                Attempt attempt = gem.cut(line, ThreadLocalRandom.current());
                if (attempt == null) return;
                if (attempt.hit()) p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.BLOCKS, 0.9f, line.good ? 1.5f : 0.6f);
                else p.playSound(p, Sound.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.8f, 0.9f);
                if (gem.grade().ordinal() > before.ordinal())
                    p.playSound(p, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.BLOCKS, 0.6f, 1.6f);
                show(coreUi, p);
            }
            public void reset(Player p) {
                GEMS.put(p.getUniqueId(), new GemCutting());
                p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
                show(coreUi, p);
            }
            public void claim(Player p, Grade grade, int score) {
                Messages.send(p, "[프로토타입] " + grade.label + " 보석을 완성했어요 (점수 " + score + "). 아이템 지급은 아직 연결되지 않았어요.");
                p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.2f);
                GEMS.put(p.getUniqueId(), new GemCutting());
                show(coreUi, p);
            }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    /** Drops a player's gem (they left). */
    public static void forget(UUID player) {
        GEMS.remove(player);
    }

    /** One frame; pure apart from the callbacks, so tests and the preview tool can build it. */
    static CoreMenu build(Map<String, Glyph> g, GemCutting gem, Actions act) {
        BookCanvas c = new BookCanvas(g, ID, "보석 세공");
        c.chrome("보석 세공", null, "amethyst_shard", act::close);
        Grade grade = gem.grade();
        boolean done = gem.done();

        // Left page header: the gem as it would turn out now, score, best reachable score.
        c.text(done ? "완성된 보석" : "보석 세공", LEFT, TOP, INK);
        c.right(done ? "세공 완료" : "남은 세공 " + gem.remaining() + "회", LEFT + PAGE, TOP, done ? GREEN : DIM);
        c.image("menu/ui/well", LEFT, 43);
        icon(c, grade.icon, LEFT, 43);
        c.text(done ? grade.label + " 보석" : "다듬는 중인 원석", LEFT + 44, 46, done ? grade.color : INK);
        c.text("점수 " + gem.score() + (done ? "" : " · 최대 " + gem.best()), LEFT + 44, 58, INK);
        c.text((done ? "등급 " : "지금 등급 ") + grade.label, LEFT + 44, 70, grade.color);
        c.image("shop/ui/divider_200", LEFT, 82);

        for (Line line : Line.values()) {
            int y = LINE_Y[line.ordinal()];
            c.text(line.label, LEFT, y, line.good ? INK : RED);
            c.right("◆ " + gem.hits(line) + " / " + GemCutting.SLOTS, LEFT + FACET * GemCutting.SLOTS - 1, y, DIM);
            List<Cell> cells = gem.cells(line);
            for (int i = 0; i < cells.size(); i++) c.image(facet(cells.get(i), line), LEFT - 1 + i * FACET, y + 10);
            c.click(LEFT, y, FACET * GemCutting.SLOTS, 23, p -> { }, HoverEvent.showText(Component.text(line.label
                    + (line.good ? ": 성공할수록 점수가 올라가요" : ": 성공할수록 점수가 내려가요"))));
            if (gem.full(line)) c.button("off", 46, "완료", CUT_X, y + 3, DIM, null);
            else c.button("green", 46, "세공", CUT_X, y + 3, WHITE, p -> act.cut(p, line));
        }
        c.image("shop/ui/divider_200", LEFT, 196);
        c.text("점수 = 견고함 + 아름다움 − 결함", LEFT, 202, BODY);
        c.text("점수가 높을수록 좋은 보석이 나와요.", LEFT, 214, BODY);

        // Right page: chance, grade table, last cuts, buttons.
        c.text("세공 확률", RIGHT, TOP, INK);
        c.right(done ? "-" : gem.chance() + "%", RIGHT + PAGE, TOP, done ? DIM : GREEN);
        c.bar(RIGHT, 45, done ? 0 : gem.chance() / 100.0);
        c.text(GemCutting.MIN + "~" + GemCutting.MAX + "%", RIGHT + 106, 45, DIM);
        c.text("성공하면 −" + GemCutting.STEP + "%p, 실패하면 +" + GemCutting.STEP + "%p", RIGHT, 58, BODY);
        c.image("shop/ui/divider_200", RIGHT, 68);
        c.text("등급표", RIGHT, 74, INK);
        c.right("점수", RIGHT + PAGE, 74, DIM);
        Grade[] grades = Grade.values();
        for (int i = 0; i < grades.length; i++) {
            Grade row = grades[grades.length - 1 - i];
            int y = GRADE_Y + i * GRADE_STEP;
            boolean now = row == grade;
            c.text(row.label + (now ? (done ? " · 결과" : " · 지금") : ""), RIGHT, y, row.color);
            c.right(row == Grade.COMMON ? row.next().min - 1 + "점 이하" : row.min + "점 이상", RIGHT + PAGE, y, now ? INK : DIM);
        }

        c.text("최근 세공", RIGHT, 160, INK);
        List<Attempt> log = gem.log();
        if (log.isEmpty()) c.text("세공 버튼을 눌러 원석을 다듬어 보세요", RIGHT, 172, DIM);
        for (int i = 0; i < LOG_LINES && i < log.size(); i++) {
            Attempt a = log.get(log.size() - 1 - i);
            boolean good = a.hit() == a.line().good;
            c.text(a.line().label + " " + (a.hit() ? "성공" : "실패"), RIGHT, 172 + i * 12, good ? GREEN : RED);
            c.right(a.chance() + "%", RIGHT + PAGE, 172 + i * 12, DIM);
        }
        if (done) c.centered(grade.label + " 보석 완성! (점수 " + gem.score() + ")", RIGHT, PAGE, 238, grade.color);

        c.button("paper", 98, "새 원석", RIGHT, BUTTON_Y, INK, act::reset);
        if (done) c.button("green", 98, "보석 받기", RIGHT + PAGE - 98, BUTTON_Y, WHITE, p -> act.claim(p, grade, gem.score()));
        else c.button("off", 98, "보석 받기", RIGHT + PAGE - 98, BUTTON_Y, DIM, null);
        return c.build();
    }

    static String facet(Cell cell, Line line) {
        return switch (cell) {
            case EMPTY -> "gem/ui/facet_empty";
            case MISS -> "gem/ui/facet_miss";
            case HIT -> line.good ? "gem/ui/facet_good" : "gem/ui/facet_bad";
        };
    }

    /** 32 px gem icon ({@code village/icon/<key>}) in a 36 px well; the 16 px item icon when it is missing. */
    private static void icon(BookCanvas c, String key, int wellX, int wellY) {
        if (c.g.containsKey("village/icon/" + key)) c.image("village/icon/" + key, wellX + 2, wellY + 2);
        else c.item(key, wellX + 10, wellY + 13);
    }
}
