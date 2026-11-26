package org.crebs.coreui.menu.smithy;

import kr.gomsv.kokonutCore.api.smithy.SmithyService;
import kr.gomsv.kokonutCore.api.smithy.SmithyService.BreakView;
import kr.gomsv.kokonutCore.api.smithy.SmithyService.CoreView;
import kr.gomsv.kokonutCore.api.smithy.SmithyService.Need;
import kr.gomsv.kokonutCore.api.smithy.SmithyService.UpgradeView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 대장간 (plan/기획/toolupgrade.md 1절): {@code /coreui smithy [player]}, opened by the blacksmith NPC from the console.
 * Rules and the actual work are Kokonut-Enchants' ({@link SmithyService}); this is only the screen.
 * <ul>
 *   <li>Left page: the upgradable gear in the player's inventory (click to pick one) and the three modes
 *       강화 · 돌파 · 마력 핵.</li>
 *   <li>Right page, 강화: next level, success and destruction odds, 장인의 기운 bar, materials, silver and gold, the
 *       강화 button.</li>
 *   <li>Right page, 돌파: next tier, materials and silver, then five buttons lit one by one in any order; the fifth
 *       runs the breakthrough. Leaving the screen or picking other gear puts the lights out and takes nothing.</li>
 *   <li>Right page, 마력 핵: the three ingredients and how many cores can be made, 1개 / 모두.</li>
 * </ul>
 * Forge-panel style (not the book): art from tools/smithy_art.py, mockups docs/smithy-mock-*.png, drawing helpers in
 * {@link SmithyCanvas}. 16 px vanilla item icons sit at y % 9 == 2; the 48 px gear icons (smithy/item) anywhere.
 */
public final class SmithyMenu {
    static final String ID = "kokonut-smithy";
    static final int LIGHTS = 5;
    static final DecimalFormat NUMBER = new DecimalFormat("#,##0");
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    enum Mode { UPGRADE("강화"), BREAK("돌파"), CORE("마력 핵");
        final String label;
        Mode(String label) { this.label = label; }
    }

    /** One player's screen state; kept in memory while they use it. */
    static final class Session {
        Mode mode = Mode.UPGRADE;
        int slot = -1;
        int page;
        boolean[] lit = new boolean[LIGHTS];
        boolean busy;
        String message;
        TextColor messageColor = SmithyCanvas.DIM;
        /** The result scene shown in the centre column until the next click, or null. */
        Effect effect;
        /**
         * The lead-in before a result scene: kind (upgrade, break, core), the frame being shown (-1 = none) and the
         * item it shows. While it plays the screen is busy.
         */
        String anim;
        int animFrame = -1;
        Effect animItem;
        /** The result stamp's first frame: drawn a little high, then it settles (overshoot). */
        boolean pop;

        void lightsOut() {
            lit = new boolean[LIGHTS];
        }

        int litCount() {
            int n = 0;
            for (boolean b : lit) if (b) n++;
            return n;
        }
    }

    /**
     * A result scene: {@code kind} is success, failure, destroyed, break or core (art smithy/fx/burst_*, smoke,
     * stamp_&lt;kind&gt;); icon and name are the item as it is shown.
     */
    record Effect(String kind, String icon, String name) { }

    /** One piece of gear in the list. */
    record Gear(int slot, String icon, String name, int level, String tech) { }

    /** Everything one frame shows; the views are for the selected gear (null when none). */
    record View(Session session, List<Gear> gear, UpgradeView upgrade, BreakView breakthrough, CoreView core) { }

    interface Actions {
        void select(Player p, int slot);
        void mode(Player p, Mode mode);
        void page(Player p, int delta);
        void upgrade(Player p);
        void light(Player p, int index);
        void craft(Player p, int amount);
        void dismiss(Player p);
        void close(Player p);
    }

    private SmithyMenu() {
    }

    /** False while the glyphs are not loaded or Kokonut-Enchants is not running. */
    public static boolean open(CoreUi coreUi, Player player) {
        var g = coreUi.getGlyphs().all();
        for (String id : List.of("smithy/ui/bg_0", "smithy/ui/bg_1", "smithy/ui/ring", "smithy/num/green_0",
                "smithy/ui/btn_on_134", "menu/icon/silver")) {
            if (!g.containsKey(id)) return false;
        }
        if (service(player) == null) return false;
        SESSIONS.put(player.getUniqueId(), new Session());
        player.playSound(player, Sound.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.5f, 1.2f);
        show(coreUi, player);
        return true;
    }

