package org.crebs.coreui.menu.kokonut;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kokonut_Pets (MCPets fork) pet screen, opened by {@code /펫} and by right-clicking your own pet ({@link Pets}).
 *
 * Left page: the pets you may summon, each row with its grade (fixed per pet kind, Core item rarity), level and state.
 * Right page: name plate (name, grade, icon, nickname, state), 체력 and 경험치 bars, the four modifiers, how far the next level is, auto pickup, the
 * description, the pet's own actions (이름 · 스킨 · 가방 · 탑승 · 신호봉, usable while summoned) and 소환 / 회수.
 * Everything that changes a pet stays in Kokonut_Pets ({@link Catalog}).
 *
 * Layout keeps CoreUi's row rules: no text on y 145..152 (the lower page tiles start at 153 and are drawn later),
 * vanilla item icons on y % 9 == 2.
 */
public final class PetMenu {
    static final String ID = "kokonut-pets";
    static final int ROWS = 8, DESC_LINES = 2;
    private static final int LEFT = 15, RIGHT = 240, PAGE = 200, TOP = 31, BUTTON_Y = 258, OPTION_Y = 232;
    private static final int[] ROW_Y = {45, 72, 99, 126, 153, 180, 207, 234};
    /** Tab icon. Pets use their spawn egg until the pack has per-pet icons ({@code pet/icon/<id>}). */
    static final String ICON = "wolf_spawn_egg";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.#");
    private static final TextColor INK = TextColor.color(0x3B2A1A), DIM = TextColor.color(0x7A6446),
            BODY = TextColor.color(0x5A4630), GREEN = TextColor.color(0x3E7A22), RED = TextColor.color(0xA63A2A),
            WHITE = TextColor.color(0xFFFFFF), PAPER = TextColor.color(0xF6ECD0), STAMP = TextColor.color(0xFFD86A),
            HEALTH = TextColor.color(0xE0503C), LOCKED = TextColor.color(0xC9A97E);

    enum Status { READY, DEAD, REVOKED }

    /** {@code level}: the level name (Lv.1 …); {@code next} null at the last level. */
    record Stats(Component level, Component next, double health, double maxHealth, double regeneration,
                 int damage, int resistance, int power, double experience, double levelStart, double levelEnd) {
        boolean maxLevel() {
            return next == null;
        }
    }

    /**
     * One pet as drawn. {@code rarity} is the pet's grade label in its Core item rarity colour (same as fish), may be
     * null; {@code nickname} and {@code stats} may be null.
     */
    record Pet(String id, String icon, Component rarity, Component name, Component nickname, List<Component> description, boolean active,
               Status status, int timeLeft, Stats stats, int pickup,
               boolean nameable, boolean skins, boolean inventory, boolean mountable, boolean signals) {
        String plain() {
            return PLAIN.serialize(name);
        }
    }

    /** What the screen needs from Kokonut_Pets. Actions on a pet need it summoned; they return false otherwise. */
    interface Catalog {
        List<Pet> pets(Player player);
        /** Summons; Kokonut_Pets reports the outcome in chat. */
        void summon(Player player, String id);
        boolean revoke(Player player, String id);
        /** Sets the name typed in the rename dialog (empty clears it); Kokonut_Pets checks it and reports in chat. */
        boolean setName(Player player, String id, String name);
        /** Longest name Kokonut_Pets accepts. */
        int maxNameLength();
        /** Kokonut_Pets' skin chest. */
        boolean skins(Player player, String id);
        /** The pet's storage chest. */
        boolean inventory(Player player, String id);
        boolean mount(Player player, String id);
        boolean signalStick(Player player, String id);
    }

    record View(List<Pet> pets, Pet selected, int page) {
        int pages() {
            return Math.max(1, (pets.size() + ROWS - 1) / ROWS);
        }
    }

    private record State(int page, String selected) { }

    /** The pet's own actions, in button order. */
    enum Option {
        NAME("이름"), SKIN("스킨"), BAG("가방"), RIDE("탑승"), STICK("신호봉");
        final String label;
        Option(String label) { this.label = label; }

