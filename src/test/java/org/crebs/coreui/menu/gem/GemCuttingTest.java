package org.crebs.coreui.menu.gem;

import org.crebs.coreui.menu.gem.GemCutting.Cell;
import org.crebs.coreui.menu.gem.GemCutting.Grade;
import org.crebs.coreui.menu.gem.GemCutting.Line;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class GemCuttingTest {
    /** nextInt(100) returns 0 (always lands at chance > 0). */
    private static final RandomGenerator HIT = () -> 0L;
    /** nextInt(100) returns 99 (never lands while chance <= 75). */
    private static final RandomGenerator MISS = new RandomGenerator() {
        @Override public long nextLong() { return -1L; }
        @Override public int nextInt(int bound) { return bound - 1; }
    };

    @Test
    void chanceStartsAt75AndMovesTenPointsWithinBounds() {
        GemCutting gem = new GemCutting();
        assertEquals(75, gem.chance());
        assertTrue(gem.cut(Line.STURDY, HIT).hit());
        assertEquals(65, gem.chance());
        for (int i = 0; i < 5; i++) gem.cut(Line.STURDY, HIT);
        assertEquals(25, gem.chance(), "never below 25%");
        assertFalse(gem.cut(Line.FLAW, MISS).hit());
        assertEquals(35, gem.chance());
        for (int i = 0; i < 8; i++) gem.cut(Line.FLAW, MISS);
        assertEquals(75, gem.chance(), "never above 75%");
        assertEquals(List.of(75, 65), gem.log().subList(0, 2).stream().map(GemCutting.Attempt::chance).toList());
    }

    @Test
    void facetsFillInOrderAndFullLinesRefuseCuts() {
        GemCutting gem = new GemCutting();
        gem.cut(Line.BEAUTY, HIT);
        gem.cut(Line.BEAUTY, MISS);
        assertEquals(List.of(Cell.HIT, Cell.MISS, Cell.EMPTY), gem.cells(Line.BEAUTY).subList(0, 3));
        for (int i = 0; i < 8; i++) gem.cut(Line.BEAUTY, HIT);
        assertTrue(gem.full(Line.BEAUTY));
        int chance = gem.chance();
        assertNull(gem.cut(Line.BEAUTY, HIT));
        assertEquals(chance, gem.chance(), "a refused cut changes nothing");
        assertEquals(20, gem.remaining());
        assertFalse(gem.done());
    }

    @Test
    void scoreIsGoodHitsMinusFlawsAndPicksTheGrade() {
        GemCutting gem = new GemCutting();
        for (int i = 0; i < 10; i++) gem.cut(Line.STURDY, HIT);
        for (int i = 0; i < 10; i++) gem.cut(Line.BEAUTY, HIT);
        assertEquals(20, gem.score());
        for (int i = 0; i < 3; i++) gem.cut(Line.FLAW, HIT);
        for (int i = 0; i < 7; i++) gem.cut(Line.FLAW, MISS);
        assertTrue(gem.done());
        assertEquals(17, gem.score());
        assertEquals(17, gem.best());
        assertEquals(Grade.MYTHIC, gem.grade());
    }

    @Test
    void bestCountsEveryEmptyGoodFacet() {
        GemCutting gem = new GemCutting();
        assertEquals(20, gem.best());
        gem.cut(Line.STURDY, MISS);
        gem.cut(Line.FLAW, HIT);
        assertEquals(18, gem.best());
    }

    @Test
    void gradeThresholds() {
        assertEquals(Grade.COMMON, Grade.of(-10));
        assertEquals(Grade.COMMON, Grade.of(3));
        assertEquals(Grade.UNCOMMON, Grade.of(4));
        assertEquals(Grade.RARE, Grade.of(8));
        assertEquals(Grade.RARE, Grade.of(10));
        assertEquals(Grade.EPIC, Grade.of(11));
        assertEquals(Grade.LEGENDARY, Grade.of(14));
        assertEquals(Grade.LEGENDARY, Grade.of(16));
        assertEquals(Grade.MYTHIC, Grade.of(17));
        assertEquals(Grade.MYTHIC, Grade.of(20));
        assertEquals(Grade.MYTHIC, Grade.LEGENDARY.next());
        assertNull(Grade.MYTHIC.next());
    }
}