    public static void forget(UUID player) {
        SESSIONS.remove(player);
    }

    static SmithyService service(Player player) {
        return player.getServer().getServicesManager().load(SmithyService.class);
    }

    private static void show(CoreUi coreUi, Player player) {
        if (!player.isOnline()) return;
        Session s = SESSIONS.get(player.getUniqueId());
        SmithyService smithy = service(player);
        if (s == null || smithy == null) return;
        List<Gear> gear = gear(player, smithy);
        if (gear.stream().noneMatch(x -> x.slot() == s.slot)) {
            s.slot = gear.isEmpty() ? -1 : gear.getFirst().slot();
            s.lightsOut();
        }
        View view = new View(s, gear,
                s.slot < 0 ? null : smithy.upgradeView(player, s.slot),
                s.slot < 0 ? null : smithy.breakView(player, s.slot),
                smithy.coreView(player));
        coreUi.open(player, build(coreUi.getGlyphs().all(), view, actions(coreUi, smithy, s)));
    }

    private static Actions actions(CoreUi coreUi, SmithyService smithy, Session s) {
        return new Actions() {
            public void select(Player p, int slot) {
                s.effect = null;
                if (s.slot != slot) {
                    s.slot = slot;
                    s.lightsOut();
                    s.message = null;
                    p.playSound(p, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.4f, 1.4f);
                }
                show(coreUi, p);
            }

            public void mode(Player p, Mode mode) {
                s.effect = null;
                if (s.mode != mode) {
                    s.mode = mode;
                    s.lightsOut();
                    s.message = null;
                    p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 0.6f, 1f);
                }
                show(coreUi, p);
            }

            public void page(Player p, int delta) {
                s.effect = null;
                s.page += delta;
                show(coreUi, p);
            }

            public void upgrade(Player p) {
                if (s.busy) return;
                s.effect = null;
                s.busy = true;
                ItemStack before = p.getInventory().getItem(s.slot);
                String icon = before == null ? "barrier" : before.getType().getKey().getKey();
                String name = before == null ? "" : name(before);
                show(coreUi, p);
                smithy.upgrade(p, s.slot, result -> {
                    Effect effect = switch (result.outcome()) {
                        case "SUCCESS" -> new Effect("success", icon, name);
                        case "FAILURE" -> new Effect("failure", icon, name);
                        case "DESTROYED" -> new Effect("destroyed", icon, name);
                        default -> null;
                    };
                    TextColor color = switch (result.outcome()) {
                        case "SUCCESS" -> SmithyCanvas.GREEN;
                        case "FAILURE" -> SmithyCanvas.GOLD;
                        default -> SmithyCanvas.RED;
                    };
                    play(coreUi, p, s, "upgrade", new Effect("upgrade", icon, name), effect, result.message(), color);
                });
            }

            public void light(Player p, int index) {
                if (s.busy || s.lit[index]) return;
                s.effect = null;
                s.lit[index] = true;
                int n = s.litCount();
                p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.9f, 0.7f + n * 0.15f);
                p.playSound(p, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 0.3f + n * 0.1f, 1f + n * 0.1f);
                if (n < LIGHTS) {
                    show(coreUi, p);
                    return;
                }
                s.busy = true;
                show(coreUi, p);
                ItemStack before = p.getInventory().getItem(s.slot);
                Effect lead = before == null ? null : new Effect("break", before.getType().getKey().getKey(), name(before));
                smithy.breakthrough(p, s.slot, result -> {
                    ItemStack after = p.getInventory().getItem(s.slot);
                    Effect effect = result.happened() && after != null
                            ? new Effect("break", after.getType().getKey().getKey(), name(after)) : null;
                    play(coreUi, p, s, "break", lead, effect, result.message(),
                            result.happened() ? SmithyCanvas.GREEN : SmithyCanvas.RED);
                });
            }

            public void craft(Player p, int amount) {
                if (s.busy) return;
                s.effect = null;
                int made = smithy.craftCores(p, amount);
                Effect effect = made > 0 ? new Effect("core", "heart_of_the_sea", "마력 핵 ×" + made) : null;
                s.busy = true;
                play(coreUi, p, s, "core", effect, effect, made > 0 ? "마력 핵 " + made + "개를 만들었어요." : "재료가 부족해요.",
                        made > 0 ? SmithyCanvas.GREEN : SmithyCanvas.RED);
            }

            public void dismiss(Player p) {
                s.effect = null;
                s.message = null;
                show(coreUi, p);
            }

