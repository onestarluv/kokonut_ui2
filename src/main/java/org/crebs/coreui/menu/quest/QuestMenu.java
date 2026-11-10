package org.crebs.coreui.menu.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.quest.QuestCatalog.Giver;
import org.crebs.coreui.menu.quest.QuestCatalog.Objective;
import org.crebs.coreui.menu.quest.QuestCatalog.Quest;
import org.crebs.coreui.menu.quest.QuestCatalog.Status;

import java.util.*;

import org.crebs.coreui.menu.book.BookCanvas;

import static org.crebs.coreui.menu.book.BookCanvas.*;

/**
 * Quest journal (prototype) in the book layout shared by the title screen and the shop. Quests are accepted from
 * NPCs ({@link QuestOfferMenu}), so the journal only has 진행 · 완료. Left page: filters (the dex's dark/paper style),
 * 7 quest rows (named by giver NPC, not place), pager. Right page: name, giver, description, objectives (slot icon + dex progress bar + count),
 * reward slots, 위치 찾기 and the status action (포기하기 / 완료하기). There are no player levels on the server.
 *
 * Sample data only; status changes live in {@link QuestProgress} (memory) and no rewards are given.
 */
public final class QuestMenu {
    static final String ID = "kokonut-quest";
    static final int ROWS = 7;
    private static final int[] ROW_Y = {72, 99, 126, 153, 180, 207, 234};
    private static final int[] GOAL_Y = {99, 126, 153};

    record State(Status filter, int page, String selected) {
        static final State START = new State(Status.ACTIVE, 0, null);

        State filter(Status f) { return new State(f, 0, null); }
        State page(int p) { return new State(filter, p, null); }
        State select(Quest q) { return new State(filter, page, q.id()); }
    }

    interface Actions {
        void go(Player p, State next);
        /** Moves the quest to {@code status} (prototype store), reports it and redraws. */
        void move(Player p, Quest quest, Status status, String message, boolean good);
        void close(Player p);
    }

    private QuestMenu() {
    }

    /** Returns false while the glyphs are not loaded (pack still generating or an older pack on the server). */
    public static boolean open(CoreUi coreUi, Player player) {
        if (!ready(coreUi)) return false;
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
        show(coreUi, player, QuestCatalog.SAMPLE, State.START);
        return true;
    }

    static boolean ready(CoreUi coreUi) {
        var g = coreUi.getGlyphs().all();
        return g.containsKey("dex/ui/page_0_0") && g.containsKey("menu/icon/quest") && g.containsKey("dex/ui/btn_paper_98")
                && g.containsKey("quest/ui/speech_200") && g.containsKey("dex/ui/bar_100");
    }

