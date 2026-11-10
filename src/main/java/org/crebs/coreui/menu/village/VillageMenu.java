package org.crebs.coreui.menu.village;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.book.BookCanvas;
import org.crebs.coreui.menu.village.VillageStore.Member;
import org.crebs.coreui.menu.village.VillageStore.Role;
import org.crebs.coreui.menu.village.VillageStore.Village;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.crebs.coreui.menu.book.BookCanvas.*;

/**
 * Village screens (prototype), opened from the main menu's green 마을 tab. They follow Kokonut-Core's village model
 * ({@code kr.gomsv.kokonutCore.village}: name, grade 0..6, OWNER/OFFICER/MEMBER, chunks, home, founding date); Core
 * has no public village API yet, so the data is {@link VillageStore}'s sample village. Three views share the book
 * and the tab row (메인 메뉴 red/inactive, 마을 green/active):
 * <ul>
 *   <li>{@link View#MAIN}: village card (the icon well opens the icon picker for the 마을장), home, 마을로 이동 /
 *       주민 목록; right page = the 마을장's notice (written in a multi-line dialog), 마을 테크 (later) / 공지 수정.</li>
 *   <li>{@link View#MEMBERS}: 8 px skin heads with nicknames and roles (마을장, 부마을장, then by join date); the
 *       selected member's details and own introduction (소개 수정 on your own row) and, for the 마을장,
 *       부마을장 지정·해제 and 추방 (asks once more).</li>
 *   <li>{@link View#ICON}: a grid of icons, a preview, 취소 / 적용.</li>
 * </ul>
 * PROTOTYPE: everyone is treated as the 마을장, changes live in server memory, 이동 and 마을 테크 only explain
 * themselves.
 */
public final class VillageMenu {
    static final String ID = "kokonut-village";
    static final int MAX_GRADE = 6;
    /** Member rows, 18 px apart on 9 px lines (the client's head object lands exactly there). */
    private static final int[] MEMBER_Y = {45, 63, 81, 99, 117, 135, 153, 171, 189, 207, 225};
    /** Notice lines; none starts at y 145..152, where the lower book tiles would cover it. */
    private static final int[] NOTICE_Y = {49, 60, 71, 82, 93, 104, 115, 126, 137, 159, 170, 181, 192, 203, 214, 225};
    /** Icon picker: 36 px wells with 32 px icons, 5 per row; no well crosses the book seam at y 153. */
    private static final int[] ICON_Y = {45, 87, 153, 195};
    private static final int ICON_COLS = 5, ICONS_PER_PAGE = ICON_COLS * 4;
    /** Icon choices: {@code vil} is the hand-drawn village icon, the rest are vanilla item keys (32 px glyphs). */
    static final List<String[]> ICONS = List.of(
            new String[]{"vil", "마을"}, new String[]{"emerald", "에메랄드"}, new String[]{"diamond", "다이아몬드"},
            new String[]{"gold_ingot", "금 주괴"}, new String[]{"iron_ingot", "철 주괴"}, new String[]{"amethyst_shard", "자수정"},
            new String[]{"heart_of_the_sea", "바다의 심장"}, new String[]{"nether_star", "네더의 별"},
            new String[]{"wheat", "밀"}, new String[]{"oak_sapling", "참나무 묘목"}, new String[]{"cherry_sapling", "벚나무 묘목"},
            new String[]{"bamboo", "대나무"}, new String[]{"sweet_berries", "달콤한 열매"}, new String[]{"apple", "사과"},
            new String[]{"golden_apple", "황금 사과"}, new String[]{"bread", "빵"}, new String[]{"cake", "케이크"},
            new String[]{"honeycomb", "벌집 조각"}, new String[]{"cod", "대구"}, new String[]{"nautilus_shell", "앵무조개 껍데기"},
            new String[]{"fishing_rod", "낚싯대"}, new String[]{"iron_pickaxe", "철 곡괭이"}, new String[]{"iron_sword", "철 검"},
            new String[]{"bow", "활"}, new String[]{"trident", "삼지창"}, new String[]{"blaze_rod", "블레이즈 막대"},
            new String[]{"compass", "나침반"}, new String[]{"filled_map", "지도"}, new String[]{"lantern", "랜턴"},
            new String[]{"writable_book", "책과 깃펜"}, new String[]{"feather", "깃털"}, new String[]{"ender_pearl", "엔더 진주"},
            new String[]{"slime_ball", "슬라임 볼"});

    enum View { MAIN, MEMBERS, ICON }