        boolean has(Pet p) {
            return switch (this) {
                case NAME -> p.nameable();
                case SKIN -> p.skins();
                case BAG -> p.inventory();
                case RIDE -> p.mountable();
                case STICK -> p.signals();
            };
        }
    }

    interface Actions {
        void select(Player p, Pet pet);
        void page(Player p, int page);
        void summon(Player p, Pet pet);
        void revoke(Player p, Pet pet);
        void option(Player p, Pet pet, Option option);
        /** The pet's auto pickup settings (mode and item list). */
        void pickup(Player p, Pet pet);
        void close(Player p);
    }

    private PetMenu() {
    }

    /** False while the book glyphs are not loaded (Kokonut_Pets then opens its chest menu). {@code select} may be null. */
    public static boolean open(CoreUi coreUi, Catalog catalog, Player player, String select) {
        var g = coreUi.getGlyphs().all();
        if (!g.containsKey("menu/ui/tab") || !g.containsKey("menu/ui/tab_join") || !g.containsKey("dex/ui/bar_100")
                || !g.containsKey("dex/ui/frame_field") || !g.containsKey("dex/ui/page_0_0")) return false;
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
        int page = 0;
        if (select != null) {
            List<Pet> pets = catalog.pets(player);
            for (int i = 0; i < pets.size(); i++) if (pets.get(i).id().equals(select)) page = i / ROWS;
        }
        show(coreUi, catalog, player, new State(page, select));
        return true;
    }

    private static void show(CoreUi coreUi, Catalog catalog, Player player, State state) {
        if (!player.isOnline()) return;
        List<Pet> pets = catalog.pets(player);
        int page = Math.clamp(state.page(), 0, Math.max(1, (pets.size() + ROWS - 1) / ROWS) - 1);
        Pet selected = pick(pets, page, state.selected());
        State now = new State(page, selected == null ? null : selected.id());
        coreUi.open(player, build(coreUi.getGlyphs().all(), new View(pets, selected, page), new Actions() {
            public void select(Player p, Pet pet) { show(coreUi, catalog, p, new State(now.page(), pet.id())); }
            public void page(Player p, int to) { show(coreUi, catalog, p, new State(to, null)); }
            public void summon(Player p, Pet pet) {
                catalog.summon(p, pet.id());
                later(coreUi, () -> show(coreUi, catalog, p, now));
            }
            public void revoke(Player p, Pet pet) {
                if (!catalog.revoke(p, pet.id())) org.crebs.coreui.Messages.send(p, "이 펫은 소환 중이 아니에요.");
                later(coreUi, () -> show(coreUi, catalog, p, now));
            }
            public void option(Player p, Pet pet, Option option) {
                if (option == Option.NAME) {
                    if (!pet.active()) { notSummoned(p); return; }
                    askName(coreUi, catalog, p, pet, () -> show(coreUi, catalog, p, now));
                    return;
                }
                switch (option) {
                    case STICK -> {
                        if (!catalog.signalStick(p, pet.id())) notSummoned(p);
                        later(coreUi, () -> show(coreUi, catalog, p, now));
                    }
                    default -> {
                        // Chat input, chests and riding replace the dialog.
                        coreUi.close(p);
                        later(coreUi, () -> {
                            if (!p.isOnline()) return;
                            boolean done = switch (option) {
                                case NAME -> true;
                                case SKIN -> catalog.skins(p, pet.id());
                                case BAG -> catalog.inventory(p, pet.id());
                                case RIDE -> catalog.mount(p, pet.id());
                                case STICK -> true;
                            };
                            if (!done) notSummoned(p);
                        });
                    }
                }
            }
            public void pickup(Player p, Pet pet) {
                if (!(catalog instanceof PetPickupMenu.Catalog settings)) return;
                PetPickupMenu.open(coreUi, settings, p, pet.id(), pet.plain(), pet.pickup(),
                        q -> show(coreUi, catalog, q, now));
            }
            public void close(Player p) { coreUi.close(p); }
        }));
    }