    static void show(CoreUi coreUi, Player player, QuestCatalog catalog, State state) {
        if (!player.isOnline()) return;
        coreUi.open(player, build(coreUi.getGlyphs().all(), catalog, state, QuestProgress.of(player), new Actions() {
            public void go(Player p, State next) { show(coreUi, p, catalog, next); }
            public void move(Player p, Quest quest, Status status, String message, boolean good) {
                QuestProgress.set(p, quest, status);
                feedback(p, message, good);
                show(coreUi, p, catalog, state);
            }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    static void feedback(Player p, String message, boolean good) {
        Messages.send(p, message);
        if (good) p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
        else p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 0.8f);
    }

    /** One frame; pure apart from the callbacks, so offline checks and the preview tool can build it. */
    static CoreMenu build(Map<String, Glyph> g, QuestCatalog catalog, State state, QuestProgress.View progress, Actions act) {
        BookCanvas c = new BookCanvas(g, ID, "퀘스트");
        List<Quest> shown = catalog.quests().stream().filter(q -> progress.status(q) == state.filter()).toList();
        long active = catalog.quests().stream().filter(q -> progress.status(q) == Status.ACTIVE).count();
        int pages = Math.max(1, (shown.size() + ROWS - 1) / ROWS), page = Math.clamp(state.page(), 0, pages - 1);
        List<Quest> rows = shown.subList(Math.min(shown.size(), page * ROWS), Math.min(shown.size(), (page + 1) * ROWS));
        Quest selected = shown.stream().filter(q -> q.id().equals(state.selected())).findFirst()
                .orElse(rows.isEmpty() ? null : rows.getFirst());

        c.chrome("퀘스트", "menu/icon/quest", null, act::close);

        // Left page.
        c.text("퀘스트 일지", LEFT, TOP, INK);
        c.right("진행 중 " + active + " / " + QuestCatalog.MAX_ACTIVE, LEFT + PAGE, TOP, active >= QuestCatalog.MAX_ACTIVE ? RED : DIM);
        for (int i = 0; i < Status.JOURNAL.size(); i++) {
            Status s = Status.JOURNAL.get(i);
            boolean on = s == state.filter();
            c.button(on ? "dark" : "paper", 36, s.label, LEFT + i * 40, 43, on ? PAPER : INK, on ? null : p -> act.go(p, state.filter(s)));
        }
        c.right(shown.size() + "개", LEFT + PAGE, 49, DIM);
        if (shown.isEmpty()) {
            c.centered(state.filter() == Status.ACTIVE ? "진행 중인 퀘스트가 없어요" : "완료한 퀘스트가 없어요", LEFT, PAGE, 135, DIM);
            if (state.filter() == Status.ACTIVE) c.centered("마을 NPC에게 말을 걸어 의뢰를 받아 보세요", LEFT, PAGE, 160, DIM);
        }
        for (int i = 0; i < rows.size(); i++) {
            Quest q = rows.get(i);
            int y = ROW_Y[i];
            boolean picked = selected != null && selected.id().equals(q.id());
            Giver giver = catalog.giver(q.giver());
            c.image(picked ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            c.item(q.icon(), LEFT + 2, y + 2);
            String right = state.filter() == Status.DONE ? "완료" : q.complete() ? "완료 가능" : q.done() + " / " + q.total();
            c.right(right, LEFT + PAGE, y + 6, state.filter() == Status.DONE || q.complete() ? GREEN : INK);
            int w = c.width(right) + 4;
            c.fitted(q.name(), LEFT + 25, y + 1, PAGE - 25 - w, picked ? GREEN : INK);
            c.fitted(giver == null ? "" : "의뢰인 " + giver.name(), LEFT + 25, y + 11, PAGE - 25 - w, DIM);
            c.click(LEFT, y, PAGE, 20, p -> act.go(p, state.select(q)), null);
        }
        c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, page > 0 ? p -> act.go(p, state.page(page - 1)) : null);
        c.centered((page + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, page + 1 < pages ? p -> act.go(p, state.page(page + 1)) : null);

        // Right page: progress-first detail.
        if (selected == null) {
            c.centered("퀘스트를 고르면 내용이 보여요", RIGHT, PAGE, 140, DIM);
            return c.build();
        }
        Quest q = selected;
        boolean done = progress.status(q) == Status.DONE;
        Giver giver = catalog.giver(q.giver());
        String stamp = done ? "완료" : q.complete() ? "완료 가능" : "진행 중";
        c.right(stamp, RIGHT + PAGE, TOP, done || q.complete() ? GREEN : DIM);
        c.fitted(q.name(), RIGHT, TOP, PAGE - c.width(stamp) - 6, INK);
        if (giver != null) c.fitted("의뢰인 " + giver.name(), RIGHT, 43, PAGE, DIM);
        for (int i = 0; i < q.description().size() && i < 2; i++) c.fitted(q.description().get(i), RIGHT, 56 + i * 11, PAGE, BODY);

        c.image("shop/ui/divider_200", RIGHT, 80);
        c.text("목표", RIGHT, 85, DIM);
        c.right(q.done() + " / " + q.total(), RIGHT + PAGE, 85, q.complete() ? GREEN : DIM);
        for (int i = 0; i < q.objectives().size() && i < GOAL_Y.length; i++) {
            Objective o = q.objectives().get(i);
            int y = GOAL_Y[i];
            c.image(o.complete() ? "dex/ui/slot_selected" : "dex/ui/slot_normal", RIGHT, y);
            c.item(o.icon(), RIGHT + 2, y + 2);
            c.fitted(o.text(), RIGHT + 25, y + 1, 110, o.complete() ? GREEN : INK);
            c.bar(RIGHT + 25, y + 12, o.total() == 0 ? 1 : Math.min(o.done(), o.total()) / (double) o.total());
            c.right(Math.min(o.done(), o.total()) + " / " + o.total(), RIGHT + PAGE, y + 6, o.complete() ? GREEN : INK);
        }

        c.image("shop/ui/divider_200", RIGHT, 178);
        c.text("보상", RIGHT, 184, DIM);
        if (done) c.right("받음", RIGHT + PAGE, 184, GREEN);
        c.rewards(q.rewards(), RIGHT, 198, RIGHT + PAGE);

        c.button("paper", 98, "위치 찾기", RIGHT, BUTTON_Y, INK, p -> {
            Messages.send(p, "[프로토타입] " + (giver == null ? "의뢰인" : giver.name()) + " 위치 안내는 아직 연결되지 않았어요.");
        });
        int bx = RIGHT + PAGE - 98;
        if (done) c.button("off", 98, "완료한 퀘스트", bx, BUTTON_Y, DIM, null);
        else if (q.complete()) c.button("green", 98, "완료하기", bx, BUTTON_Y, WHITE,
                p -> act.move(p, q, Status.DONE, "'" + q.name() + "' 완료! [프로토타입] 보상은 지급되지 않아요.", true));
        else {
            c.button("paper", 98, "포기하기", bx, BUTTON_Y, RED, null);
            c.click(bx, BUTTON_Y, 98, 20, p -> act.move(p, q, Status.OFFERED, "'" + q.name() + "' 퀘스트를 포기했어요. 의뢰인에게 다시 받을 수 있어요.", false),
                    HoverEvent.showText(Component.text("진행도는 사라지고, 의뢰인에게 다시 받을 수 있어요")));
        }
        return c.build();
    }
}
