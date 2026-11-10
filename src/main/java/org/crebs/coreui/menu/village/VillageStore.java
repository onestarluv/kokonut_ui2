package org.crebs.coreui.menu.village;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PROTOTYPE store for the one sample village, in server memory only (gone on restart), so promotions, kicks, the
 * icon and the notice survive between screens. It follows Kokonut-Core's village model; a Core village service
 * replaces it later.
 */
final class VillageStore {
    /** Kokonut-Core roles ({@code OWNER, OFFICER, MEMBER}) with the names players see. */
    enum Role {
        OWNER("마을장"), OFFICER("부마을장"), MEMBER("주민");
        final String label;
        Role(String label) { this.label = label; }
    }

    /**
     * {@code account} is the Minecraft name whose skin the 8 px head shows (sample: the MHF_ heads); {@code intro}
     * is the member's own introduction (empty when not written).
     */
    record Member(String name, String account, Role role, String joined, String lastSeen, String intro) {
        Member role(Role r) { return new Member(name, account, r, joined, lastSeen, intro); }
        Member intro(String text) { return new Member(name, account, role, joined, lastSeen, text); }
    }

    /** 마을장, then 부마을장, then everyone by join date (earliest first); same-day joins keep their joining order. */
    static final java.util.Comparator<Member> ORDER = java.util.Comparator.<Member, Role>comparing(Member::role).thenComparing(Member::joined);

    /** Immutable snapshot the screens draw. {@code icon} is a vanilla item key, or {@code "vil"} (the hand-drawn village icon). */
    record Village(String name, int grade, List<Member> members, long chunks, String home, String founded,
                   String icon, String notice, String noticeBy, String noticeAt) {
        Member owner() {
            return members.stream().filter(m -> m.role() == Role.OWNER).findFirst().orElse(null);
        }
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final List<Member> MEMBERS = new ArrayList<>(List.of(
            new Member("핑퐁", "MHF_Steve", Role.OWNER, "2026.11.08", "접속 중", "코코넛 마을을 만든 핑퐁이에요.\n광장 공사 중이니 원목 기부 환영해요!"),
            new Member("달빛", "MHF_Alex", Role.OFFICER, "2026.11.08", "1시간 전", "밤에 주로 접속해요. 낚시 좋아해요."),
            new Member("모래", "MHF_Villager", Role.MEMBER, "2026.11.08", "어제", ""),
            new Member("솔솔", "MHF_Pig", Role.MEMBER, "2026.11.09", "접속 중", "농사 담당! 밀 필요하면 말해 주세요."),
            new Member("하늘", "MHF_Chicken", Role.MEMBER, "2026.11.10", "3일 전", ""),
            new Member("도토리", "MHF_Cow", Role.MEMBER, "2026.11.09", "어제", "건축 연습 중이에요."),
            new Member("바람", "MHF_Sheep", Role.MEMBER, "2026.11.09", "2시간 전", ""),
            new Member("초코", "MHF_Creeper", Role.MEMBER, "2026.11.10", "접속 중", "광산 탐험가입니다."),
            new Member("별빛", "MHF_Zombie", Role.MEMBER, "2026.11.08", "방금 전", "")));
    private static String icon = "vil";
    private static String notice = "이번 주말에 마을 광장 공사를 해요.\n참나무 원목을 창고 상자에 넣어 주세요!\n\n공사 중에는 광장 대신 북쪽 다리로 다녀 주세요.";
    private static String noticeBy = "핑퐁", noticeAt = "2026.11.09";

    private VillageStore() {
    }

    static Village snapshot() {
        return new Village("코코넛 마을", 3, MEMBERS.stream().sorted(ORDER).toList(), 34, "world · 120, 64, -340", "2026.11.08",
                icon, notice, noticeBy, noticeAt);
    }

    static void setIcon(String key) {
        icon = key;
    }

    static void setNotice(String text, String author) {
        notice = text;
        noticeBy = author;
        noticeAt = LocalDate.now().format(DAY);
    }

    /** Toggles 부마을장 ↔ 주민; the 마을장 is never changed here. */
    static void toggleOfficer(String name) {
        for (int i = 0; i < MEMBERS.size(); i++) {
            Member m = MEMBERS.get(i);
            if (!m.name().equals(name) || m.role() == Role.OWNER) continue;
            MEMBERS.set(i, m.role(m.role() == Role.OFFICER ? Role.MEMBER : Role.OFFICER));
        }
    }

    static void setIntro(String name, String text) {
        MEMBERS.replaceAll(m -> m.name().equals(name) ? m.intro(text) : m);
    }

    static void kick(String name) {
        MEMBERS.removeIf(m -> m.name().equals(name) && m.role() != Role.OWNER);
    }
}
