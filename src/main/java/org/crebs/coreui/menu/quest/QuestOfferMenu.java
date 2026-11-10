package org.crebs.coreui.menu.quest;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.quest.QuestCatalog.Giver;
import org.crebs.coreui.menu.quest.QuestCatalog.Objective;
import org.crebs.coreui.menu.quest.QuestCatalog.Quest;
import org.crebs.coreui.menu.quest.QuestCatalog.Status;

import java.util.List;
import java.util.Map;

import org.crebs.coreui.menu.book.BookCanvas;

import static org.crebs.coreui.menu.book.BookCanvas.*;

/**
 * What an NPC quest giver shows (prototype), in the same book layout. Left page: the NPC (portrait well, name,
 * greeting) and the quests they hand out with their state. Right page: the selected quest as the NPC tells
 * it (speech bubble), its objectives and reward slots, and 거절 / 수락 (or 일지 열기 / 완료 보고 once accepted).
 * Opened with {@code /coreui quest npc <giver> [player]}, e.g. from a Citizens NPC console command.
 */
public final class QuestOfferMenu {
    static final String ID = "kokonut-quest-offer";
    private static final int[] ROW_Y = {99, 126, 153, 180, 207};

    record State(String giver, String selected) {
        State select(Quest q) { return new State(giver, q.id()); }
    }

    interface Actions {
        void go(Player p, State next);
        void move(Player p, Quest quest, Status status, String message, boolean good);
        void journal(Player p);
        void close(Player p);
    }

    private QuestOfferMenu() {
    }

    /** False while the glyphs are not loaded; {@code giver} must exist (see {@link #givers()}). */
    public static boolean open(CoreUi coreUi, Player player, String giver) {
        if (!QuestMenu.ready(coreUi) || QuestCatalog.SAMPLE.giver(giver) == null) return false;
        player.playSound(player, Sound.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.6f, 1f);
        show(coreUi, player, QuestCatalog.SAMPLE, new State(giver, null));
        return true;
    }

    public static List<String> givers() {
        return QuestCatalog.SAMPLE.givers().stream().map(Giver::id).toList();
    }

