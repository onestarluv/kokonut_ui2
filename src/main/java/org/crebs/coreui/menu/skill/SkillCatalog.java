package org.crebs.coreui.menu.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample data of the skill tree prototype ({@link SkillMenu}): one skill (채광) with its tech tree, level rewards,
 * passive bonuses, XP table and a few statistics. There is no skill system behind it yet; a real one would supply
 * the same records.
 */
final class SkillCatalog {
    /** Requirement id of nodes that hang directly from the hub (the skill itself). */
    static final String HUB = "hub";

    enum Branch {
        SURVEY("탐사"), DIG("채굴"), SUPPORT("보조");

        final String label;

        Branch(String label) { this.label = label; }
    }

    /**
     * One node of the tree. {@code row} and {@code h} place it on {@link SkillMenu}'s grid (rows top to bottom,
     * columns in half steps); {@code icon} is a vanilla item key. {@code requires} lists node ids or {@link #HUB};
     * every one of them must be learned first. {@code level} is the skill level needed.
     */
    record Node(String id, String name, String icon, Branch branch, int tier, int row, int h, int cost, int level,
                List<String> requires, String description, String effect) {
    }

    record LevelReward(int level, String icon, String title, String reward) {
    }

    /** Passive bonus that grows with the skill level: {@code perLevel} percent per level. */
    record Bonus(String name, double perLevel) {
    }

    record XpSource(String icon, String name, int xp) {
    }

    record Stat(String label, String value) {
    }

    record Skill(String id, String name, String icon, int maxLevel, int levelsPerPoint, List<Node> nodes,
                 List<LevelReward> rewards, List<Bonus> bonuses, List<XpSource> sources, List<Stat> stats) {
        Node node(String id) {
            if (id == null) return null;
            for (Node n : nodes) if (n.id().equals(id)) return n;
            return null;
        }

        /** Nodes that list {@code parent} as a requirement. */
        List<Node> children(Node parent) {
            return nodes.stream().filter(n -> n.requires().contains(parent.id())).toList();
        }

        /** Total cost of the nodes of each branch, in declaration order. */
        Map<Branch, Integer> branchCost() {
            Map<Branch, Integer> out = new LinkedHashMap<>();
            for (Branch b : Branch.values()) out.put(b, 0);
            for (Node n : nodes) out.merge(n.branch(), n.cost(), Integer::sum);
            return out;
        }
    }

    private static Node node(String id, String name, String icon, Branch branch, int tier, int row, int h, int cost,
                             int level, List<String> requires, String description, String effect) {
        return new Node(id, name, icon, branch, tier, row, h, cost, level, requires, description, effect);
    }

