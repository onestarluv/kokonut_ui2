import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

/**
 * Regenerates coreui-widths.properties with the exact advances Minecraft's TrueTypeGlyphProvider uses:
 * FT_Set_Pixel_Sizes(round(size * oversample)), FT_Load_Glyph(flags 0x400008), advance.x / 64 / oversample.
 */
public class FtWidths {
    public static void main(String[] a) throws Exception {
        float size = 8f, oversample = 16f;
        byte[] bytes = Files.readAllBytes(Path.of(a[0]));
        ByteBuffer font = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        TreeMap<Integer, Float> widths = new TreeMap<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer lib = stack.mallocPointer(1);
            check(FreeType.FT_Init_FreeType(lib), "init");
            long library = lib.get(0);
            PointerBuffer facePtr = stack.mallocPointer(1);
            check(FreeType.FT_New_Memory_Face(library, font, 0, facePtr), "face");
            FT_Face face = FT_Face.create(facePtr.get(0));
            check(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "charmap");
            int px = Math.round(size * oversample);
            check(FreeType.FT_Set_Pixel_Sizes(face, px, px), "size");
            var index = stack.mallocInt(1);
            long cp = FreeType.FT_Get_First_Char(face, index);
            while (index.get(0) != 0) {
                check(FreeType.FT_Load_Glyph(face, index.get(0), 0x400008), "load");
                float advance = face.glyph().advance().x() / 64f / oversample;
                widths.put((int) cp, advance);
                cp = FreeType.FT_Get_Next_Char(face, cp, index);
            }
            FreeType.FT_Done_Face(face);
            FreeType.FT_Done_FreeType(library);
        } finally {
            MemoryUtil.memFree(font);
        }
        StringBuilder out = new StringBuilder("# Galmuri11 advances at size 8 / oversample 16, as Minecraft's ttf provider computes them.\n");
        widths.forEach((k, v) -> out.append(Integer.toHexString(k)).append('=').append(trim(v)).append('\n'));
        Files.writeString(Path.of(a[1]), out);
        Set<Float> fractions = new TreeSet<>();
        widths.values().forEach(v -> fractions.add(v - (float) Math.floor(v)));
        System.out.println("glyphs=" + widths.size() + " distinct fractions=" + fractions
                + " a=" + widths.get((int) 'a') + " A=" + widths.get((int) 'A') + " 1=" + widths.get((int) '1')
                + " space=" + widths.get(32) + " 가=" + widths.get(0xAC00) + " ·=" + widths.get(0xB7));
    }

    private static String trim(float v) {
        String s = Float.toString(v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private static void check(int error, String what) {
        if (error != 0) throw new IllegalStateException(what + " failed: " + error);
    }
}
