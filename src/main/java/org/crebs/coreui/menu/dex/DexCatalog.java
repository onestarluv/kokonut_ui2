package org.crebs.coreui.menu.dex;

import java.util.List;

/**
 * Read-only data the item dex draws. The bundled {@link #SAMPLE} is placeholder content for layout testing;
 * replace it with real per-player collection data once item tracking exists.
 * Icons are vanilla item keys (glyphs {@code dex/item/<key>} and {@code dex/portrait/<key>}, see tools/dex_vanilla.txt).
 */
public record DexCatalog(List<Category> categories, List<Milestone> globalMilestones, List<Recent> recent,
                         List<TopItem> top, int serverRank, int serverPlayers, String firstDiscovery) {

    public enum Status { FOUND, SEEN, LOCKED }

    /** {@code frame} names a portrait backdrop {@code dex/ui/frame_<frame>}. */
    public record Entry(String id, String name, String icon, Status status, int rarity, String rarityLabel,
                        String frame, String where, String how, int obtained, List<String> description,
                        List<String> uses, int sellPrice, int reward, boolean isNew) {
        public boolean found() { return status == Status.FOUND; }
    }

    public enum MilestoneState { DONE, CLAIMABLE, PROGRESS, LOCKED }

    public record Milestone(String id, String icon, String title, String reward, MilestoneState state, int progress, int goal) {}

    /** {@code registered} counts the category's progress even where no sample entries exist yet. */
    public record Category(String key, String label, String icon, String color, int total, int registered,
                           List<Entry> entries, List<Milestone> milestones) {}

    public record Recent(String icon, String name, String category, String date) {}

    public record TopItem(String icon, String name, int count) {}

    public int registered() { return categories.stream().mapToInt(Category::registered).sum(); }

    public int total() { return categories.stream().mapToInt(Category::total).sum(); }

    public Category category(String key) {
        return categories.stream().filter(c -> c.key().equals(key)).findFirst().orElse(categories.getFirst());
    }

    private static Entry found(String id, String name, int rarity, String rarityLabel, String frame, String where, String how,
                               int obtained, List<String> description, List<String> uses, int price, boolean isNew) {
        return new Entry(id, name, id, Status.FOUND, rarity, rarityLabel, frame, where, how, obtained, description, uses, price, 50, isNew);
    }

    private static Entry seen(String id, String name, String where, String hint) {
        return new Entry(id, name, id, Status.SEEN, 0, "???", "locked", where, "???", 0, List.of(hint), List.of(), 0, 50, false);
    }

    private static List<Milestone> mineralMilestones() {
        return List.of(
                new Milestone("ore-1", "gold_nugget", "첫 등록", "50 코인", MilestoneState.DONE, 1, 1),
                new Milestone("ore-5", "paper", "5종 등록", "칭호 [초보 광부]", MilestoneState.CLAIMABLE, 5, 5),
                new Milestone("ore-10", "gold_nugget", "10종 등록", "200 코인 · [동굴 탐사가]", MilestoneState.PROGRESS, 9, 10),
                new Milestone("ore-20", "bundle", "20종 등록", "광부의 가방 (+9칸)", MilestoneState.LOCKED, 9, 20),
                new Milestone("ore-32", "diamond_pickaxe", "32종 완성", "곡괭이 스킨 · [광맥 기록자]", MilestoneState.LOCKED, 9, 32));
    }

    public static final DexCatalog SAMPLE = new DexCatalog(
            List.of(
                    new Category("ore", "광물", "diamond", "#7E7E8A", 32, 9, List.of(
                            found("coal", "석탄", 1, "흔함", "deep", "지하 어디서나", "곡괭이 채굴", 1284,
                                    List.of("가장 흔한 연료. 불을 붙이면", "오래 타오른다."), List.of(), 1, false),
                            found("raw_copper", "구리 원석", 1, "흔함", "deep", "얕은 동굴 · 산간 지대", "곡괭이 채굴", 402,
                                    List.of("공기에 닿으면 천천히", "푸르게 녹이 슨다."), List.of(), 2, false),
                            found("raw_iron", "철 원석", 2, "보통", "deep", "동굴 중층", "곡괭이 채굴", 611,
                                    List.of("도구와 갑옷의 기본 재료.", "제련하면 단단해진다."), List.of(), 4, false),
                            found("raw_gold", "금 원석", 2, "보통", "deep", "깊은 동굴 · 황무지", "곡괭이 채굴", 96,
                                    List.of("무르지만 상인들이", "가장 반기는 광물."), List.of(), 8, false),
                            found("lapis_lazuli", "청금석", 2, "보통", "deep", "동굴 하층", "곡괭이 채굴", 210,
                                    List.of("갈아서 푸른 안료를 만든다.", "마법 연구에 쓰인다."), List.of(), 6, false),
                            found("redstone", "레드스톤", 2, "보통", "deep", "동굴 최하층", "곡괭이 채굴", 330,
                                    List.of("붉게 빛나는 가루.", "신호를 전한다고 한다."), List.of(), 3, false),
                            found("emerald", "에메랄드", 3, "희귀", "field", "산맥 깊은 곳", "곡괭이 채굴", 17,
                                    List.of("마을 상인들의 화폐로", "쓰이는 귀한 보석."), List.of(), 20, false),
                            found("amethyst_shard", "자수정 조각", 3, "희귀", "cave", "지하 동굴 · 자수정 정동", "자수정 싹 채집", 42,
                                    List.of("깊은 동굴 속 정동 안쪽에서 자란다.", "두드리면 맑은 소리가 난다."),
                                    List.of("amethyst_block", "spyglass"), 12, true),
                            seen("diamond", "다이아몬드", "동굴 최하층 (소문)", "깊은 곳에서 하얗게 빛나는 광석을 봤다"),
                            found("quartz", "네더 석영", 2, "보통", "lava", "네더 황무지", "곡괭이 채굴", 88,
                                    List.of("네더의 붉은 바위 속에", "하얀 결정으로 박혀 있다."), List.of(), 5, false),
                            seen("netherite_scrap", "네더라이트 파편", "네더 용암 바다 아래 (소문)", "용암 바다 아래 잠들어 있다고 한다"),
                            seen("echo_shard", "메아리 조각", "고대 도시 (소문)", "고대 도시에서만 발견된다는 소문")),
                            mineralMilestones()),
                    new Category("crop", "농작물", "wheat", "#C8A040", 24, 14, List.of(), List.of()),
                    new Category("plant", "식물", "sugar_cane", "#C0708A", 30, 11, List.of(), List.of()),
                    new Category("fish", "물고기", "cod", "#4F80C0", 28, 3, List.of(), List.of()),
                    new Category("relic", "유물", "brush", "#8A6AAA", 12, 1, List.of(), List.of()),
                    new Category("misc", "기타", "bundle", "#6E7E6A", 16, 4, List.of(), List.of())),
            List.of(
                    new Milestone("all-25", "paper", "전체 25종 등록", "칭호 [수집 입문자]", MilestoneState.DONE, 25, 25),
                    new Milestone("all-50", "gold_nugget", "전체 50종 등록", "500 코인 · [도감 연구원]", MilestoneState.PROGRESS, 42, 50),
                    new Milestone("all-100", "book", "전체 100종 등록", "금박 도감 표지", MilestoneState.LOCKED, 42, 100),
                    new Milestone("all-142", "totem_of_undying", "전체 142종 완성", "[만물박사] · 트로피", MilestoneState.LOCKED, 42, 142)),
            List.of(new Recent("amethyst_shard", "자수정 조각", "광물", "오늘"), new Recent("wheat", "밀", "농작물", "어제"),
                    new Recent("cod", "대구", "물고기", "11/06"), new Recent("quartz", "네더 석영", "광물", "11/05")),
            List.of(new TopItem("coal", "석탄", 1284), new TopItem("wheat", "밀", 962), new TopItem("raw_iron", "철 원석", 611)),
            12, 238, "네더 석영");
}