    /** {@code member}: selected on MEMBERS; {@code confirmKick}: 추방 asked once; {@code icon}: picked on ICON. */
    record State(View view, String member, boolean confirmKick, String icon, int page) {
        static final State MAIN = new State(View.MAIN, null, false, null, 0);

        static State members(String member) { return new State(View.MEMBERS, member, false, null, 0); }
        static State icon(String picked) { return new State(View.ICON, null, false, picked, 0); }
        State page(int p) { return new State(view, null, false, icon, p); }
        State confirm() { return new State(view, member, true, icon, page); }
    }

    interface Actions {
        void go(Player p, State next);
        void mainMenu(Player p);
        void editNotice(Player p, Village v);
        void editIntro(Player p, Member m);
        void toggleOfficer(Player p, Member m);
        void kick(Player p, Member m);
        void setIcon(Player p, String key);
        void tell(Player p, String message);
        void close(Player p);
    }

    private VillageMenu() {
    }

    /** False while the glyphs are not loaded (pack still generating or an older pack on the server). */
    public static boolean open(CoreUi coreUi, Player player) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("dex/ui/page_0_0") || !g.containsKey("menu/ui/tab_green") || !g.containsKey("menu/icon/vil_32") || !g.containsKey("menu/ui/well_selected")
                || !g.containsKey("dex/ui/btn_paper_98")) return false;
        player.playSound(player, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.6f, 1.2f);
        show(coreUi, player, State.MAIN);
        return true;
    }

    private static void show(CoreUi coreUi, Player player, State state) {
        if (!player.isOnline()) return;
        coreUi.open(player, build(coreUi.getGlyphs().all(), VillageStore.snapshot(), state, true, new Actions() {
            public void go(Player p, State next) { show(coreUi, p, next); }
            public void mainMenu(Player p) {
                if (!org.crebs.coreui.menu.kokonut.MainMenu.open(coreUi, p)) Messages.send(p, "메인 메뉴를 아직 불러오지 못했어요.");
            }
            public void editNotice(Player p, Village v) { askNotice(coreUi, p, v); }
            public void editIntro(Player p, Member m) { askIntro(coreUi, p, m); }
            public void toggleOfficer(Player p, Member m) {
                VillageStore.toggleOfficer(m.name());
                boolean now = m.role() != Role.OFFICER;
                feedback(p, m.name() + "님을 " + (now ? "부마을장으로 지정했어요." : "부마을장에서 해제했어요."), true);
                show(coreUi, p, State.members(m.name()));
            }
            public void kick(Player p, Member m) {
                VillageStore.kick(m.name());
                feedback(p, m.name() + "님을 마을에서 추방했어요.", false);
                show(coreUi, p, State.members(null));
            }
            public void setIcon(Player p, String key) {
                VillageStore.setIcon(key);
                feedback(p, "마을 아이콘을 바꿨어요.", true);
                show(coreUi, p, State.MAIN);
            }
            public void tell(Player p, String message) { feedback(p, message, false); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    private static void feedback(Player p, String message, boolean good) {
        Messages.send(p, "[프로토타입] " + message);
        if (good) p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
        else p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 0.8f);
    }

    /** Multi-line notice editor (Paper dialog); 저장 stores it and reopens the village screen. */
    private static void askNotice(CoreUi coreUi, Player player, Village v) {
        coreUi.removeSession(player);
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton save = ActionButton.builder(Component.text("저장")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi, () -> {
                    String text = Objects.requireNonNullElse(response.getText("notice"), "").strip();
                    VillageStore.setNotice(text, org.crebs.coreui.menu.kokonut.CoreBalance.displayName(player));
                    feedback(player, text.isEmpty() ? "공지를 지웠어요." : "공지를 저장했어요.", true);
                    show(coreUi, player, State.MAIN);
                }), once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("취소")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi, () -> show(coreUi, player, State.MAIN)), once)).build();
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("마을 공지 수정"))
                        .canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(List.of(DialogBody.plainMessage(Component.text("주민 모두가 마을 화면에서 보는 공지예요. 비우고 저장하면 지워져요."), 260)))
                        .inputs(List.of(DialogInput.text("notice", Component.text("공지"))
                                .initial(v.notice()).maxLength(400).width(300)
                                .multiline(TextDialogInput.MultilineOptions.create(12, 140)).build()))
                        .build())
                .type(DialogType.confirmation(save, cancel))));
    }

    /** Multi-line introduction editor for the viewer's own member row. */
    private static void askIntro(CoreUi coreUi, Player player, Member m) {
        coreUi.removeSession(player);
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton save = ActionButton.builder(Component.text("저장")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi, () -> {
                    String text = Objects.requireNonNullElse(response.getText("intro"), "").strip();
                    VillageStore.setIntro(m.name(), text);
                    feedback(player, text.isEmpty() ? "소개글을 지웠어요." : "소개글을 저장했어요.", true);
                    show(coreUi, player, State.members(m.name()));
                }), once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("취소")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi, () -> show(coreUi, player, State.members(m.name()))), once)).build();
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("내 소개 수정"))
                        .canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(List.of(DialogBody.plainMessage(Component.text("주민 목록에서 다른 주민들이 보는 소개글이에요."), 260)))
                        .inputs(List.of(DialogInput.text("intro", Component.text("소개"))
                                .initial(m.intro()).maxLength(200).width(300)
                                .multiline(TextDialogInput.MultilineOptions.create(6, 90)).build()))
                        .build())
                .type(DialogType.confirmation(save, cancel))));
    }

    private static void onMain(CoreUi coreUi, Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(coreUi, task);
    }

    /**
     * One frame; pure apart from the callbacks, so offline checks and the preview tool can build it.
     * {@code owner}: the viewer is the 마을장 (management buttons shown).
     */
    static CoreMenu build(Map<String, Glyph> g, Village v, State state, boolean owner, Actions act) {
        BookCanvas c = new BookCanvas(g, ID, "마을");
        c.pages();
        // Tab row: 메인 메뉴 (red, inactive, goes back) and 마을 (green, active, joined to the book).
        c.image("menu/ui/tab_off", 16, 0);
        c.image("menu/icon/ego", 20, 2);
        c.text("메인 메뉴", 38, 6, WHITE);
        c.click(16, 0, 88, 20, act::mainMenu, null);
        c.image("menu/ui/tab_green", 108, 0);
        c.image("menu/icon/vil", 112, 2);
        c.text("마을", 130, 6, WHITE);
        c.image("menu/ui/tab_green_join", 109, 20);
        c.close(act::close);
        switch (state.view()) {
            case MAIN -> main(c, v, owner, act);
            case MEMBERS -> members(c, v, state, owner, act);
            case ICON -> icons(c, v, state, act);
        }
        return c.build();
    }

    /** Village icon at 32 px inside a 36 px well at ({@code wellX}, {@code wellY}): the hand-drawn village icon or a
     * vanilla item's {@code village/icon/<key>} glyph (any y); the 16 px item icon if the 32 px one is missing. */
    private static void icon(BookCanvas c, String key, int wellX, int wellY) {
        if ("vil".equals(key)) c.image("menu/icon/vil_32", wellX + 2, wellY + 2);
        else if (c.g.containsKey("village/icon/" + key)) c.image("village/icon/" + key, wellX + 2, wellY + 2);
        else c.item(key, wellX + 10, wellY + 13);
    }

    private static String iconName(String key) {
        return ICONS.stream().filter(i -> i[0].equals(key)).map(i -> i[1]).findFirst().orElse(key);
    }

    private static void main(BookCanvas c, Village v, boolean owner, Actions act) {
        Member leader = v.owner();
        c.fitted(v.name(), LEFT, TOP, PAGE - 50, INK);
        c.right(v.grade() + "등급", LEFT + PAGE, TOP, GREEN);
        c.image("menu/ui/well", LEFT, 43);
        icon(c, v.icon(), LEFT, 43);
        if (owner) c.click(LEFT, 43, 36, 36, p -> act.go(p, State.icon(v.icon())),
                HoverEvent.showText(Component.text("마을 아이콘 바꾸기 (마을장)")));
        c.fitted("마을장 " + (leader == null ? "-" : leader.name()), LEFT + 44, 46, PAGE - 44, INK);
        c.fitted("주민 " + v.members().size() + "명 · 영토 " + v.chunks() + "청크", LEFT + 44, 58, PAGE - 44, DIM);
        for (int i = 0; i < MAX_GRADE; i++) c.image(i < v.grade() ? "dex/ui/pip_on" : "dex/ui/pip_off", LEFT + 44 + i * 8, 72);
        c.text("등급 " + v.grade() + " / " + MAX_GRADE, LEFT + 44 + MAX_GRADE * 8 + 4, 71, DIM);

        c.image("shop/ui/divider_200", LEFT, 85);
        String[][] rows = {{"마을 위치", v.home()}, {"창설일", v.founded()}};
        for (int i = 0; i < rows.length; i++) {
            c.text(rows[i][0], LEFT, 91 + i * 12, DIM);
            c.right(rows[i][1], LEFT + PAGE, 91 + i * 12, INK);
        }
        c.button("paper", 98, "마을로 이동", LEFT, BUTTON_Y, INK, p -> act.tell(p, "마을 홈(" + v.home() + ")으로 이동은 아직 연결되지 않았어요."));
        c.button("paper", 98, "주민 목록", LEFT + 102, BUTTON_Y, INK, p -> act.go(p, State.members(null)));

        // Right page: the 마을장's notice.
        c.text("마을 공지", RIGHT, TOP, INK);
        if (!v.notice().isEmpty()) c.right(v.noticeBy() + " · " + v.noticeAt(), RIGHT + PAGE, TOP, DIM);
        c.image("shop/ui/divider_200", RIGHT, 43);
        if (v.notice().isEmpty()) {
            c.centered("아직 공지가 없어요", RIGHT, PAGE, 120, DIM);
            if (owner) c.centered("공지 수정을 눌러 주민들에게 알려 보세요", RIGHT, PAGE, 132, DIM);
        } else {
            List<String> lines = c.wrap(v.notice(), PAGE);
            for (int i = 0; i < lines.size() && i < NOTICE_Y.length; i++) {
                boolean last = i == NOTICE_Y.length - 1 && lines.size() > NOTICE_Y.length;
                c.text(last ? lines.get(i) + " …" : lines.get(i), RIGHT, NOTICE_Y[i], BODY);
            }
        }
        c.button("paper", 98, "마을 테크", RIGHT, BUTTON_Y, INK, p -> act.tell(p, "마을 테크(기술 트리) 화면은 추후 구현 예정이에요."));
        if (owner) c.button("paper", 98, "공지 수정", RIGHT + PAGE - 98, BUTTON_Y, INK, p -> act.editNotice(p, v));
    }

    private static void members(BookCanvas c, Village v, State state, boolean owner, Actions act) {
        List<Member> members = v.members();
        int pages = Math.max(1, (members.size() + MEMBER_Y.length - 1) / MEMBER_Y.length);
        int page = Math.clamp(state.page(), 0, pages - 1);
        List<Member> rows = members.subList(Math.min(members.size(), page * MEMBER_Y.length),
                Math.min(members.size(), (page + 1) * MEMBER_Y.length));
        Member selected = members.stream().filter(m -> m.name().equals(state.member())).findFirst()
                .orElse(rows.isEmpty() ? null : rows.getFirst());

        c.text("주민 목록", LEFT, TOP, INK);
        c.right(members.size() + "명", LEFT + PAGE, TOP, DIM);
        for (int i = 0; i < rows.size(); i++) {
            Member m = rows.get(i);
            int y = MEMBER_Y[i];
            boolean picked = selected != null && selected.name().equals(m.name());
            c.head(head(m), LEFT, y);
            c.fitted(m.name(), LEFT + 11, y, PAGE - 70, picked ? GREEN : INK);
            c.right(m.role().label, LEFT + PAGE, y, m.role() == Role.MEMBER ? DIM : GREEN);
            c.click(LEFT, y - 4, PAGE, 17, p -> act.go(p, State.members(m.name())), null);
        }
        c.button("paper", 98, "돌아가기", LEFT, BUTTON_Y, INK, p -> act.go(p, State.MAIN));
        if (pages > 1) {
            c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, page > 0 ? p -> act.go(p, state.page(page - 1)) : null);
            c.centered((page + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
            c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, page + 1 < pages ? p -> act.go(p, state.page(page + 1)) : null);
        }

        // Right page: the selected member.
        if (selected == null) return;
        Member m = selected;
        c.head(head(m), RIGHT, 36);
        c.fitted(m.name(), RIGHT + 11, 36, PAGE - 70, INK);
        c.right(m.role().label, RIGHT + PAGE, 36, m.role() == Role.MEMBER ? DIM : GREEN);
        c.image("shop/ui/divider_200", RIGHT, 50);
        String[][] info = {{"역할", m.role().label}, {"가입일", m.joined()}, {"최근 접속", m.lastSeen()}};
        for (int i = 0; i < info.length; i++) {
            c.text(info[i][0], RIGHT, 56 + i * 12, DIM);
            c.right(info[i][1], RIGHT + PAGE, 56 + i * 12, INK);
        }
        // The member's own introduction (wrapped; no line starts on the book seam at y 145..152).
        c.image("shop/ui/divider_200", RIGHT, 96);
        boolean self = m.role() == Role.OWNER; // PROTOTYPE: the viewer is the 마을장
        c.text("소개", RIGHT, 102, DIM);
        if (m.intro().isEmpty()) c.text(self ? "소개 수정을 눌러 나를 소개해 보세요" : "아직 소개글이 없어요", RIGHT, 115, DIM);
        else {
            List<String> lines = c.wrap(m.intro(), PAGE);
            int[] ys = {115, 126, 137, 159, 170, 181, 192, 203, 214};
            for (int i = 0; i < lines.size() && i < ys.length; i++)
                c.text(i == ys.length - 1 && lines.size() > ys.length ? lines.get(i) + " …" : lines.get(i), RIGHT, ys[i], BODY);
        }
        if (state.confirmKick()) c.centered("한 번 더 누르면 " + m.name() + "님을 추방해요", RIGHT, PAGE, 238, RED);

        // Buttons: your own row edits your introduction; the 마을장 manages everyone else.
        int bx = RIGHT + PAGE - 98;
        if (self) {
            c.button("paper", 98, "소개 수정", RIGHT, BUTTON_Y, INK, p -> act.editIntro(p, m));
            c.button("off", 98, "추방 불가", bx, BUTTON_Y, DIM, null);
        } else if (owner && m.role() != Role.OWNER) {
            if (m.role() == Role.MEMBER) c.button("green", 98, "부마을장 지정", RIGHT, BUTTON_Y, WHITE, p -> act.toggleOfficer(p, m));
            else c.button("paper", 98, "부마을장 해제", RIGHT, BUTTON_Y, INK, p -> act.toggleOfficer(p, m));
            if (state.confirmKick()) c.button("paper", 98, "정말 추방", bx, BUTTON_Y, RED, p -> act.kick(p, m));
            else c.button("paper", 98, "추방", bx, BUTTON_Y, RED, p -> act.go(p, State.members(m.name()).confirm()));
        }
    }

    private static net.kyori.adventure.text.object.PlayerHeadObjectContents head(Member m) {
        return ObjectContents.playerHead().name(m.account()).hat(true).build();
    }

    private static void icons(BookCanvas c, Village v, State state, Actions act) {
        String picked = state.icon() == null ? v.icon() : state.icon();
        c.text("마을 아이콘 고르기", LEFT, TOP, INK);
        c.right(ICONS.size() + "개", LEFT + PAGE, TOP, DIM);
        int pages = Math.max(1, (ICONS.size() + ICONS_PER_PAGE - 1) / ICONS_PER_PAGE), page = Math.clamp(state.page(), 0, pages - 1);
        for (int i = 0; i < ICONS_PER_PAGE && page * ICONS_PER_PAGE + i < ICONS.size(); i++) {
            String[] option = ICONS.get(page * ICONS_PER_PAGE + i);
            int x = LEFT + (i % ICON_COLS) * 41, y = ICON_Y[i / ICON_COLS];
            c.image(option[0].equals(picked) ? "menu/ui/well_selected" : "menu/ui/well", x, y);
            icon(c, option[0], x, y);
            c.click(x, y, 36, 36, p -> act.go(p, new State(View.ICON, null, false, option[0], page)), HoverEvent.showText(Component.text(option[1])));
        }
        c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, page > 0 ? p -> act.go(p, new State(View.ICON, null, false, picked, page - 1)) : null);
        c.centered((page + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, page + 1 < pages ? p -> act.go(p, new State(View.ICON, null, false, picked, page + 1)) : null);

        // Right page: preview, 취소 / 적용.
        c.text("미리보기", RIGHT, TOP, INK);
        c.image("menu/ui/well", RIGHT, 43);
        icon(c, picked, RIGHT, 43);
        c.fitted(v.name(), RIGHT + 44, 49, PAGE - 44, INK);
        c.fitted(iconName(picked), RIGHT + 44, 61, PAGE - 44, picked.equals(v.icon()) ? DIM : GREEN);
        c.image("shop/ui/divider_200", RIGHT, 85);
        c.text("현재 아이콘", RIGHT, 91, DIM);
        c.right(iconName(v.icon()), RIGHT + PAGE, 91, INK);
        c.fitted("마을 화면과 마을 목록에 보여요.", RIGHT, 106, PAGE, BODY);
        c.fitted("마을장만 바꿀 수 있어요.", RIGHT, 118, PAGE, BODY);
        c.button("paper", 98, "취소", RIGHT, BUTTON_Y, INK, p -> act.go(p, State.MAIN));
        if (picked.equals(v.icon())) c.button("off", 98, "적용", RIGHT + PAGE - 98, BUTTON_Y, DIM, null);
        else c.button("green", 98, "적용", RIGHT + PAGE - 98, BUTTON_Y, WHITE, p -> act.setIcon(p, picked));
    }
}
