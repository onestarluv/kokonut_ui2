package org.crebs.coreui.menu.village;

import java.util.List;
import java.util.UUID;

/**
 * What the village screens draw, already formatted (dates, "접속 중"). {@link VillageBridge} fills it from
 * Kokonut-Core's village service; {@link #sample()} is the fixed sample village for the offline preview tool.
 * Nothing here names Core classes, so the screens load without Kokonut-Core.
 */
final class VillageStore {
    /** Kokonut-Core roles ({@code OWNER, OFFICER, MEMBER}) with the names players see. */
    enum Role {
        OWNER("마을장"), OFFICER("관리자"), MEMBER("주민");
        final String label;
        Role(String label) { this.label = label; }
        boolean manages() { return this != MEMBER; }
    }

    /**
     * {@code account} is the Minecraft name whose skin the 8 px head shows; {@code name} is the nickname shown;
     * {@code intro} is the member's own introduction (empty when not written).
     */
    record Member(UUID id, String name, String account, Role role, String joined, String lastSeen, String intro) { }

    /** Immutable snapshot the screens draw. {@code icon} is a vanilla item key, or {@code "vil"} (the hand-drawn village icon). */
    /** {@code nextChunkCost}: silver for the next chunk (0 free, -1 when the territory is full of {@code maxChunks}). */
    record Village(long id, String name, int grade, List<Member> members, int maxMembers, long chunks, int maxChunks, long nextChunkCost,
                   String home, String founded, String icon, String notice, String noticeBy, String noticeAt) {
        Member owner() {
            return members.stream().filter(m -> m.role() == Role.OWNER).findFirst().orElse(null);
        }

        Member member(UUID id) {
            return members.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
        }
    }

    /** A pending invitation shown to players without a village. */
    record Invite(long villageId, String name, String icon, int grade, long members, int maxMembers, String inviter, String at) { }

    /** The viewer's village (null when not in one), their invitations, and the founding cost in silver. */
    record Overview(Village village, List<Invite> invites, long createCost, int startMembers) { }

    private VillageStore() {
    }

    static final UUID SAMPLE_OWNER = new UUID(0, 1);

    /** Sample data for {@code tools/MenuPreview}: a village seen by its 마을장, or no village with two invitations. */
    static Overview sample(boolean member) {
        if (!member) {
            return new Overview(null, List.of(
                    new Invite(7, "벚꽃 마을", "cherry_sapling", 2, 11, 13, "달빛", "2026.11.13"),
                    new Invite(9, "바다 마을", "nautilus_shell", 0, 3, 8, "모래", "2026.11.12")), 100_000, 8);
        }
        List<Member> members = List.of(
                new Member(SAMPLE_OWNER, "핑퐁", "MHF_Steve", Role.OWNER, "2026.11.08", "접속 중", "코코넛 마을을 만든 핑퐁이에요.\n광장 공사 중이니 원목 기부 환영해요!"),
                new Member(new UUID(0, 2), "달빛", "MHF_Alex", Role.OFFICER, "2026.11.08", "1시간 전", "밤에 주로 접속해요. 낚시 좋아해요."),
                new Member(new UUID(0, 3), "모래", "MHF_Villager", Role.MEMBER, "2026.11.08", "어제", ""),
                new Member(new UUID(0, 9), "별빛", "MHF_Zombie", Role.MEMBER, "2026.11.08", "방금 전", ""),
                new Member(new UUID(0, 4), "솔솔", "MHF_Pig", Role.MEMBER, "2026.11.09", "접속 중", "농사 담당! 밀 필요하면 말해 주세요."),
                new Member(new UUID(0, 6), "도토리", "MHF_Cow", Role.MEMBER, "2026.11.09", "어제", "건축 연습 중이에요."),
                new Member(new UUID(0, 7), "바람", "MHF_Sheep", Role.MEMBER, "2026.11.09", "2시간 전", ""),
                new Member(new UUID(0, 5), "하늘", "MHF_Chicken", Role.MEMBER, "2026.11.10", "3일 전", ""),
                new Member(new UUID(0, 8), "초코", "MHF_Creeper", Role.MEMBER, "2026.11.10", "접속 중", "광산 탐험가입니다."));
        Village village = new Village(1, "코코넛 마을", 3, members, 16, 34, 400, 30_000, "home1 · home1 · 120, 64, -340", "2026.11.08", "vil",
                "이번 주말에 마을 광장 공사를 해요.\n참나무 원목을 창고 상자에 넣어 주세요!\n\n공사 중에는 광장 대신 북쪽 다리로 다녀 주세요.",
                "핑퐁", "2026.11.09");
        return new Overview(village, List.of(), 100_000, 8);
    }
}