    private static void show(CoreUi coreUi, Player player, QuestCatalog catalog, State state) {
        if (!player.isOnline()) return;
        coreUi.open(player, build(coreUi.getGlyphs().all(), catalog, state, QuestProgress.of(player), new Actions() {
            public void go(Player p, State next) { show(coreUi, p, catalog, next); }
            public void move(Player p, Quest quest, Status status, String message, boolean good) {
                QuestProgress.set(p, quest, status);
                QuestMenu.feedback(p, message, good);
                show(coreUi, p, catalog, state.select(quest));
            }
            public void journal(Player p) { QuestMenu.show(coreUi, p, catalog, QuestMenu.State.START); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    /** One frame; pure apart from the callbacks, so offline checks and the preview tool can build it. */
    static CoreMenu build(Map<String, Glyph> g, QuestCatalog catalog, State state, QuestProgress.View progress, Actions act) {
        BookCanvas c = new BookCanvas(g, ID, "의뢰");
        Giver giver = catalog.giver(state.giver());
        // Finished quests drop off the NPC's list (they stay in the journal's 완료 filter).
        List<Quest> quests = catalog.from(giver).stream().filter(q -> progress.status(q) != Status.DONE).toList();
        long active = catalog.quests().stream().filter(q -> progress.status(q) == Status.ACTIVE).count();
        // Default selection: the first quest that can be accepted, else the first one.
        Quest selected = quests.stream().filter(q -> q.id().equals(state.selected())).findFirst()
                .or(() -> quests.stream().filter(q -> progress.status(q) == Status.OFFERED).findFirst())
                .orElse(quests.isEmpty() ? null : quests.getFirst());

        c.chrome("의뢰", null, "writable_book", act::close);

        // Left page: the NPC, then their quests.
        c.image("menu/ui/well", LEFT, 36);
        c.item(giver.icon(), LEFT + 10, 47);
        c.fitted(giver.name(), LEFT + 44, 41, PAGE - 44, INK);
        List<String> hello = giver.greeting();
        for (int i = 0; i < hello.size() && i < 2; i++)
            c.fitted((i == 0 ? "“" : "") + hello.get(i) + (i == Math.min(hello.size(), 2) - 1 ? "”" : ""), LEFT + 44, 54 + i * 11, PAGE - 44, BODY);
        c.image("shop/ui/divider_200", LEFT, 80);
        long open = quests.stream().filter(q -> progress.status(q) == Status.OFFERED).count();
        c.text("받을 수 있는 의뢰", LEFT, 85, DIM);
        c.right(open + " / " + quests.size() + "건", LEFT + PAGE, 85, DIM);
        if (quests.isEmpty()) c.centered("지금은 부탁할 일이 없대요", LEFT, PAGE, 140, DIM);
        for (int i = 0; i < quests.size() && i < ROW_Y.length; i++) {
            Quest q = quests.get(i);
            int y = ROW_Y[i];
            Status s = progress.status(q);
            boolean picked = selected != null && selected.id().equals(q.id());
            c.image(picked ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            c.item(q.icon(), LEFT + 2, y + 2);
            int w = s == Status.OFFERED && !q.rewards().isEmpty() && q.rewards().getFirst().kind() == QuestCatalog.Reward.Kind.GOLD
                    ? c.goldRight(q.rewards().getFirst().amount(), LEFT + PAGE, y + 2, INK) : 0;
            c.fitted(q.name(), LEFT + 25, y + 1, PAGE - 29 - w, picked ? GREEN : s == Status.OFFERED ? INK : DIM);
            String sub = switch (s) {
                case OFFERED -> "수락 가능";
                case ACTIVE -> q.complete() ? "완료 보고 가능" : "진행 중 · " + q.done() + " / " + q.total();
                case DONE -> "완료함";
            };
            c.fitted(sub, LEFT + 25, y + 11, PAGE - 29 - w, s == Status.DONE || (s == Status.ACTIVE && q.complete()) ? GREEN : DIM);
            c.click(LEFT, y, PAGE, 20, p -> act.go(p, state.select(q)), null);
        }

        // Right page: the NPC tells the quest.
        if (selected == null) {
            c.centered("지금은 부탁할 일이 없대요", RIGHT, PAGE, 140, DIM);
            return c.build();
        }
        Quest q = selected;
        Status s = progress.status(q);
        c.fitted(q.name(), RIGHT, TOP, PAGE, INK);
        c.image("quest/ui/speech_200", RIGHT, 43);
        List<String> lines = q.description();
        for (int i = 0; i < lines.size() && i < 3; i++)
            c.fitted((i == 0 ? "“" : "") + lines.get(i) + (i == Math.min(lines.size(), 3) - 1 ? "”" : ""), RIGHT + 6, 50 + i * 11, PAGE - 12, BODY);

        c.text("목표", RIGHT, 96, DIM);
        for (int i = 0; i < q.objectives().size() && i < 3; i++) {
            Objective o = q.objectives().get(i);
            int y = 108 + i * 11;
            c.fitted("· " + o.text(), RIGHT, y, PAGE - 40, INK);
            c.right(s == Status.OFFERED ? "× " + o.total() : Math.min(o.done(), o.total()) + " / " + o.total(), RIGHT + PAGE, y,
                    s != Status.OFFERED && o.complete() ? GREEN : INK);
        }
        c.image("shop/ui/divider_200", RIGHT, 143);
        c.text("보상", RIGHT, 157, DIM);
        c.rewards(q.rewards(), RIGHT, 171, RIGHT + PAGE);

        String hint = switch (s) {
            case OFFERED -> active >= QuestCatalog.MAX_ACTIVE ? "진행 중인 퀘스트가 가득 찼어요 (최대 " + QuestCatalog.MAX_ACTIVE + "개)"
                    : "수락하면 퀘스트 일지에 추가돼요";
            case ACTIVE -> q.complete() ? "목표를 모두 채웠어요! 보고해 주세요" : "진행 중인 의뢰예요";
            case DONE -> "이미 완료한 의뢰예요";
        };
        c.centered(hint, RIGHT, PAGE, 238, s == Status.OFFERED && active >= QuestCatalog.MAX_ACTIVE ? RED : DIM);

        int left = RIGHT, rightX = RIGHT + PAGE - 98;
        switch (s) {
            case OFFERED -> {
                c.button("paper", 98, "거절", left, BUTTON_Y, INK,
                        p -> { QuestMenu.feedback(p, giver.name() + ": “그래요, 마음이 바뀌면 다시 와요.”", false); act.close(p); });
                if (active >= QuestCatalog.MAX_ACTIVE) c.button("off", 98, "진행 칸 가득", rightX, BUTTON_Y, RED, null);
                else c.button("green", 98, "수락", rightX, BUTTON_Y, WHITE,
                        p -> act.move(p, q, Status.ACTIVE, "'" + q.name() + "' 의뢰를 수락했어요. 퀘스트 일지에서 확인하세요.", true));
            }
            case ACTIVE -> {
                c.button("paper", 98, "일지 열기", left, BUTTON_Y, INK, act::journal);
                if (q.complete()) c.button("green", 98, "완료 보고", rightX, BUTTON_Y, WHITE,
                        p -> act.move(p, q, Status.DONE, "'" + q.name() + "' 완료! [프로토타입] 보상은 지급되지 않아요.", true));
                else c.button("off", 98, "진행 중", rightX, BUTTON_Y, DIM, null);
            }
            case DONE -> {
                c.button("paper", 98, "일지 열기", left, BUTTON_Y, INK, act::journal);
                c.button("off", 98, "완료함", rightX, BUTTON_Y, DIM, null);
            }
        }
        return c.build();
    }
}
