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
import org.crebs.coreui.menu.village.VillageStore.Invite;
import org.crebs.coreui.menu.village.VillageStore.Member;
import org.crebs.coreui.menu.village.VillageStore.Overview;
import org.crebs.coreui.menu.village.VillageStore.Role;
import org.crebs.coreui.menu.village.VillageStore.Village;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import static org.crebs.coreui.menu.book.BookCanvas.*;

/**
 * Village screens, opened from the main menu's green 마을 tab or {@code /마을}. Data and every change go through
 * Kokonut-Core's village service ({@link VillageBridge}); Core checks permissions again, so the buttons shown here
 * are only a convenience. Views share the book and the tab row (메인 메뉴 red/inactive, 마을 green/active):
 * <ul>
 *   <li>{@link View#NONE}: no village yet: founding (cost, 마을 창설 → name dialog) and received invitations
 *       (수락 / 거절).</li>
 *   <li>{@link View#MAIN}: village card (the icon well opens the icon picker for the 마을장), home, 마을로 이동 /
 *       주민 목록; right page = the notice (마을장·부마을장 edit it in a multi-line dialog), 마을 테크 (later).</li>
 *   <li>{@link View#MEMBERS}: 8 px skin heads with nicknames and roles (마을장, 부마을장, then by join date), 초대 for
 *       마을장·부마을장; the selected member's details and own introduction (소개 수정 / 탈퇴 on your own row),
 *       부마을장 지정·해제 (마을장) and 추방 (마을장: anyone, 부마을장: 주민; asks once more).</li>
 *   <li>{@link View#ICON}: a grid of icons, a preview, 취소 / 적용.</li>
 * </ul>
 * Navigation between views reuses the loaded snapshot; after a change the snapshot is loaded again.
 */
public final class VillageMenu {
    static final String ID = "kokonut-village";
    static final int MAX_GRADE = 6;
    /** Member rows, 18 px apart on 9 px lines (the client's head object lands exactly there). */
    private static final int[] MEMBER_Y = {45, 63, 81, 99, 117, 135, 153, 171, 189, 207, 225};
    /** Invitation rows on the right page of {@link View#NONE} (above the book seam). */
    private static final int[] INVITE_Y = {49, 63, 77, 91, 105, 119};
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

    enum View { NONE, MAIN, MEMBERS, ICON }

    /**
     * {@code member}: selected member (MEMBERS) or invitation's village id (NONE); {@code confirm}: 추방/탈퇴 asked once;
     * {@code icon}: picked on ICON.
     */
    record State(View view, String member, boolean confirm, String icon, int page) {
        static final State MAIN = new State(View.MAIN, null, false, null, 0);
        static final State NONE = new State(View.NONE, null, false, null, 0);

        static State members(String member) { return new State(View.MEMBERS, member, false, null, 0); }
        static State icon(String picked) { return new State(View.ICON, null, false, picked, 0); }
        static State invite(long village) { return new State(View.NONE, Long.toString(village), false, null, 0); }
        State page(int p) { return new State(view, null, false, icon, p); }
        State asking() { return new State(view, member, true, icon, page); }
    }

    /** A change's outcome from Core; {@code message} is shown to the player either way. */
    record Reply(boolean ok, String message) { }

    /** Where the data comes from and where changes go (Kokonut-Core through {@link VillageBridge}). */
    interface Backend {
        /** {@code done} gets null when loading failed. */
        void load(Player p, Consumer<Overview> done);
        void create(Player p, String name, Consumer<Reply> done);
        void invite(Player p, String target, Consumer<Reply> done);
        void accept(Player p, long village, Consumer<Reply> done);
        void decline(Player p, long village, Consumer<Reply> done);
        void leave(Player p, Consumer<Reply> done);
        void kick(Player p, UUID target, Consumer<Reply> done);
        void setRole(Player p, UUID target, Role role, Consumer<Reply> done);
        void setIcon(Player p, String icon, Consumer<Reply> done);
        void setNotice(Player p, String text, Consumer<Reply> done);
        void setIntro(Player p, String text, Consumer<Reply> done);
        void home(Player p, Consumer<Reply> done);
        /** Buys the chunk the player stands in (마을장·부마을장). */
        void claimChunk(Player p, Consumer<Reply> done);
    }

