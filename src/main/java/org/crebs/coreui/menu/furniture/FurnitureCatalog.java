package org.crebs.coreui.menu.furniture;

import java.util.ArrayList;
import java.util.List;

/**
 * What the furniture workbench makes: 13 woods x 28 pieces of the TooManyFurniture set, registered in Nexo as
 * {@code tmf_<wood>_<type>} (see {@code D:/kokonut/tools/toomanyfurniture/tmf_convert.py}).
 * <p>Costs: every piece takes kokonut ({@link #KOKONUT_PLAIN}, or {@link #KOKONUT_USABLE} for pieces you can sit on
 * or store items in) plus planks, logs and sticks of its wood, more for larger and heavier pieces. The item amounts
 * are placeholders until the real balance is decided.</p>
 */
public final class FurnitureCatalog {
    /** Kokonut for decoration-only pieces (tables, desks) and for pieces with a function (seats, storage). */
    public static final int KOKONUT_PLAIN = 3, KOKONUT_USABLE = 5;
    public static final String STICK = "stick";

    /** What a placed piece does (Nexo seats / storage, see tmf_convert.py SEAT_TYPES and STORAGE). */
    public enum Use { NONE, SEAT, STORAGE }

    private FurnitureCatalog() {
    }

    /** Vanilla wood families in the order of the wood slots (same order as tmf_convert.py WOOD_KO). */
    public enum Wood {
        OAK("oak", "참나무", "oak_log", "원목"),
        SPRUCE("spruce", "가문비나무", "spruce_log", "원목"),
        BIRCH("birch", "자작나무", "birch_log", "원목"),
        JUNGLE("jungle", "정글", "jungle_log", "원목"),
        ACACIA("acacia", "아카시아", "acacia_log", "원목"),
        DARK_OAK("dark_oak", "짙은 참나무", "dark_oak_log", "원목"),
        MANGROVE("mangrove", "맹그로브", "mangrove_log", "원목"),
        CHERRY("cherry", "벚나무", "cherry_log", "원목"),
        PALE_OAK("pale_oak", "창백한 참나무", "pale_oak_log", "원목"),
        POPLAR("poplar", "포플러나무", "poplar_log", "원목"),
        BAMBOO("bamboo", "대나무", "bamboo_block", "블록"),
        CRIMSON("crimson", "진홍빛", "crimson_stem", "자루"),
        WARPED("warped", "뒤틀린", "warped_stem", "자루");

        public final String key, label, log, logLabel;

        Wood(String key, String label, String log, String logWord) {
            this.key = key;
            this.label = label;
            this.log = log;
            this.logLabel = label + " " + logWord;
        }

        /** Vanilla item key of this wood's planks. */
        public String planks() {
            return key + "_planks";
        }
    }

    /** Furniture pieces in the order of the recipe grid (four per row): seats, tables, desks, storage, shelves. */
    public enum Type {
        STOOL("stool", "스툴", Use.SEAT, 1, 0, 3),
        MODERN_STOOL("modern_stool", "모던 스툴", Use.SEAT, 2, 0, 2),
        TALL_STOOL("tall_stool", "높은 스툴", Use.SEAT, 1, 0, 4),
        CHAIR("chair", "의자", Use.SEAT, 2, 0, 4),
        FANCY_CHAIR("fancy_chair", "고급 의자", Use.SEAT, 3, 1, 0),
        MODERN_CHAIR("modern_chair", "모던 의자", Use.SEAT, 3, 0, 2),
        BENCH("bench", "벤치", Use.SEAT, 3, 0, 4),
        LONG_BENCH("long_bench", "긴 벤치", Use.SEAT, 6, 0, 6),
        TABLE("table", "탁자", Use.NONE, 3, 0, 4),
        LONG_TABLE("long_table", "긴 탁자", Use.NONE, 6, 0, 4),
        BIG_TABLE("big_table", "큰 탁자", Use.NONE, 12, 0, 4),
        FANCY_TABLE("fancy_table", "고급 탁자", Use.NONE, 3, 1, 0),
        FANCY_LONG_TABLE("fancy_long_table", "고급 긴 탁자", Use.NONE, 6, 2, 0),
        FANCY_BIG_TABLE("fancy_big_table", "고급 큰 탁자", Use.NONE, 12, 4, 0),
        OUTSIDE_TABLE("outside_table", "야외 탁자", Use.NONE, 4, 0, 4),
        OUTSIDE_BIG_TABLE("outside_big_table", "큰 야외 탁자", Use.NONE, 8, 0, 8),
        DESKTOP("desktop", "책상", Use.NONE, 4, 0, 2),
        LONG_DESKTOP("long_desktop", "긴 책상", Use.NONE, 8, 0, 4),
        DESKTOP_CORNER("desktop_corner", "모서리 책상", Use.NONE, 6, 0, 3),
        BUREAU("bureau", "서랍장", Use.STORAGE, 6, 1, 0),
        LONG_BUREAU("long_bureau", "긴 서랍장", Use.STORAGE, 12, 2, 0),
        CRATE("crate", "상자", Use.STORAGE, 6, 0, 0),
        LONG_CRATE("long_crate", "높은 상자", Use.STORAGE, 12, 0, 0),
        BIG_CRATE("big_crate", "큰 상자", Use.STORAGE, 24, 0, 0),
        SHELVING("shelving", "선반", Use.STORAGE, 4, 0, 2),
        LONG_SHELVING("long_shelving", "긴 선반", Use.STORAGE, 8, 0, 4),
        MODERN_SHELVING("modern_shelving", "모던 선반", Use.STORAGE, 4, 1, 0),
        LONG_MODERN_SHELVING("long_modern_shelving", "긴 모던 선반", Use.STORAGE, 8, 2, 0);

        public final String key, label;
        public final Use use;
        public final int planks, logs, sticks, kokonut;

        Type(String key, String label, Use use, int planks, int logs, int sticks) {
            this.key = key;
            this.label = label;
            this.use = use;
            this.kokonut = use == Use.NONE ? KOKONUT_PLAIN : KOKONUT_USABLE;
            this.planks = planks;
            this.logs = logs;
            this.sticks = sticks;
        }
    }

    /** One ingredient: vanilla item key, Korean name and how many are used. */
    public record Material(String item, String name, int amount) {}

    /** "참나무 긴 탁자". */
    public static String name(Wood wood, Type type) {
        return wood.label + " " + type.label;
    }

    /** The Nexo item this recipe gives, e.g. {@code tmf_oak_long_table}. */
    public static String itemId(Wood wood, Type type) {
        return "tmf_" + art(wood, type);
    }

    /** File name of the piece's pictures: {@code furniture/icon/<art>} (16 px) and {@code furniture/preview/<art>} (32 px). */
    public static String art(Wood wood, Type type) {
        return wood.key + "_" + type.key;
    }

    /** Item ingredients in display order (planks, then logs, then sticks); kokonut is charged on top. */
    public static List<Material> materials(Wood wood, Type type) {
        List<Material> list = new ArrayList<>(3);
        list.add(new Material(wood.planks(), wood.label + " 판자", type.planks));
        if (type.logs > 0) list.add(new Material(wood.log, wood.logLabel, type.logs));
        if (type.sticks > 0) list.add(new Material(STICK, "막대기", type.sticks));
        return list;
    }
}