    /** Rename dialog: one text field with the current name; 확인 applies, 취소 goes back to the pet screen. */
    private static void askName(CoreUi coreUi, Catalog catalog, Player player, Pet pet, Runnable back) {
        coreUi.removeSession(player);
        var once = net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).lifetime(java.time.Duration.ofMinutes(10)).build();
        String current = pet.nickname() == null ? "" : PLAIN.serialize(pet.nickname());
        int max = Math.max(1, catalog.maxNameLength());
        var confirm = io.papermc.paper.registry.data.dialog.ActionButton.builder(Component.text("확인")).width(100)
                .action(io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((response, audience) -> onMain(coreUi, () -> {
                    String name = java.util.Objects.requireNonNullElse(response.getText("name"), "");
                    catalog.setName(player, pet.id(), name);
                    later(coreUi, back);
                }), once)).build();
        var cancel = io.papermc.paper.registry.data.dialog.ActionButton.builder(Component.text("취소")).width(100)
                .action(io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((response, audience) -> onMain(coreUi, back), once)).build();
        player.showDialog(io.papermc.paper.dialog.Dialog.create(builder -> builder.empty()
                .base(io.papermc.paper.registry.data.dialog.DialogBase.builder(Component.text(pet.plain() + " 이름"))
                        .canCloseWithEscape(true).pause(false)
                        .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Component.text(
                                "펫에게 붙일 이름을 입력하세요. (최대 " + max + "자)\n비우고 확인하면 이름이 지워져요."), 240)))
                        .inputs(List.of(io.papermc.paper.registry.data.dialog.input.DialogInput.text("name", Component.text("이름"))
                                .initial(current.length() > max ? current.substring(0, max) : current).maxLength(max).width(200).build()))
                        .build())
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(confirm, cancel))));
    }

    private static void onMain(CoreUi coreUi, Runnable task) {
        if (org.bukkit.Bukkit.isPrimaryThread()) task.run();
        else org.bukkit.Bukkit.getScheduler().runTask(coreUi, task);
    }

    private static void notSummoned(Player p) {
        org.crebs.coreui.Messages.send(p, "펫을 소환해야 쓸 수 있어요.");
    }

    /** Next tick: Kokonut_Pets spawns and revokes on the main thread, so the redraw sees the new state. */
    private static void later(CoreUi coreUi, Runnable task) {
        coreUi.getServer().getScheduler().runTask(coreUi, task);
    }

    /** The kept selection if still listed, else the first summoned pet on this page, else the page's first row. */
    static Pet pick(List<Pet> pets, int page, String wanted) {
        if (wanted != null) for (Pet p : pets) if (p.id().equals(wanted)) return p;
        List<Pet> rows = pets.subList(Math.min(pets.size(), page * ROWS), Math.min(pets.size(), (page + 1) * ROWS));
        for (Pet p : rows) if (p.active()) return p;
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 소환 중 / 대기 / 부활 12초 / 재소환 12초. */
    static String state(Pet p) {
        if (p.status() == Status.DEAD) return "부활 " + p.timeLeft() + "초";
        if (p.status() == Status.REVOKED) return "재소환 " + p.timeLeft() + "초";
        return p.active() ? "소환 중" : "대기";
    }

    private static TextColor stateColor(Pet p) {
        if (p.status() != Status.READY) return RED;
        return p.active() ? GREEN : DIM;
    }

    /** One frame; pure apart from the callbacks, so tests and the offline preview can build it with sample data. */
    static CoreMenu build(Map<String, Glyph> g, View v, Actions act) {
        TitleMenu.Canvas c = new TitleMenu.Canvas(g, ID, "펫");
        for (int col = 0; col < 2; col++) {
            c.image("dex/ui/page_0_" + col, col * 228, 20);
            c.image("dex/ui/page_1_" + col, col * 228, 153);
        }
        c.image("menu/ui/tab", 16, 0);
        c.item(ICON, 20, 2);
        c.text("펫", 38, 6, WHITE);
        c.image("menu/ui/tab_join", 17, 20);
        c.image("dex/ui/close", 432, 0);
        c.click(432, 0, 20, 20, act::close, null);

        list(c, v, act);
        Pet s = v.selected();
        if (s == null) {
            c.centered("펫을 고르면 정보가 보여요", RIGHT, PAGE, 140, DIM);
            return c.b.build();
        }
        plate(c, s);
        if (s.stats() != null) stats(c, s, act);
        else if (s.pickup() > 0) {
            pickup(c, s, 125, act);
            description(c, s, rows(s, 139));
        } else description(c, s, rows(s, 125));
        options(c, s, act);
        main(c, s, act);
        return c.b.build();
    }

    /** Left page: pets with grade, level and state. */
    private static void list(TitleMenu.Canvas c, View v, Actions act) {
        long active = v.pets().stream().filter(Pet::active).count();
        c.text("내 펫", LEFT, TOP, INK);
        c.right(active > 0 ? "소환 " + active + " · 전체 " + v.pets().size() : "전체 " + v.pets().size(), LEFT + PAGE, TOP, DIM);
        if (v.pets().isEmpty()) {
            c.centered("아직 함께하는 펫이 없어요", LEFT, PAGE, 117, DIM);
            c.centered("펫을 얻으면 여기에 보여요", LEFT, PAGE, 129, DIM);
        }
        int from = v.page() * ROWS;
        for (int i = 0; i < ROWS && from + i < v.pets().size(); i++) {
            Pet pet = v.pets().get(from + i);
            int y = ROW_Y[i];
            boolean on = v.selected() != null && v.selected().id().equals(pet.id());
            c.image(on ? "dex/ui/slot_selected" : "dex/ui/slot_normal", LEFT, y);
            icon(c, pet, LEFT + 2, y + 2);
            Component title = pet.nickname() != null ? pet.nickname() : pet.name();
            String state = state(pet);
            int stateWidth = org.crebs.coreui.menu.render.TextWidths.width(Component.text(state));
            c.fitted(PLAIN.serialize(title), LEFT + 25, y + 2, PAGE - 32 - stateWidth, INK);
            c.right(state, LEFT + PAGE - 2, y + 2, stateColor(pet));
            int x = LEFT + 25;
            if (pet.rarity() != null) x += grade(c, pet.rarity(), x, y + 12) + 4;
            if (pet.stats() != null) c.text(PLAIN.serialize(pet.stats().level()), x, y + 12, DIM);
            else if (pet.rarity() == null) c.text("일반 펫", x, y + 12, DIM);
            c.click(LEFT, y, PAGE, 20, p -> act.select(p, pet), HoverEvent.showText(pet.name()));
        }
        int pages = v.pages();
        c.button("paper", 20, "<", LEFT + 132, BUTTON_Y, INK, v.page() > 0 ? p -> act.page(p, v.page() - 1) : null);
        c.centered((v.page() + 1) + "/" + pages, LEFT + 152, 28, BUTTON_Y + 6, INK);
        c.button("paper", 20, ">", LEFT + 180, BUTTON_Y, INK, v.page() + 1 < pages ? p -> act.page(p, v.page() + 1) : null);
    }

    /** Name plate: species name and grade on top, icon, nickname, state. */
    private static void plate(TitleMenu.Canvas c, Pet s) {
        c.image("dex/ui/frame_field", RIGHT, TOP);
        String tier = s.rarity() == null ? "" : PLAIN.serialize(s.rarity());
        int tierWidth = tier.isEmpty() ? 0 : org.crebs.coreui.menu.render.TextWidths.width(Component.text(tier)) + 6;
        c.fitted(s.plain(), RIGHT + 5, TOP + 4, PAGE - 10 - tierWidth, PAPER);
        if (!tier.isEmpty()) {
            int width = org.crebs.coreui.menu.render.TextWidths.width(Component.text(tier)) + 4;
            grade(c, s.rarity(), RIGHT + PAGE - 5 - width, TOP + 4);
        }
        icon(c, s, RIGHT + (PAGE - 16) / 2, 65);
        if (s.nickname() != null) {
            String nick = PLAIN.serialize(s.nickname());
            c.centered("\"" + nick + "\"", RIGHT, PAGE, 88, PAPER);
        }
        c.centered(state(s), RIGHT, PAGE, 101, s.status() != Status.READY ? HEALTH : s.active() ? STAMP : PAPER);
    }

    /** 체력 and 경험치 bars, modifiers, next tier, description. */
    private static void stats(TitleMenu.Canvas c, Pet s, Actions act) {
        Stats st = s.stats();
        c.text("체력", RIGHT, 125, DIM);
        bar(c, RIGHT + 34, 126, st.maxHealth() <= 0 ? 0 : st.health() / st.maxHealth(), HEALTH);
        c.right(NUMBER.format(Math.floor(st.health())) + " / " + NUMBER.format(st.maxHealth()), RIGHT + PAGE, 125, INK);

        double span = st.levelEnd() - st.levelStart();
        double xp = st.maxLevel() ? 1 : span <= 0 ? 0 : (st.experience() - st.levelStart()) / span;
        c.text("경험치", RIGHT, 137, DIM);
        bar(c, RIGHT + 34, 138, xp, null);
        c.right(st.maxLevel() ? "최고 레벨" : NUMBER.format(Math.floor(st.experience())) + " / " + NUMBER.format(st.levelEnd()), RIGHT + PAGE, 137, INK);

        modifier(c, "공격", st.damage(), RIGHT, 153);
        modifier(c, "방어", st.resistance(), RIGHT + 100, 153);
        modifier(c, "힘", st.power(), RIGHT, 165);
        c.text("회복", RIGHT + 100, 165, DIM);
        c.text(NUMBER.format(st.regeneration()) + " /초", RIGHT + 134, 165, st.regeneration() > 0 ? GREEN : INK);

        String level = PLAIN.serialize(st.level());
        if (st.maxLevel()) c.text(level + " · 최고 레벨에 도달했어요", RIGHT, 179, BODY);
        else {
            String next = PLAIN.serialize(st.next());
            c.fitted(level + " · " + next + "까지 " + NUMBER.format(Math.max(0, Math.ceil(st.levelEnd() - st.experience()))) + " XP", RIGHT, 179, PAGE, BODY);
        }
        if (s.pickup() > 0) {
            pickup(c, s, 193, act);
            description(c, s, rows(s, 207));
        } else description(c, s, rows(s, 193));
    }

    /** 자동 줍기 row; the whole row opens the pickup settings. */
    private static void pickup(TitleMenu.Canvas c, Pet s, int y, Actions act) {
        c.text("자동 줍기", RIGHT, y, DIM);
        c.text("반경 " + s.pickup() + "칸", RIGHT + 50, y, GREEN);
        c.right("설정 ›", RIGHT + PAGE, y, GREEN);
        c.click(RIGHT, y - 2, PAGE, 12, p -> act.pickup(p, s),
                HoverEvent.showText(Component.text("줍기 모드와 아이템 목록 설정", WHITE)));
    }

    private static void modifier(TitleMenu.Canvas c, String label, int percent, int x, int y) {
        c.text(label, x, y, DIM);
        c.text((percent > 0 ? "+" : "") + percent + "%", x + 34, y, percent > 0 ? GREEN : percent < 0 ? RED : INK);
    }

    /** A 100 px bar: {@code ratio} of 98 px filled with the skill XP pieces, multiplied by {@code tint} when set. */
    private static void bar(TitleMenu.Canvas c, int x, int y, double ratio, TextColor tint) {
        c.image("dex/ui/bar_100", x, y);
        int amount = (int) Math.round(98 * Math.clamp(ratio, 0, 1));
        String piece = "skill/ui/xp_";
        for (int size = 64, cursor = x + 1; size >= 1; size /= 2) {
            while (amount >= size) {
                Glyph glyph = c.g.get(piece + size);
                if (glyph == null) return;
                var element = c.b.glyph("img" + c.ids++, glyph).position(cursor, y + 1);
                if (tint != null) element.tint(tint);
                element.add();
                cursor += size;
                amount -= size;
            }
        }
    }

    /** Description lines at {@code rows}; the last row ends with "…" when cut, full text on hover. */
    private static void description(TitleMenu.Canvas c, Pet s, int[] rows) {
        List<Component> lines = new ArrayList<>();
        for (Component line : s.description()) if (!PLAIN.serialize(line).isBlank()) lines.add(line);
        if (lines.isEmpty()) return;
        int shown = Math.min(rows.length, lines.size());
        for (int i = 0; i < shown; i++) {
            Component line = lines.get(i);
            String plain = PLAIN.serialize(line);
            boolean cut = i == shown - 1 && lines.size() > shown;
            c.fitted(cut ? plain + " …" : plain, RIGHT, rows[i], PAGE, BODY);
        }
        c.click(RIGHT, rows[0] - 2, PAGE, rows[shown - 1] - rows[0] + 12, p -> { }, tooltip(s));
    }

    private static int optionCount(Pet s) {
        int n = 0;
        for (Option option : Option.values()) if (option.has(s)) n++;
        return n;
    }

    /** Up to three actions share the bottom row with 소환하기; more go on their own row above it. */
    private static boolean wrapped(Pet s) {
        return optionCount(s) > 3;
    }

    /** Description rows from {@code first} down to the buttons (12 px apart, skipping the y 145..152 band). */
    static int[] rows(Pet s, int first) {
        int last = wrapped(s) ? OPTION_Y - 12 : BUTTON_Y - 14;
        java.util.List<Integer> rows = new java.util.ArrayList<>();
        for (int y = first; y <= last; y += 12) {
            if (y > 144 && y < 153) y = 153;
            if (y <= last) rows.add(y);
        }
        return rows.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The pet's actions; greyed out until it is summoned. Bottom row left of 소환하기, or their own row when wrapped. */
    private static void options(TitleMenu.Canvas c, Pet s, Actions act) {
        int x = RIGHT, y = wrapped(s) ? OPTION_Y : BUTTON_Y;
        for (Option option : Option.values()) {
            if (!option.has(s)) continue;
            boolean usable = s.active();
            c.button(usable ? "paper" : "dark", 36, option.label, x, y, usable ? INK : LOCKED,
                    usable ? p -> act.option(p, s, option) : null);
            if (!usable) c.click(x, y, 36, 20, p -> { },
                    HoverEvent.showText(Component.text("펫을 소환하면 쓸 수 있어요", WHITE)));
            x += 41;
        }
    }

    /** 소환하기 / 회수하기, or a waiting button while the pet recovers; narrower when three actions share its row. */
    private static void main(TitleMenu.Canvas c, Pet s, Actions act) {
        boolean narrow = optionCount(s) == 3;
        if (s.active()) {
            int w = narrow ? 52 : 100;
            c.button("paper", w, "회수하기", RIGHT + PAGE - w, BUTTON_Y, INK, p -> act.revoke(p, s));
        } else {
            int w = narrow ? 60 : 100;
            if (s.status() != Status.READY) c.button("off", w, state(s), RIGHT + PAGE - w, BUTTON_Y, DIM, null);
            else c.button("green", w, "소환하기", RIGHT + PAGE - w, BUTTON_Y, WHITE, p -> act.summon(p, s));
        }
    }

    /** The grade label in its rarity colour on a dark tag (light rarity colours are unreadable on parchment). */
    private static int grade(TitleMenu.Canvas c, Component rarity, int x, int y) {
        return c.tagged(rarity, PLAIN.serialize(rarity), x, y, PAGE - 30);
    }

    /** Per-pet icon from the pack when present, else the pet's spawn egg. */
    private static void icon(TitleMenu.Canvas c, Pet pet, int x, int y) {
        if (c.g.containsKey("pet/icon/" + pet.id())) c.image("pet/icon/" + pet.id(), x, y);
        else c.item(pet.icon(), x, y);
    }

    /** Full name and description. */
    private static HoverEventSource<?> tooltip(Pet pet) {
        Component text = pet.name();
        for (Component line : pet.description()) text = text.append(Component.newline()).append(line);
        return HoverEvent.showText(text);
    }
}
