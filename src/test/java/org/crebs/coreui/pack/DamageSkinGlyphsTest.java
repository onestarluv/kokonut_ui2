package org.crebs.coreui.pack;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class DamageSkinGlyphsTest {
    @Test
    void cutsTenCellsTrimmedToTheirVisibleColumns() throws Exception {
        // 10 cells of 32x32; digit n is a bar of n + 2 opaque columns starting at x = 4. Cell 7 is empty.
        BufferedImage strip = new BufferedImage(320, 32, BufferedImage.TYPE_INT_ARGB);
        for (int n = 0; n < 10; n++) {
            if (n == 7) continue;
            for (int x = 0; x < n + 2; x++) for (int y = 0; y < 32; y++) strip.setRGB(n * 32 + 4 + x, y, 0xFFFFFFFF);
        }
        var entries = DamageSkinGlyphs.digits("breeze", strip);
        assertEquals(9, entries.size());
        var three = entries.get(3);
        assertEquals("damage/digit/breeze/3", three.id());
        assertEquals(16, three.height());
        assertEquals(Math.round(5 * 16 / 32f), three.width());
        BufferedImage png = ImageIO.read(new ByteArrayInputStream(three.png()));
        assertEquals(5, png.getWidth());
        assertEquals(32, png.getHeight());
        assertFalse(entries.stream().anyMatch(e -> e.id().endsWith("/7")));
    }
}
