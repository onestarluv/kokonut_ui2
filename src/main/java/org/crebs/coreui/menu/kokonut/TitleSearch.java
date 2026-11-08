package org.crebs.coreui.menu.kokonut;

import java.util.*;
import java.util.function.Function;

/**
 * Title search: case and space insensitive "contains" on the title, Hangul initial consonants ("ㅋㅋㄴ" finds
 * "코코넛"), {@code #12} for a title number, then the description. Title hits come first, in their original order.
 */
public final class TitleSearch {
    private static final String INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";

    private TitleSearch() {
    }

    /** What the search compares: plain title and description text (no tags) and the title number. */
    public record Target(long id, String title, String description) { }

    public static <T> List<T> filter(List<T> items, String query, Function<T, Target> target) {
        String q = normalize(query);
        if (q.isEmpty()) return List.copyOf(items);
        List<Map.Entry<Integer, T>> hits = new ArrayList<>();
        for (T item : items) {
            int score = score(q, target.apply(item));
            if (score >= 0) hits.add(Map.entry(score, item));
        }
        hits.sort(Map.Entry.comparingByKey()); // stable: equal scores keep the definition order
        return hits.stream().map(Map.Entry::getValue).toList();
    }

    /** Lower rank = better: 0 title prefix, 1 title contains, 2 initials, 3 description; -1 no match. */
    static int score(String q, Target t) {
        if (q.startsWith("#")) {
            String digits = q.substring(1);
            return !digits.isEmpty() && digits.chars().allMatch(Character::isDigit) && digits.equals(Long.toString(t.id())) ? 0 : -1;
        }
        String title = normalize(t.title());
        if (title.startsWith(q)) return 0;
        if (title.contains(q)) return 1;
        if (q.chars().allMatch(c -> INITIALS.indexOf(c) >= 0) && initials(title).contains(q)) return 2;
        return normalize(t.description()).contains(q) ? 3 : -1;
    }

    static String normalize(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        value.toLowerCase(Locale.ROOT).codePoints().filter(c -> !Character.isWhitespace(c)).forEach(out::appendCodePoint);
        return out.toString();
    }

    /** Each Hangul syllable becomes its initial consonant; everything else is kept. */
    static String initials(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) out.append(c >= 0xAC00 && c <= 0xD7A3 ? INITIALS.charAt((c - 0xAC00) / 588) : c);
        return out.toString();
    }
}
