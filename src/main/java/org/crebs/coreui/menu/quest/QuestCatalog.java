package org.crebs.coreui.menu.quest;

import java.util.List;
import java.util.Objects;

/**
 * Quest data for the quest screens: NPC quest givers and their quests. Quests are accepted from an NPC
 * ({@link QuestOfferMenu}); the journal ({@link QuestMenu}) only lists quests in progress and finished ones.
 * The server has no player levels, so quests have none. {@link #SAMPLE} is placeholder content for the prototype.
 */
public record QuestCatalog(List<Giver> givers, List<Quest> quests) {
    /**
     * Where a quest stands for the player. {@code OFFERED} quests only appear at their NPC; the journal's
     * filters are {@link #JOURNAL}.
     */
    public enum Status {
        OFFERED("수락 가능"), ACTIVE("진행"), DONE("완료");

        public static final List<Status> JOURNAL = List.of(ACTIVE, DONE);
        public final String label;

        Status(String label) {
            this.label = label;
        }
    }

    /**
     * An NPC who hands out quests; quests are shown by their giver, not by place. {@code icon} is a vanilla item key
     * standing in for the NPC's portrait; {@code greeting} is up to two lines.
     */
    public record Giver(String id, String name, String icon, List<String> greeting) {
        public Giver {
            greeting = List.copyOf(greeting);
        }
    }

    /** {@code icon} is a vanilla item key for the objective's slot. */
    public record Objective(String text, String icon, int done, int total) {
        public boolean complete() {
            return done >= total;
        }
    }

    /** A reward slot: gold (16 px gold icon), experience (experience bottle) or a vanilla item. */
    public record Reward(Kind kind, String item, String name, long amount) {
        public enum Kind { GOLD, EXP, ITEM }

        public static Reward gold(long amount) { return new Reward(Kind.GOLD, null, "골드", amount); }
        public static Reward exp(long amount) { return new Reward(Kind.EXP, "experience_bottle", "경험치", amount); }
        public static Reward item(String item, String name, long amount) { return new Reward(Kind.ITEM, item, name, amount); }
    }

    /** {@code description} is what the giver says (two or three lines); {@code icon} is a vanilla item key. */
    public record Quest(String id, String giver, String name, String icon, List<String> description,
                        List<Objective> objectives, List<Reward> rewards, Status status) {
        public Quest {
            description = List.copyOf(description);
            objectives = List.copyOf(objectives);
            rewards = List.copyOf(rewards);
        }

        public int done() {
            return objectives.stream().mapToInt(o -> Math.min(o.done(), o.total())).sum();
        }

        public int total() {
            return objectives.stream().mapToInt(Objective::total).sum();
        }

        public boolean complete() {
            return objectives.stream().allMatch(Objective::complete);
        }
    }

    public static final int MAX_ACTIVE = 5;

    public QuestCatalog {
        givers = List.copyOf(givers);
        quests = List.copyOf(quests);
    }

    public Giver giver(String id) {
        return givers.stream().filter(g -> g.id().equals(id)).findFirst().orElse(null);
    }

    public List<Quest> from(Giver giver) {
        return quests.stream().filter(q -> q.giver().equals(giver.id())).toList();
    }

    private static Objective o(String text, String icon, int done, int total) {
        return new Objective(text, icon, done, total);
    }

    public static final QuestCatalog SAMPLE = new QuestCatalog(List.of(
            new Giver("elen", "숲지기 엘렌", "oak_sapling", List.of("숲은 오늘도 조용하네요.", "부탁할 일이 좀 있어요.")),
            new Giver("bori", "상인 보리", "emerald", List.of("어서 와요!", "마침 손이 모자라던 참이에요.")),
            new Giver("doyun", "광부 도윤", "iron_pickaxe", List.of("동굴 안쪽이 수상해.", "한번 봐 주겠나?")),
            new Giver("haru", "안내원 하루", "compass", List.of("처음 오셨나요?", "마을을 소개해 드릴게요."))),
            List.of(
                    new Quest("letter", "elen", "잃어버린 편지", "paper",
                            List.of("숲길 어딘가에 편지를 떨어뜨렸어요.", "슬라임이 반짝이는 걸 좋아한다던데…"),
                            List.of(o("슬라임 처치", "slime_ball", 0, 5), o("편지 조각 모으기", "paper", 0, 3)),
                            List.of(Reward.gold(500), Reward.exp(1200)), Status.OFFERED),
                    new Quest("herb", "elen", "약초꾼의 부탁", "sweet_berries",
                            List.of("상처에 바를 약초가 모자라요.", "강가 근처에서 열매를 찾아 주세요."),
                            List.of(o("달콤한 열매 모으기", "sweet_berries", 0, 8)),
                            List.of(Reward.gold(300), Reward.exp(900), Reward.item("bread", "빵", 8)), Status.OFFERED),
                    new Quest("bridge", "elen", "무너진 다리", "oak_log",
                            List.of("다리를 고칠 나무가 필요해요."),
                            List.of(o("참나무 원목 모으기", "oak_log", 6, 10)),
                            List.of(Reward.gold(800), Reward.exp(1500)), Status.ACTIVE),
                    new Quest("cellar", "bori", "지하 창고 정리", "barrel",
                            List.of("창고에 쥐가 너무 많아요.", "상자도 엉망이 됐고요."),
                            List.of(o("창고 쥐 처치", "rotten_flesh", 12, 12), o("상자 정리", "chest", 3, 3)),
                            List.of(Reward.gold(1500), Reward.exp(3100), Reward.item("emerald", "에메랄드", 2)), Status.ACTIVE),
                    new Quest("fish", "bori", "항구의 아침", "cod",
                            List.of("아침 시장에 낼 생선이 모자라요."),
                            List.of(o("대구 낚기", "cod", 0, 6), o("연어 낚기", "salmon", 0, 3)),
                            List.of(Reward.gold(600), Reward.exp(1300), Reward.item("fishing_rod", "낚싯대", 1)), Status.OFFERED),
                    new Quest("crystal", "doyun", "빛나는 동굴", "amethyst_shard",
                            List.of("동굴 깊은 곳에서 빛이 새어 나와.", "무엇인지 알아봐 주게."),
                            List.of(o("자수정 조각 캐기", "amethyst_shard", 2, 10), o("횃불 설치", "torch", 3, 5),
                                    o("동굴 끝 조사", "ender_eye", 0, 1)),
                            List.of(Reward.gold(1800), Reward.exp(3600), Reward.item("iron_pickaxe", "철 곡괭이", 1)), Status.ACTIVE),
                    new Quest("first", "haru", "첫걸음", "compass",
                            List.of("마을을 한 바퀴 둘러봐요."),
                            List.of(o("광장 둘러보기", "map", 1, 1)),
                            List.of(Reward.gold(100), Reward.exp(300)), Status.DONE)));
}
