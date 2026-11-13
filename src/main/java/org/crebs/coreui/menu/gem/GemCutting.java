package org.crebs.coreui.menu.gem;

import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * One rough gem being cut (prototype rules, no plugin data). Three lines of {@link #SLOTS} facets: 견고함 and
 * 아름다움 should land, 결함 should not. Each cut fills the next facet of the chosen line with a hit or a miss; one
 * shared chance starts at {@link #START}% and moves {@link #STEP}%p down after a hit and up after a miss, within
 * {@link #MIN}..{@link #MAX}%. When every facet is filled the score ({@code 견고함 + 아름다움 − 결함}) picks the
 * {@link Grade}.
 */
public final class GemCutting {
    public static final int SLOTS = 10, START = 75, STEP = 10, MIN = 25, MAX = 75;

    public enum Line {
        STURDY("견고함", true), BEAUTY("아름다움", true), FLAW("결함", false);

        public final String label;
        /** True when hits raise the score; 결함 lowers it. */
        public final boolean good;

        Line(String label, boolean good) {
            this.label = label;
            this.good = good;
        }
    }

    public enum Cell { EMPTY, HIT, MISS }

    /**
     * Result grades by score, lowest first. {@code icon} is the vanilla item drawn for the gem (32 px
     * {@code village/icon/<icon>}, temporary art); {@code color} is the grade's text colour on parchment.
     */
    public enum Grade {
        COMMON("일반", Integer.MIN_VALUE, "amethyst_shard", 0x7A6446),
        UNCOMMON("고급", 4, "emerald", 0x3E7A22),
        RARE("희귀", 8, "diamond", 0x2F64A8),
        EPIC("영웅", 11, "ender_pearl", 0x7B3FA8),
        LEGENDARY("전설", 14, "heart_of_the_sea", 0xC0651A),
        MYTHIC("신화", 17, "nether_star", 0xB02040);

        public final String label;
        /** Lowest score of this grade ({@link Integer#MIN_VALUE} for 일반). */
        public final int min;
        public final String icon;
        public final TextColor color;

        Grade(String label, int min, String icon, int color) {
            this.label = label;
            this.min = min;
            this.icon = icon;
            this.color = TextColor.color(color);
        }

        /** The grade above this one, null for 신화. */
        public Grade next() {
            return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
        }

        public static Grade of(int score) {
            Grade result = COMMON;
            for (Grade grade : values()) if (score >= grade.min) result = grade;
            return result;
        }
    }

    /** One cut: the line, whether it landed and the chance it was rolled at. */
    public record Attempt(Line line, boolean hit, int chance) { }

    private final Cell[][] cells = new Cell[Line.values().length][SLOTS];
    private final List<Attempt> log = new ArrayList<>();
    private int chance = START;

    public GemCutting() {
        for (Cell[] line : cells) Arrays.fill(line, Cell.EMPTY);
    }

    public List<Cell> cells(Line line) {
        return List.of(cells[line.ordinal()]);
    }

    public int hits(Line line) {
        return count(line, Cell.HIT);
    }

    public int filled(Line line) {
        return SLOTS - count(line, Cell.EMPTY);
    }

    public boolean full(Line line) {
        return filled(line) == SLOTS;
    }

    public boolean done() {
        for (Line line : Line.values()) if (!full(line)) return false;
        return true;
    }

    /** Facets left on all lines. */
    public int remaining() {
        int left = 0;
        for (Line line : Line.values()) left += SLOTS - filled(line);
        return left;
    }

    /** Success chance of the next cut, in percent. */
    public int chance() {
        return chance;
    }

    public int score() {
        return hits(Line.STURDY) + hits(Line.BEAUTY) - hits(Line.FLAW);
    }

    /** The best score still reachable: every empty good facet lands and no more flaws do. */
    public int best() {
        return score() + (SLOTS - filled(Line.STURDY)) + (SLOTS - filled(Line.BEAUTY));
    }

    public Grade grade() {
        return Grade.of(score());
    }

    /** Cuts, newest last. */
    public List<Attempt> log() {
        return List.copyOf(log);
    }

    /** Cuts the next facet of {@code line}; null (nothing changes) when the line is full. */
    public Attempt cut(Line line, RandomGenerator random) {
        if (full(line)) return null;
        int rolled = chance;
        boolean hit = random.nextInt(100) < rolled;
        cells[line.ordinal()][filled(line)] = hit ? Cell.HIT : Cell.MISS;
        chance = Math.clamp(rolled + (hit ? -STEP : STEP), MIN, MAX);
        Attempt attempt = new Attempt(line, hit, rolled);
        log.add(attempt);
        return attempt;
    }

    private int count(Line line, Cell cell) {
        int n = 0;
        for (Cell c : cells[line.ordinal()]) if (c == cell) n++;
        return n;
    }
}