            public void close(Player p) {
                forget(p.getUniqueId());
                coreUi.close(p);
            }
        };
    }

    /** Lead-in: one flash frame, then the result scene {@value #FLASH_TICKS} ticks later. */
    static final int FLASH_TICKS = SmithyService.RESULT_DELAY_TICKS;

    static int frames(String kind) {
        return 1;
    }

    static int ticks(String kind) {
        return FLASH_TICKS;
    }

    /**
     * Plays the lead-in for {@code kind} (re-sending the screen once per frame), then shows the result scene. Nothing
     * plays when there is no result to show (refused). Stops drawing, but still records the result, if the player
     * left or another menu replaced this one in the meantime.
     */
    private static void play(CoreUi coreUi, Player p, Session s, String kind, Effect item, Effect effect, String message,
                             TextColor color) {
        Runnable finish = () -> {
            s.anim = null;
            s.animFrame = -1;
            s.animItem = null;
            s.busy = false;
            s.lightsOut();
            s.effect = effect;
            s.message = message;
            s.messageColor = color;
        };
        if (effect == null || item == null) {
            finish.run();
            show(coreUi, p);
            return;
        }
        s.anim = kind;
        s.animItem = item;
        int total = frames(kind);
        for (int frame = 0; frame <= total; frame++) {
            int f = frame;
            long at = (long) frame * ticks(kind);
            coreUi.getServer().getScheduler().runTaskLater(coreUi, () -> {
                boolean ours = p.isOnline() && SESSIONS.get(p.getUniqueId()) == s && coreUi.getSession(p)
                        .map(session -> session.getMenu().getId().equals(ID)).orElse(false);
                if (f == total) {
                    finish.run();
                    if (!ours) return;
                    s.pop = s.effect != null;
                    show(coreUi, p);
                    if (s.pop) coreUi.getServer().getScheduler().runTaskLater(coreUi, () -> {
                        s.pop = false;
                        boolean still = p.isOnline() && SESSIONS.get(p.getUniqueId()) == s && s.effect != null
                                && coreUi.getSession(p).map(session -> session.getMenu().getId().equals(ID)).orElse(false);
                        if (still) show(coreUi, p);
                    }, 2L);
                    return;
                }
                if (!ours || s.anim == null) return;
                s.animFrame = f;
                frameSound(p, kind, f);
                show(coreUi, p);
            }, at);
        }
    }

    private static void frameSound(Player p, String kind, int frame) {
        switch (kind) {
            case "upgrade" -> {
                p.playSound(p, Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.9f, 1.0f);
                p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.9f, 1.4f);
            }
            case "break" -> {
                p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.7f, 1.4f);
                p.playSound(p, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.BLOCKS, 0.9f, 1.2f);
            }
            default -> p.playSound(p, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.6f, 1.4f);
        }
    }

    /** The player's gear the blacksmith works on: hotbar first, then the rest of the inventory and armour slots. */
    static List<Gear> gear(Player player, SmithyService smithy) {
        List<Gear> out = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < 41; slot++) {
            if (!smithy.isGear(player, slot)) continue;
            ItemStack stack = inventory.getItem(slot);
            var view = smithy.upgradeView(player, slot);
            out.add(new Gear(slot, stack.getType().getKey().getKey(), name(stack), view.level(), view.tech()));
        }
        return out;
    }

    /**
     * The name shown in the list: a rename, else the item name set on it (breakthrough gear: "오리할콘 곡괭이"), else the
     * Korean vanilla name. The vanilla name is a translation the client fills in, so as plain text it would be English.
     */
    static String name(ItemStack stack) {
        var meta = stack.getItemMeta();
        if (meta != null && meta.hasCustomName()) return PLAIN.serialize(meta.customName());
        if (meta != null && meta.hasItemName()) return PLAIN.serialize(meta.itemName());
        return korean(stack.getType().name());
    }

    private static final Map<String, String> MATERIALS = Map.of("WOODEN", "나무", "STONE", "돌", "IRON", "철",
            "GOLDEN", "금", "DIAMOND", "다이아몬드", "NETHERITE", "네더라이트", "COPPER", "구리", "LEATHER", "가죽",
            "CHAINMAIL", "사슬");
    private static final Map<String, String> KINDS = Map.ofEntries(Map.entry("PICKAXE", "곡괭이"), Map.entry("SHOVEL", "삽"),
            Map.entry("AXE", "도끼"), Map.entry("HOE", "괭이"), Map.entry("SWORD", "검"), Map.entry("SPEAR", "창"),
            Map.entry("HELMET", "투구"), Map.entry("CHESTPLATE", "흉갑"), Map.entry("LEGGINGS", "레깅스"),
            Map.entry("BOOTS", "부츠"), Map.entry("FISHING_ROD", "낚싯대"), Map.entry("TRIDENT", "삼지창"),
            Map.entry("MACE", "철퇴"), Map.entry("CROSSBOW", "쇠뇌"), Map.entry("BOW", "활"));

    /** Korean vanilla name of a gear material: DIAMOND_PICKAXE -> 다이아몬드 곡괭이, FISHING_ROD -> 낚싯대. */
    static String korean(String material) {
        if (KINDS.containsKey(material)) return KINDS.get(material);
        int cut = material.indexOf('_');
        if (cut > 0) {
            String tier = MATERIALS.get(material.substring(0, cut));
            String kind = KINDS.get(material.substring(cut + 1));
            if (tier != null && kind != null) return tier + " " + kind;
        }
        return material.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Gear strip rows: 24 px slot at x 18, top y = STRIP_Y + i * STRIP_ROW (y % 9 == 7 so the icon at y + 4 is on 2). */
    static final int STRIP_Y = 52, STRIP_ROW = 27, STRIP_ROWS = 7;
    /** Centre column middle, breakthrough ring centre and radius (tools/smithy_art.py RING_R). */
    static final int CX = 168, RING_CY = 120, RING_R = 48;
    /** beam_i image offsets from the ring centre (printed by tools/smithy_art.py). */
    static final int[][] BEAMS = {{0, -48}, {15, -15}, {9, 12}, {-28, 12}, {-46, -15}};
    static final int PANEL_X = 270, PANEL_CX = 337;

    /** One frame; pure apart from the callbacks, so the preview tool can build it. */
    static CoreMenu build(Map<String, Glyph> g, View v, Actions act) {
        Session s = v.session();
        SmithyCanvas c = new SmithyCanvas(g, ID, "대장간");
        c.image("smithy/ui/bg_0", 0, 0);
        c.image("smithy/ui/bg_1", 210, 0);
        c.image("smithy/ui/hammer", 11, 9);
        c.text("대장간", 31, 13, SmithyCanvas.GOLD);
        for (Mode mode : Mode.values()) {
            int x = 226 + mode.ordinal() * 54;
            boolean on = mode == s.mode;
            c.image(on ? "smithy/ui/tab_on" : "smithy/ui/tab_off", x, 9);
            c.centered(mode.label, x + 25, 13, on ? SmithyCanvas.TEXT : SmithyCanvas.DIM);
            c.click(x, 9, 50, 16, p -> act.mode(p, mode), null);
        }
        c.image("smithy/ui/close", 394, 9);
        c.click(394, 9, 16, 16, act::close, HoverEvent.showText(Component.text("닫기")));

        strip(c, s, v.gear(), act);
        Gear selected = null;
        for (Gear x : v.gear()) if (x.slot() == s.slot) selected = x;
        switch (s.mode) {
            case UPGRADE -> upgradePage(c, s, selected, v.upgrade(), act);
            case BREAK -> breakPage(c, s, selected, v.breakthrough(), act);
            case CORE -> corePage(c, s, v.core(), act);
        }
        if (s.animFrame >= 0 && s.animItem != null) animScene(c, s);
        else if (s.effect != null) effectScene(c, s, act);
        else if (s.message != null) c.centered(s.message, CX, 226, s.messageColor);
        return c.build();
    }

    /** The flash before a result: white rays and a shock ring around the item, nothing else. */
    private static void animScene(SmithyCanvas c, Session s) {
        Effect item = s.animItem;
        int top = switch (s.anim) {
            case "break" -> RING_CY - 24;
            case "core" -> 76;
            default -> 70;
        };
        int cy = top + 24;
        c.image("smithy/fx/flash", CX - 70, Math.max(42, cy - 70));
        c.image("smithy/fx/shock", CX - 61, Math.max(33, cy - 61));
        c.bigItem(item.icon(), CX - 24, top);
    }

    /**
     * The result scene in the centre column: backdrop (rays, smoke), the item (cracked when destroyed), the stamp and
     * the message. Any click on the column dismisses it.
     */
    private static void effectScene(SmithyCanvas c, Session s, Actions act) {
        Effect e = s.effect;
        String backdrop = switch (e.kind()) {
            case "success" -> "smithy/fx/burst_gold";
            case "failure" -> "smithy/fx/smoke";
            case "destroyed" -> "smithy/fx/burst_red";
            case "break" -> "smithy/fx/burst_blue";
            default -> "smithy/fx/burst_violet";
        };
        TextColor color = switch (e.kind()) {
            case "success" -> SmithyCanvas.GOLD;
            case "failure" -> SmithyCanvas.DIM;
            case "destroyed" -> SmithyCanvas.RED;
            case "break" -> SmithyCanvas.BLUE;
            default -> SmithyCanvas.VIOLET;
        };
        if (c.has(backdrop)) c.image(backdrop, CX - 70, 42);
        c.centered(e.name(), CX, 38, color);
        c.bigItem(e.icon(), CX - 24, 70);
        if (e.kind().equals("destroyed") && c.has("smithy/fx/cracks")) c.image("smithy/fx/cracks", CX - 24, 71);
        String stamp = "smithy/fx/stamp_" + e.kind();
        if (c.has(stamp)) c.image(stamp, CX - c.g.get(stamp).width() / 2, s.pop ? 122 : 128);
        int y = 172;
        if (s.message != null) {
            for (String line : wrap(c, s.message, 180)) {
                c.centered(line, CX, y, s.messageColor);
                y += 12;
            }
        }
        c.centered("눌러서 계속", CX, 214, SmithyCanvas.SUBTLE);
        c.click(74, 32, 186, 212, act::dismiss, null);
    }

    /** Left strip: the gear as icons with their level; pages of STRIP_ROWS with ◀ ▶ in the header. */
    private static void strip(SmithyCanvas c, Session s, List<Gear> gear, Actions act) {
        int pages = Math.max(1, (gear.size() + STRIP_ROWS - 1) / STRIP_ROWS);
        s.page = Math.clamp(s.page, 0, pages - 1);
        if (pages > 1) {
            c.text("◀", 13, 37, SmithyCanvas.DIM);
            c.click(10, 35, 12, 12, p -> act.page(p, -1), null);
            c.centered((s.page + 1) + "/" + pages, 40, 37, SmithyCanvas.DIM);
            c.text("▶", 61, 37, SmithyCanvas.DIM);
            c.click(58, 35, 12, 12, p -> act.page(p, 1), null);
        } else {
            c.centered("장비", 40, 37, SmithyCanvas.DIM);
        }
        if (gear.isEmpty()) {
            c.centered("없음", 40, 60, SmithyCanvas.SUBTLE);
            return;
        }
        for (int i = 0; i < STRIP_ROWS; i++) {
            int index = s.page * STRIP_ROWS + i;
            if (index >= gear.size()) break;
            Gear row = gear.get(index);
            int y = STRIP_Y + i * STRIP_ROW;
            boolean on = row.slot() == s.slot;
            if (on) c.image("smithy/ui/slot_on", 16, y - 2);
            else c.image("smithy/ui/slot", 18, y);
            c.item(row.icon(), 22, y + 4);
            c.text("+" + row.level(), 47, y + 8, on ? SmithyCanvas.EMBER : SmithyCanvas.DIM);
            String tip = row.name() + " +" + row.level() + (row.tech().equals("기본") ? "" : " · " + row.tech());
            c.click(16, y - 2, 56, 28, p -> act.select(p, row.slot()), HoverEvent.showText(Component.text(tip)));
        }
    }

    private static TextColor techColor(String tech) {
        return switch (tech) {
            case "오리할콘" -> TextColor.color(0xE8A060);
            case "미스릴" -> TextColor.color(0x8FE3E8);
            case "솔라리움" -> TextColor.color(0xFFD24A);
            default -> SmithyCanvas.TEXT;
        };
    }

    private static void noGear(SmithyCanvas c, String title) {
        c.centered(title, PANEL_CX, 38, SmithyCanvas.TEXT);
        c.centered("왼쪽에서 장비를 고르세요", CX, 112, SmithyCanvas.DIM);
        c.centered("다이아몬드·네더라이트 장비,", PANEL_CX, 60, SmithyCanvas.DIM);
        c.centered("낚싯대·삼지창·철퇴·쇠뇌·활", PANEL_CX, 72, SmithyCanvas.DIM);
        c.centered("을 강화할 수 있어요", PANEL_CX, 84, SmithyCanvas.DIM);
    }

    private static void upgradePage(SmithyCanvas c, Session s, Gear selected, UpgradeView v, Actions act) {
        if (selected == null || v == null) {
            noGear(c, "강화");
            return;
        }
        // Centre: glow, name, level plate, gear on the anvil, pips (the result scene takes its place).
        if (s.effect == null && s.animFrame < 0) upgradeCentre(c, selected, v);


        // Right panel.
        if (!v.ready()) {
            c.centered("강화", PANEL_CX, 38, SmithyCanvas.TEXT);
            int y = 56;
            for (String line : wrap(c, v.message(), 134)) {
                c.centered(line, PANEL_CX, y, SmithyCanvas.RED);
                y += 12;
            }
            if (v.gauge() > 0) artisan(c, v.gauge(), 100);
            c.image("smithy/ui/btn_off_134", PANEL_X, 210);
            c.centered("강화할 수 없어요", PANEL_CX, 219, SmithyCanvas.DIM);
            return;
        }
        c.centered("+" + v.level() + "  →  +" + v.target(), PANEL_CX, 38, SmithyCanvas.TEXT);
        if (v.guaranteed()) {
            c.bigNumber("100%", "green", PANEL_CX, 52);
            c.centered("장인의 기운으로 확정 성공", PANEL_CX, 74, SmithyCanvas.GOLD);
        } else if (v.destroy() > 0) {
            c.bigNumber(v.success() + "%", "green", PANEL_CX - 29, 52);
            c.centered("성공", PANEL_CX - 29, 74, SmithyCanvas.DIM);
            c.image("smithy/ui/vline", PANEL_CX + 4, 54);
            c.bigNumber(v.destroy() + "%", "red", PANEL_CX + 37, 52);
            c.centered("파괴", PANEL_CX + 37, 74, SmithyCanvas.DIM);
        } else {
            c.bigNumber(v.success() + "%", "green", PANEL_CX, 52);
            c.centered("성공 · 파괴 없음", PANEL_CX, 74, SmithyCanvas.DIM);
        }
        artisan(c, v.gauge(), 94);
        // Materials, cost and the button sit at the bottom of the panel.
        c.image("smithy/ui/hline", PANEL_X, 124);
        c.text("재료", PANEL_X, 131, SmithyCanvas.DIM);
        needs(c, v.needs(), 142);
        money(c, v.silver(), v.gold(), 176);
        boolean can = v.enoughItems() && !s.busy;
        c.image(can ? "smithy/ui/btn_on_134" : "smithy/ui/btn_off_134", PANEL_X, 210);
        if (can) {
            c.centered("강화", PANEL_CX, 219, SmithyCanvas.TEXT);
            c.click(PANEL_X, 210, 134, 26, act::upgrade, null);
        } else {
            c.centered(s.busy ? "강화하는 중…" : "재료가 부족해요", PANEL_CX, 219, SmithyCanvas.DIM);
        }
    }

    private static void upgradeCentre(SmithyCanvas c, Gear selected, UpgradeView v) {
        c.image("smithy/ui/glow_ember", CX - 70, 42);
        c.centered(selected.name(), CX, 38, techColor(v.tech()));
        c.image("smithy/ui/level_plate", CX - 24, 49);
        c.centered("+" + v.level() + " / " + v.cap(), CX, 51, SmithyCanvas.TEXT);
        c.bigItem(selected.icon(), CX - 24, 70);
        c.image("smithy/ui/anvil", CX - 33, 124);
        c.centered("강화 단계", CX, 158, SmithyCanvas.DIM);
        pips(c, v.level(), v.cap(), v.max(), v.perTier(), 170);
        if (v.max() > 5) c.centered("다이아 · 오리할콘 · 미스릴 · 솔라리움", CX, 196, SmithyCanvas.SUBTLE);
    }

    /** Upgrade pips: {@code max} diamonds in rows of 2 x perTier, a gap between tiers, coloured by tier. */
    private static void pips(SmithyCanvas c, int level, int cap, int max, int perTier, int y) {
        String[] tiers = {"d", "o", "m", "s"};
        int cols = Math.min(max, perTier * 2);
        int rowWidth = cols * 9 + (cols / perTier - 1) * 4 - 2;
        int x0 = CX - rowWidth / 2;
        for (int i = 0; i < max; i++) {
            int col = i % cols, row = i / cols;
            int x = x0 + col * 9 + (col / perTier) * 4, py = y + row * 10;
            String tier = max <= 5 ? "d" : tiers[Math.min(3, i / perTier)];
            String state = i < level ? "on" : i < cap ? "open" : "lock";
            c.image("smithy/ui/pip_" + tier + "_" + state, x, py);
        }
    }

    private static void artisan(SmithyCanvas c, double gauge, int y) {
        c.text("장인의 기운", PANEL_X, y, SmithyCanvas.GOLD);
        c.right(percent(gauge), PANEL_X + 134, y, gauge >= 100 ? SmithyCanvas.GREEN : SmithyCanvas.GOLD);
        c.gauge(PANEL_X, y + 11, gauge / 100.0);
        c.click(PANEL_X, y, 134, 18, p -> { }, HoverEvent.showText(Component.text(
                "실패하면 차오르고, 100%가 되면 다음 강화가 반드시 성공해요")));
    }

    /** Material slots in a row (slot top y % 9 == 7, icon on 2), "보유/필요" beside each. */
    private static void needs(SmithyCanvas c, List<Need> needs, int y) {
        int x = PANEL_X;
        for (Need need : needs) {
            String value = NUMBER.format(need.have()) + "/" + NUMBER.format(need.amount());
            c.image("smithy/ui/slot", x, y);
            c.item(need.icon(), x + 4, y + 4);
            c.text(value, x + 27, y + 8, need.enough() ? SmithyCanvas.GREEN : SmithyCanvas.RED);
            c.click(x, y, 27 + c.width(value), 24, p -> { },
                    HoverEvent.showText(Component.text(need.name() + " " + need.amount() + "개 필요")));
            x += Math.max(68, 33 + c.width(value));
        }
    }

    private static void money(SmithyCanvas c, long silver, long gold, int y) {
        c.image("menu/icon/silver", PANEL_X, y);
        c.text(NUMBER.format(silver), PANEL_X + 19, y + 4, SmithyCanvas.TEXT);
        if (gold > 0) {
            c.image("menu/icon/gold", PANEL_X + 78, y);
            c.text(NUMBER.format(gold), PANEL_X + 97, y + 4, SmithyCanvas.TEXT);
        }
    }

    private static void breakPage(SmithyCanvas c, Session s, Gear selected, BreakView v, Actions act) {
        if (selected == null || v == null) {
            noGear(c, "돌파");
            return;
        }
        boolean can = v.ready() && v.enoughItems() && !s.busy;
        if (s.effect == null && s.animFrame < 0) breakCentre(c, s, selected, v, can, act);

        // Right panel.
        breakPanel(c, v);
    }

    private static void breakCentre(SmithyCanvas c, Session s, Gear selected, BreakView v, boolean can, Actions act) {
        c.centered(selected.name() + "  +" + v.level() + " / " + v.cap(), CX, 40, techColor(v.from()));
        int lit = s.litCount();
        // Glow grows with the lights; ring, beams, gear, then orbs (later lines paint over earlier ones).
        if (can) c.image("smithy/ui/glow_blue", CX - 62, RING_CY - 62);
        if (can && lit >= 3) c.image("smithy/ui/glow_blue", CX - 62, RING_CY - 62);
        c.image("smithy/ui/ring", CX - RING_R - 8, RING_CY - RING_R - 8);
        for (int i = 0; i < LIGHTS; i++) {
            if (s.lit[i]) c.image("smithy/ui/beam_" + i, CX + BEAMS[i][0], RING_CY + BEAMS[i][1]);
        }
        c.bigItem(selected.icon(), CX - 24, RING_CY - 24);
        for (int i = 0; i < LIGHTS; i++) {
            int index = i;
            double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
            int ox = CX + (int) Math.round(RING_R * Math.cos(a)), oy = RING_CY + (int) Math.round(RING_R * Math.sin(a));
            if (s.lit[i]) c.image("smithy/ui/glow_orb", ox - 14, oy - 14);
            c.image(s.lit[i] ? "smithy/ui/orb_on" : "smithy/ui/orb_off", ox - 7, oy - 7);
            if (can && !s.lit[i]) c.click(ox - 9, oy - 9, 19, 19, p -> act.light(p, index),
                    HoverEvent.showText(Component.text("빛 모으기")));
        }
        if (can) {
            c.centered("구슬을 모두 눌러 빛을 모으세요  " + lit + " / " + LIGHTS, CX, 190, SmithyCanvas.BLUE);
            c.centered("닫으면 아무것도 쓰지 않아요", CX, 204, SmithyCanvas.SUBTLE);
        } else if (s.busy) {
            c.centered("빛이 모이는 중…", CX, 190, SmithyCanvas.BLUE);
        }
    }

    private static void breakPanel(SmithyCanvas c, BreakView v) {
        c.centered("돌파", PANEL_CX, 38, SmithyCanvas.TEXT);
        if (v.to() == null) {
            int y = 60;
            for (String line : wrap(c, v.message(), 134)) {
                c.centered(line, PANEL_CX, y, SmithyCanvas.RED);
                y += 12;
            }
            return;
        }
        c.image("smithy/ui/badge_from", PANEL_X + 2, 52);
        c.centered(v.from().equals("기본") ? "네더라이트" : v.from(), PANEL_X + 29, 55, SmithyCanvas.DIM);
        c.centered("→", PANEL_CX, 55, SmithyCanvas.TEXT);
        c.image("smithy/ui/badge_to", PANEL_X + 78, 52);
        c.centered(v.to(), PANEL_X + 105, 55, TextColor.color(0xFFDCB4));
        if (v.ready()) {
            c.centered("성공 100%", PANEL_CX, 74, SmithyCanvas.GREEN);
            c.centered("단계 · 각인 · 슬롯 유지", PANEL_CX, 86, SmithyCanvas.DIM);
            c.centered("돌파 후 +" + (v.cap() + 1) + "부터 강화", PANEL_CX, 98, SmithyCanvas.TEXT);
        } else {
            int y = 72;
            for (String line : wrap(c, v.message(), 134)) {
                if (y > 100) break;
                c.centered(line, PANEL_CX, y, SmithyCanvas.RED);
                y += 12;
            }
        }
        c.image("smithy/ui/hline", PANEL_X, 124);
        c.text("재료", PANEL_X, 131, SmithyCanvas.DIM);
        needs(c, v.needs(), 142);
        money(c, v.silver(), 0, 176);
        if (v.ready() && !v.enoughItems()) c.centered("재료가 부족해요", PANEL_CX, 219, SmithyCanvas.RED);
        else if (v.ready()) c.centered("가운데 구슬 5개를 누르면 돌파해요", PANEL_CX, 219, SmithyCanvas.SUBTLE);
    }

    private static void corePage(SmithyCanvas c, Session s, CoreView v, Actions act) {
        if (s.effect == null && s.animFrame < 0) coreCentre(c, v);
        corePanel(c, v, act);
    }

    private static void coreCentre(SmithyCanvas c, CoreView v) {
        c.image("smithy/ui/glow_violet", CX - 60, 40);
        c.centered("마력 핵", CX, 38, SmithyCanvas.VIOLET);
        c.bigItem("heart_of_the_sea", CX - 24, 76);
        for (int i = 0; i < v.perCore().size(); i++) {
            Need need = v.perCore().get(i);
            int x = CX - 61 + i * 52;
            c.image("smithy/ui/slot", x, 151);
            c.item(need.icon(), x + 4, 155);
            c.centered(need.name(), x + 12, 179, SmithyCanvas.DIM);
            c.centered(NUMBER.format(need.have()), x + 12, 191, need.have() > 0 ? SmithyCanvas.GREEN : SmithyCanvas.RED);
        }
        c.centered("각 1개 → 마력 핵 1개", CX, 207, SmithyCanvas.SUBTLE);
    }

    private static void corePanel(SmithyCanvas c, CoreView v, Actions act) {

        c.centered("만들 수 있는 개수", PANEL_CX, 46, SmithyCanvas.DIM);
        c.bigNumber(String.valueOf(v.possible()), "violet", PANEL_CX, 60);
        c.centered("도구 6강부터 강화에 써요", PANEL_CX, 100, SmithyCanvas.DIM);
        c.centered("실버는 들지 않아요", PANEL_CX, 112, SmithyCanvas.DIM);
        boolean can = v.possible() > 0;
        c.image(can ? "smithy/ui/btn_on_62" : "smithy/ui/btn_off_62", PANEL_X, 212);
        c.centered("1개", PANEL_X + 31, 220, can ? SmithyCanvas.TEXT : SmithyCanvas.DIM);
        c.image(can ? "smithy/ui/btn_on_62" : "smithy/ui/btn_off_62", PANEL_X + 72, 212);
        c.centered("모두", PANEL_X + 103, 220, can ? SmithyCanvas.TEXT : SmithyCanvas.DIM);
        if (can) {
            c.click(PANEL_X, 212, 62, 24, p -> act.craft(p, 1), null);
            c.click(PANEL_X + 72, 212, 62, 24, p -> act.craft(p, Math.min(64, v.possible())),
                    HoverEvent.showText(Component.text(Math.min(64, v.possible()) + "개 만들기")));
        }
    }

    /** Splits text into lines no wider than {@code max} px, breaking at spaces. */
    static List<String> wrap(SmithyCanvas c, String text, int max) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (c.width(next) <= max || line.isEmpty()) {
                line.setLength(0);
                line.append(next);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    static String percent(double value) {
        return value == Math.floor(value) ? (long) value + "%" : String.format(Locale.ROOT, "%.1f%%", value);
    }
}