    /** What the buttons do; {@link #build} only draws. */
    interface Actions {
        void go(Player p, State next);
        void mainMenu(Player p);
        void create(Player p);
        void accept(Player p, Invite i);
        void decline(Player p, Invite i);
        void invite(Player p);
        void leave(Player p);
        void home(Player p);
        void claimChunk(Player p);
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

    /**
     * Starts loading and opens the screen when the data arrives. False while the glyphs are not loaded (pack still
     * generating or an older pack on the server) or Kokonut-Core has no village service.
     */
    public static boolean open(CoreUi coreUi, Player player) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("dex/ui/page_0_0") || !g.containsKey("menu/ui/tab_green") || !g.containsKey("menu/icon/vil_32") || !g.containsKey("menu/ui/well_selected")
                || !g.containsKey("dex/ui/btn_paper_98") || !g.containsKey("dex/ui/btn_paper_52")) return false;
        Backend backend = backend(coreUi);
        if (backend == null) return false;
        player.playSound(player, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.6f, 1.2f);
        reload(coreUi, backend, player, null);
        return true;
    }

    private static Backend backend(CoreUi coreUi) {
        if (!coreUi.getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) return null;
        try {
            return VillageBridge.find(coreUi.getServer());
        } catch (LinkageError olderCore) {
            return null;
        }
    }

    /** Loads the snapshot again, then shows {@code next} (null: the village's main page, or NONE without a village). */
    private static void reload(CoreUi coreUi, Backend backend, Player player, State next) {
        backend.load(player, overview -> {
            if (!player.isOnline()) return;
            if (overview == null) { Messages.send(player, "마을 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."); return; }
            State state = next;
            if (overview.village() == null) state = state != null && state.view() == View.NONE ? state : State.NONE;
            else if (state == null || state.view() == View.NONE) state = State.MAIN;
            show(coreUi, backend, player, overview, state);
        });
    }

    private static void show(CoreUi coreUi, Backend backend, Player player, Overview overview, State state) {
        if (!player.isOnline()) return;
        UUID viewer = player.getUniqueId();
        // A change's result: message, then fresh data on {@code next}.
        java.util.function.BiFunction<Player, State, Consumer<Reply>> after = (p, next) -> reply -> {
            feedback(p, reply.message(), reply.ok());
            reload(coreUi, backend, p, next);
        };
        coreUi.open(player, build(coreUi.getGlyphs().all(), overview, viewer, state, new Actions() {
            public void go(Player p, State next) { show(coreUi, backend, p, overview, next); }
            public void mainMenu(Player p) {
                if (!org.crebs.coreui.menu.kokonut.MainMenu.open(coreUi, p)) Messages.send(p, "메인 메뉴를 아직 불러오지 못했어요.");
            }
            public void create(Player p) { askName(coreUi, backend, p, overview); }
            public void accept(Player p, Invite i) { backend.accept(p, i.villageId(), after.apply(p, State.MAIN)); }
            public void decline(Player p, Invite i) { backend.decline(p, i.villageId(), after.apply(p, State.NONE)); }
            public void invite(Player p) { askInvite(coreUi, backend, p, overview); }
            public void leave(Player p) { backend.leave(p, after.apply(p, State.NONE)); }
            public void claimChunk(Player p) { backend.claimChunk(p, after.apply(p, State.MAIN)); }
            public void home(Player p) {
                coreUi.close(p);
                backend.home(p, reply -> feedback(p, reply.message(), reply.ok()));
            }
            public void editNotice(Player p, Village v) { askNotice(coreUi, backend, p, overview, v); }
            public void editIntro(Player p, Member m) { askIntro(coreUi, backend, p, overview, m); }
            public void toggleOfficer(Player p, Member m) {
                backend.setRole(p, m.id(), m.role() == Role.OFFICER ? Role.MEMBER : Role.OFFICER, after.apply(p, State.members(m.id().toString())));
            }
            public void kick(Player p, Member m) { backend.kick(p, m.id(), after.apply(p, State.members(null))); }
            public void setIcon(Player p, String key) { backend.setIcon(p, key, after.apply(p, State.MAIN)); }
            public void tell(Player p, String message) { feedback(p, message, false); }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    private static void feedback(Player p, String message, boolean good) {
        if (!p.isOnline()) return;
        Messages.send(p, message);
        if (good) p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
        else p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 0.8f);
    }

    // ---- input dialogs ----

    /** One text dialog: 저장/취소; {@code save} gets the stripped text, {@code cancel} reopens the screen. */
    private static void ask(CoreUi coreUi, Player player, String title, String body, String key, String label, String initial,
                            int maxLength, int[] lines, String saveLabel, Consumer<String> save, Runnable cancel) {
        coreUi.removeSession(player);
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton ok = ActionButton.builder(Component.text(saveLabel)).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi,
                        () -> save.accept(Objects.requireNonNullElse(response.getText(key), "").strip())), once)).build();
        ActionButton no = ActionButton.builder(Component.text("취소")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(coreUi, cancel), once)).build();
        var input = DialogInput.text(key, Component.text(label)).initial(initial).maxLength(maxLength).width(300);
        if (lines != null) input.multiline(TextDialogInput.MultilineOptions.create(lines[0], lines[1]));
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title))
                        .canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(List.of(DialogBody.plainMessage(Component.text(body), 260)))
                        .inputs(List.of(input.build()))
                        .build())
                .type(DialogType.confirmation(ok, no))));
    }

    private static void askName(CoreUi coreUi, Backend backend, Player player, Overview o) {
        ask(coreUi, player, "마을 창설", "은화 " + money(o.createCost()) + "이 들어요. 지금 서 있는 곳이 마을 홈이 돼요.\n이름은 한글·영문·숫자 2~12자예요.",
                "name", "마을 이름", "", 12, null, "창설", name -> {
                    if (name.isEmpty()) { show(coreUi, backend, player, o, State.NONE); return; }
                    backend.create(player, name, reply -> {
                        feedback(player, reply.message(), reply.ok());
                        reload(coreUi, backend, player, reply.ok() ? State.MAIN : State.NONE);
                    });
                }, () -> show(coreUi, backend, player, o, State.NONE));
    }

    private static void askInvite(CoreUi coreUi, Backend backend, Player player, Overview o) {
        ask(coreUi, player, "주민 초대", "초대할 플레이어의 닉네임을 적어 주세요. 초대는 3일 동안 유효해요.",
                "target", "닉네임", "", 32, null, "초대", target -> {
                    if (target.isEmpty()) { show(coreUi, backend, player, o, State.members(null)); return; }
                    backend.invite(player, target, reply -> {
                        feedback(player, reply.message(), reply.ok());
                        reload(coreUi, backend, player, State.members(null));
                    });
                }, () -> show(coreUi, backend, player, o, State.members(null)));
    }

    /** Multi-line notice editor (Paper dialog); 저장 stores it and reopens the village screen. */
    private static void askNotice(CoreUi coreUi, Backend backend, Player player, Overview o, Village v) {
        ask(coreUi, player, "마을 공지 수정", "주민 모두가 마을 화면에서 보는 공지예요. 비우고 저장하면 지워져요.",
                "notice", "공지", v.notice(), 400, new int[]{12, 140}, "저장", text -> backend.setNotice(player, text, reply -> {
                    feedback(player, reply.message(), reply.ok());
                    reload(coreUi, backend, player, State.MAIN);
                }), () -> show(coreUi, backend, player, o, State.MAIN));
    }

    /** Multi-line introduction editor for the viewer's own member row. */
    private static void askIntro(CoreUi coreUi, Backend backend, Player player, Overview o, Member m) {
        State back = State.members(m.id().toString());
        ask(coreUi, player, "내 소개 수정", "주민 목록에서 다른 주민들이 보는 소개글이에요.",
                "intro", "소개", m.intro(), 200, new int[]{6, 90}, "저장", text -> backend.setIntro(player, text, reply -> {
                    feedback(player, reply.message(), reply.ok());
                    reload(coreUi, backend, player, back);
                }), () -> show(coreUi, backend, player, o, back));
    }

    private static void onMain(CoreUi coreUi, Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(coreUi, task);
    }

    private static String money(long amount) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(amount);
    }

    // ---- drawing ----

    /** One frame; pure apart from the callbacks, so offline checks and the preview tool can build it. */
    static CoreMenu build(Map<String, Glyph> g, Overview o, UUID viewer, State state, Actions act) {
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
        Village v = o.village();
        if (v == null) {
            none(c, o, state, act);
            return c.build();
        }
        Member me = v.member(viewer);
        Role role = me == null ? Role.MEMBER : me.role();
        switch (state.view()) {
            case NONE, MAIN -> main(c, v, role, state, act);
            case MEMBERS -> members(c, v, viewer, role, state, act);
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

    /** No village: founding on the left, received invitations on the right. */
    private static void none(BookCanvas c, Overview o, State state, Actions act) {
        c.text("마을", LEFT, TOP, INK);
        c.image("menu/ui/well", LEFT, 43);
        icon(c, "vil", LEFT, 43);
        c.fitted("아직 마을이 없어요", LEFT + 44, 49, PAGE - 44, INK);
        c.fitted("마을을 세우거나 초대를 받아요", LEFT + 44, 61, PAGE - 44, DIM);
        c.image("shop/ui/divider_200", LEFT, 85);
        String[][] rows = {{"창설 비용", "은화 " + money(o.createCost())}, {"처음 인원", "최대 " + o.startMembers() + "명"},
                {"마을 등급", "0 ~ " + MAX_GRADE + "등급"}, {"초대 유효", "3일"}};
        for (int i = 0; i < rows.length; i++) {
            c.text(rows[i][0], LEFT, 91 + i * 12, DIM);
            c.right(rows[i][1], LEFT + PAGE, 91 + i * 12, INK);
        }
        c.fitted("창설하면 지금 서 있는 곳이 마을 홈이 돼요.", LEFT, 159, PAGE, BODY);
        c.fitted("마을 주민과 마을 채팅·공지를 함께 써요.", LEFT, 171, PAGE, BODY);
        c.button("green", 98, "마을 창설", LEFT, BUTTON_Y, WHITE, act::create);

        // Right page: invitations.
        List<Invite> invites = o.invites();
        c.text("받은 초대", RIGHT, TOP, INK);
        c.right(invites.size() + "개", RIGHT + PAGE, TOP, DIM);
        c.image("shop/ui/divider_200", RIGHT, 43);
        if (invites.isEmpty()) {
            c.centered("받은 초대가 없어요", RIGHT, PAGE, 120, DIM);
            c.centered("마을 주민에게 초대를 부탁해 보세요", RIGHT, PAGE, 132, DIM);
            return;
        }
        Invite picked = invites.stream().filter(i -> Long.toString(i.villageId()).equals(state.member())).findFirst().orElse(invites.getFirst());
        for (int i = 0; i < invites.size() && i < INVITE_Y.length; i++) {
            Invite inv = invites.get(i);
            int y = INVITE_Y[i];
            boolean selected = inv == picked;
            c.fitted(inv.name(), RIGHT, y, PAGE - 50, selected ? GREEN : INK);
            c.right(inv.grade() + "등급", RIGHT + PAGE, y, DIM);
            c.click(RIGHT, y - 3, PAGE, 14, p -> act.go(p, State.invite(inv.villageId())), null);
        }
        c.image("menu/ui/well", RIGHT, 159);
        icon(c, picked.icon(), RIGHT, 159);
        c.fitted(picked.name(), RIGHT + 44, 162, PAGE - 44, INK);
        c.fitted("주민 " + picked.members() + "/" + picked.maxMembers() + "명", RIGHT + 44, 174, PAGE - 44, DIM);
        c.fitted(picked.inviter() + "님의 초대 · " + picked.at(), RIGHT, 203, PAGE, BODY);
        boolean full = picked.members() >= picked.maxMembers();
        if (full) c.fitted("인원이 가득 차서 지금은 들어갈 수 없어요", RIGHT, 215, PAGE, RED);
        c.button("paper", 98, "거절", RIGHT, BUTTON_Y, INK, p -> act.decline(p, picked));
        if (full) c.button("off", 98, "수락", RIGHT + PAGE - 98, BUTTON_Y, DIM, null);
        else c.button("green", 98, "수락", RIGHT + PAGE - 98, BUTTON_Y, WHITE, p -> act.accept(p, picked));
    }

    private static void main(BookCanvas c, Village v, Role role, State state, Actions act) {
        Member leader = v.owner();
        boolean owner = role == Role.OWNER;
        c.fitted(v.name(), LEFT, TOP, PAGE - 50, INK);
        c.right(v.grade() + "등급", LEFT + PAGE, TOP, GREEN);
        c.image("menu/ui/well", LEFT, 43);
        icon(c, v.icon(), LEFT, 43);
        if (owner) c.click(LEFT, 43, 36, 36, p -> act.go(p, State.icon(v.icon())),
                HoverEvent.showText(Component.text("마을 아이콘 바꾸기 (마을장)")));
        c.fitted("마을장 " + (leader == null ? "-" : leader.name()), LEFT + 44, 46, PAGE - 44, INK);
        c.fitted("주민 " + v.members().size() + "/" + v.maxMembers() + "명 · 영토 " + v.chunks() + "청크", LEFT + 44, 58, PAGE - 44, DIM);
        for (int i = 0; i < MAX_GRADE; i++) c.image(i < v.grade() ? "dex/ui/pip_on" : "dex/ui/pip_off", LEFT + 44 + i * 8, 72);
        c.text("등급 " + v.grade() + " / " + MAX_GRADE, LEFT + 44 + MAX_GRADE * 8 + 4, 71, DIM);

        c.image("shop/ui/divider_200", LEFT, 85);
        c.text("마을 홈", LEFT, 91, DIM);
        c.fitted(v.home(), LEFT, 103, PAGE, INK);
        c.text("창설일", LEFT, 117, DIM);
        c.right(v.founded(), LEFT + PAGE, 117, INK);
        c.text("내 역할", LEFT, 129, DIM);
        c.right(role.label, LEFT + PAGE, 129, role == Role.MEMBER ? INK : GREEN);
        // Territory (below the book seam): size, next price and, for the 마을장, buying the chunk they stand in.
        c.text("영토", LEFT, 159, DIM);
        c.right(v.chunks() + " / " + v.maxChunks() + "청크", LEFT + PAGE, 159, INK);
        c.text("다음 청크", LEFT, 171, DIM);
        String next = v.nextChunkCost() < 0 ? "더 넓힐 수 없음" : v.nextChunkCost() == 0 ? "무료" : "은화 " + money(v.nextChunkCost());
        c.right(next, LEFT + PAGE, 171, v.nextChunkCost() == 0 ? GREEN : INK);
        if (role.manages()) {
            c.fitted("서 있는 청크를 사요 · 기존 영토와 맞닿아야 해요", LEFT, 185, PAGE, DIM);
            c.fitted((owner ? "홈 설정 /마을 홈설정 · " : "") + "영토 줄이기 /마을 영토 해제", LEFT, 197, PAGE, DIM);
            if (v.nextChunkCost() < 0) c.button("off", 98, "이 청크 구매", LEFT, 232, DIM, null);
            else if (state.confirm()) {
                c.button("green", 98, "정말 구매", LEFT, 232, WHITE, act::claimChunk);
                c.fitted(v.nextChunkCost() == 0 ? "한 번 더 누르면 무료로 사요" : "한 번 더 누르면 " + next + "로 사요", LEFT + 102, 238, PAGE - 102, RED);
            } else c.button("paper", 98, "이 청크 구매", LEFT, 232, INK, p -> act.go(p, State.MAIN.asking()));
        }
        if ("-".equals(v.home())) c.button("off", 98, "마을로 이동", LEFT, BUTTON_Y, DIM, null);
        else c.button("paper", 98, "마을로 이동", LEFT, BUTTON_Y, INK, act::home);
        c.button("paper", 98, "주민 목록", LEFT + 102, BUTTON_Y, INK, p -> act.go(p, State.members(null)));

        // Right page: the notice.
        c.text("마을 공지", RIGHT, TOP, INK);
        if (!v.notice().isEmpty()) c.right(v.noticeBy() + " · " + v.noticeAt(), RIGHT + PAGE, TOP, DIM);
        c.image("shop/ui/divider_200", RIGHT, 43);
        if (v.notice().isEmpty()) {
            c.centered("아직 공지가 없어요", RIGHT, PAGE, 120, DIM);
            if (role.manages()) c.centered("공지 수정을 눌러 주민들에게 알려 보세요", RIGHT, PAGE, 132, DIM);
        } else {
            List<String> lines = c.wrap(v.notice(), PAGE);
            for (int i = 0; i < lines.size() && i < NOTICE_Y.length; i++) {
                boolean last = i == NOTICE_Y.length - 1 && lines.size() > NOTICE_Y.length;
                c.text(last ? lines.get(i) + " …" : lines.get(i), RIGHT, NOTICE_Y[i], BODY);
            }
        }
        c.button("paper", 98, "마을 테크", RIGHT, BUTTON_Y, INK, p -> act.tell(p, "마을 테크(기술 트리) 화면은 추후 구현 예정이에요."));
        if (role.manages()) c.button("paper", 98, "공지 수정", RIGHT + PAGE - 98, BUTTON_Y, INK, p -> act.editNotice(p, v));
    }

    private static void members(BookCanvas c, Village v, UUID viewer, Role role, State state, Actions act) {
        List<Member> members = v.members();
        int pages = Math.max(1, (members.size() + MEMBER_Y.length - 1) / MEMBER_Y.length);
        int page = Math.clamp(state.page(), 0, pages - 1);
        List<Member> rows = members.subList(Math.min(members.size(), page * MEMBER_Y.length),
                Math.min(members.size(), (page + 1) * MEMBER_Y.length));
        Member selected = members.stream().filter(m -> m.id().toString().equals(state.member())).findFirst()
                .orElse(rows.isEmpty() ? null : rows.getFirst());

        c.text("주민 목록", LEFT, TOP, INK);
        c.right(members.size() + "/" + v.maxMembers() + "명", LEFT + PAGE, TOP, DIM);
        for (int i = 0; i < rows.size(); i++) {
            Member m = rows.get(i);
            int y = MEMBER_Y[i];
            boolean picked = selected != null && selected.id().equals(m.id());
            c.head(head(m), LEFT, y);
            c.fitted(m.name(), LEFT + 11, y, PAGE - 70, picked ? GREEN : INK);
            c.right(m.role().label, LEFT + PAGE, y, m.role() == Role.MEMBER ? DIM : GREEN);
            c.click(LEFT, y - 4, PAGE, 17, p -> act.go(p, State.members(m.id().toString())), null);
        }
        c.button("paper", 52, "돌아가기", LEFT, BUTTON_Y, INK, p -> act.go(p, State.MAIN));
        if (role.manages()) {
            if (members.size() < v.maxMembers()) c.button("paper", 52, "초대", LEFT + 56, BUTTON_Y, INK, act::invite);
            else c.button("paper", 52, "가득 참", LEFT + 56, BUTTON_Y, DIM, null);
        }
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
        boolean self = m.id().equals(viewer);
        c.text("소개", RIGHT, 102, DIM);
        if (m.intro().isEmpty()) c.text(self ? "소개 수정을 눌러 나를 소개해 보세요" : "아직 소개글이 없어요", RIGHT, 115, DIM);
        else {
            List<String> lines = c.wrap(m.intro(), PAGE);
            int[] ys = {115, 126, 137, 159, 170, 181, 192, 203, 214};
            for (int i = 0; i < lines.size() && i < ys.length; i++)
                c.text(i == ys.length - 1 && lines.size() > ys.length ? lines.get(i) + " …" : lines.get(i), RIGHT, ys[i], BODY);
        }

        // Buttons: your own row edits your introduction (and leaves); 마을장/부마을장 manage the others.
        int bx = RIGHT + PAGE - 98;
        boolean canKick = !self && (role == Role.OWNER ? m.role() != Role.OWNER : role == Role.OFFICER && m.role() == Role.MEMBER);
        if (self) {
            c.button("paper", 98, "소개 수정", RIGHT, BUTTON_Y, INK, p -> act.editIntro(p, m));
            if (role == Role.OWNER) c.button("off", 98, "탈퇴 불가", bx, BUTTON_Y, DIM, null);
            else if (state.confirm()) c.button("paper", 98, "정말 탈퇴", bx, BUTTON_Y, RED, act::leave);
            else c.button("paper", 98, "마을 탈퇴", bx, BUTTON_Y, RED, p -> act.go(p, State.members(m.id().toString()).asking()));
            if (state.confirm() && role != Role.OWNER) c.centered("한 번 더 누르면 마을에서 탈퇴해요", RIGHT, PAGE, 238, RED);
            if (role == Role.OWNER) c.centered("마을장은 /마을 위임 · /마을 해산을 써요", RIGHT, PAGE, 238, DIM);
            return;
        }
        if (role == Role.OWNER) {
            if (m.role() == Role.MEMBER) c.button("green", 98, "부마을장 지정", RIGHT, BUTTON_Y, WHITE, p -> act.toggleOfficer(p, m));
            else c.button("paper", 98, "부마을장 해제", RIGHT, BUTTON_Y, INK, p -> act.toggleOfficer(p, m));
        }
        if (canKick) {
            if (state.confirm()) {
                c.centered("한 번 더 누르면 " + m.name() + "님을 추방해요", RIGHT, PAGE, 238, RED);
                c.button("paper", 98, "정말 추방", bx, BUTTON_Y, RED, p -> act.kick(p, m));
            } else c.button("paper", 98, "추방", bx, BUTTON_Y, RED, p -> act.go(p, State.members(m.id().toString()).asking()));
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
        c.fitted("마을 화면과 받은 초대에 보여요.", RIGHT, 106, PAGE, BODY);
        c.fitted("마을장만 바꿀 수 있어요.", RIGHT, 118, PAGE, BODY);
        c.button("paper", 98, "취소", RIGHT, BUTTON_Y, INK, p -> act.go(p, State.MAIN));
        if (picked.equals(v.icon())) c.button("off", 98, "적용", RIGHT + PAGE - 98, BUTTON_Y, DIM, null);
        else c.button("green", 98, "적용", RIGHT + PAGE - 98, BUTTON_Y, WHITE, p -> act.setIcon(p, picked));
    }
}