    /** 채광: 탐사 above the hub, 채굴 below it, one 보조 node on each side. */
    static final Skill MINING = new Skill("mining", "채광", "diamond_pickaxe", 50, 5, List.of(
            // 탐사, 3단계 (row 0)
            node("luck", "행운의 손길", "rabbit_foot", Branch.SURVEY, 3, 0, 2, 1, 20, List.of("hawkeye"),
                    "손끝에 운이 따릅니다. 광석을 캘 때 가끔 광물이 하나 더 떨어집니다.", "광물 추가 획득 확률 +8%"),
            node("survey", "광맥 탐지", "filled_map", Branch.SURVEY, 3, 0, 4, 1, 20, List.of("hawkeye"),
                    "웅크린 채 곡괭이를 들면 가장 가까운 광맥의 방향을 느낍니다.", "탐지 범위 24칸 · 재사용 대기 60초"),
            node("resonance", "광맥 공명", "echo_shard", Branch.SURVEY, 3, 0, 6, 1, 30, List.of("sense", "geology"),
                    "광물을 채굴하면 일정 확률로 주변에 연결된 추가 광맥을 발견합니다.", "추가 광맥 발견 확률 +25%"),
            node("appraise", "원석 감정", "emerald", Branch.SURVEY, 3, 0, 8, 1, 20, List.of("gemcraft"),
                    "원석의 결을 읽어 더 좋은 품질로 캐냅니다.", "보석 원석 품질 상승 확률 +10%"),
            node("stardust", "별의 조각", "nether_star", Branch.SURVEY, 3, 0, 10, 1, 40, List.of("gemcraft"),
                    "깊은 곳에서 아주 드물게 별빛이 깃든 광물을 찾아냅니다.", "희귀 광물 발견 확률 +3%"),
            // 탐사, 1~2단계 (row 1)
            node("hawkeye", "매의 눈", "spyglass", Branch.SURVEY, 2, 1, 3, 1, 10, List.of("sense"),
                    "광석을 캐면 주변에 숨은 같은 광석의 윤곽이 잠시 보입니다.", "반경 6칸 · 3초 동안 표시"),
            node("sense", "광부의 감각", "compass", Branch.SURVEY, 1, 1, 5, 1, 1, List.of(HUB),
                    "오랜 경험으로 돌의 소리를 듣습니다. 광석에서 광물을 더 많이 얻습니다.", "광물 획득량 +10%"),
            node("geology", "지질 분석", "brush", Branch.SURVEY, 1, 1, 7, 1, 1, List.of(HUB),
                    "지층을 살펴 광물이 묻힌 자리를 가늠합니다. 채광 경험치를 더 얻습니다.", "채광 경험치 +15%"),
            node("gemcraft", "보석 세공", "amethyst_shard", Branch.SURVEY, 2, 1, 9, 1, 10, List.of("geology"),
                    "보석을 다치지 않게 캐내어 더 비싼 값에 팝니다.", "보석류 판매가 +10%"),
            // 보조 (row 2, beside the hub)
            node("whet", "숫돌질", "flint", Branch.SUPPORT, 1, 2, 3, 1, 1, List.of(HUB),
                    "틈틈이 날을 세워 곡괭이를 오래 씁니다.", "곡괭이 내구도 소모 -15%"),
            node("lamp", "광부의 등불", "lantern", Branch.SUPPORT, 1, 2, 9, 1, 1, List.of(HUB),
                    "어두운 갱도에 익숙해져 빛이 없어도 손이 느려지지 않습니다.", "어둠 속 채굴 속도 감소 없음"),
            // 채굴, 1단계 (row 3)
            node("arm", "강인한 팔", "golden_pickaxe", Branch.DIG, 1, 3, 4, 1, 1, List.of(HUB),
                    "곡괭이질에 힘이 붙어 돌과 광석을 더 빨리 캡니다.", "채굴 속도 +8%"),
            node("focus", "집중 채굴", "blaze_powder", Branch.DIG, 1, 3, 6, 1, 1, List.of(HUB),
                    "같은 광석을 이어서 캘수록 손이 빨라집니다. 다른 블록을 캐면 초기화됩니다.", "연속 채굴마다 속도 +2% (최대 10회)"),
            node("stamina", "지구력", "golden_apple", Branch.DIG, 1, 3, 8, 1, 1, List.of(HUB),
                    "오래 일해도 쉽게 지치지 않습니다.", "채굴 중 허기 소모 -20%"),
            // 채굴, 2단계 (row 4)
            node("blast", "폭파 채굴", "gunpowder", Branch.DIG, 2, 4, 2, 1, 10, List.of("arm"),
                    "웅크린 채 광석을 캐면 화약을 터뜨려 주변 돌까지 함께 부숩니다.", "3×3 범위 채굴 · 재사용 대기 30초"),
            node("swift", "신속한 손놀림", "sugar", Branch.DIG, 2, 4, 4, 1, 10, List.of("arm"),
                    "쉴 틈 없이 곡괭이를 휘두릅니다.", "채굴 속도 +10%"),
            node("chain", "연쇄 채굴", "redstone", Branch.DIG, 2, 4, 6, 1, 20, List.of("focus"),
                    "광석 하나를 캐면 맞닿은 같은 광석이 연달아 부서집니다.", "최대 8개까지 연쇄"),
            node("insight", "숙련의 깨달음", "experience_bottle", Branch.DIG, 2, 4, 8, 1, 10, List.of("stamina"),
                    "일하면서 배웁니다. 광석을 캘 때 경험 구슬이 더 나옵니다.", "광석 경험 구슬 +25%"),
            node("pack", "넉넉한 배낭", "bundle", Branch.DIG, 2, 4, 10, 1, 10, List.of("stamina"),
                    "짐 싸는 요령이 생겨 캔 광물이 바로 가방에 들어옵니다.", "캔 광물 자동 획득"),
            // 채굴, 3단계 (row 5)
            node("frenzy", "광란의 채굴", "netherite_pickaxe", Branch.DIG, 3, 5, 3, 2, 50, List.of("blast", "swift"),
                    "희귀 광석을 캐면 피가 끓어올라 한동안 미친 듯이 캡니다.", "8초 동안 채굴 속도 +40%"),
            node("blessing", "대지의 축복", "diamond", Branch.DIG, 3, 5, 6, 2, 40, List.of("chain"),
                    "대지가 광부를 알아봅니다. 연쇄로 부순 광석에도 행운이 적용됩니다.", "연쇄 채굴에 행운 효과 적용"),
            node("undying", "불굴의 광부", "totem_of_undying", Branch.DIG, 3, 5, 9, 2, 50, List.of("insight", "pack"),
                    "갱도에서 쓰러질 만큼 다쳐도 한 번은 버텨 냅니다.", "지하에서 치명상 1회 방어 · 대기 10분")),
            List.of(new LevelReward(10, "iron_pickaxe", "Lv. 10 달성", "견습 광부의 곡괭이"),
                    new LevelReward(20, "gold_ingot", "Lv. 20 달성", "골드 2,000"),
                    new LevelReward(30, "enchanted_book", "Lv. 30 달성", "행운 II 마법이 부여된 책"),
                    new LevelReward(40, "diamond", "Lv. 40 달성", "다이아몬드 ×8"),
                    new LevelReward(50, "netherite_pickaxe", "Lv. 50 달성", "칭호 「광맥의 주인」")),
            List.of(new Bonus("채굴 속도", 0.2), new Bonus("채광 경험치", 0.1), new Bonus("광물 판매가", 0.1)),
            List.of(new XpSource("coal", "석탄", 5), new XpSource("raw_copper", "구리 원석", 6),
                    new XpSource("raw_iron", "철 원석", 10), new XpSource("redstone", "레드스톤", 12),
                    new XpSource("raw_gold", "금 원석", 14), new XpSource("lapis_lazuli", "청금석", 16),
                    new XpSource("diamond", "다이아몬드", 40), new XpSource("emerald", "에메랄드", 50)),
            List.of(new Stat("캔 광석", "12,480개"), new Stat("발견한 광맥", "312곳"),
                    new Stat("누적 경험치", "48,240 XP"), new Stat("서버 순위", "17위")));

    private SkillCatalog() {
    }
}
