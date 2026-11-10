package org.crebs.coreui.menu.render;

import com.google.gson.Gson;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Label fonts and text measurement.
 *
 * Labels use Galmuri and vanilla ASCII for the configured characters. Each font is shifted down by its
 * index to place labels at any pixel inside a 9px text line.
 *
 * Vanilla bitmap advances are whole pixels. Galmuri advances are multiples of 1/16 px.
 */
public final class TextWidths {
    /** Smallest step of a Galmuri advance at size 8 / oversample 16. */
    static final int SUBPIXELS = 16;
    public static final String DEFAULT_VANILLA_CHARS =
            "1234567890!?;:@#$%^&*()-=_+`~qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM<>/{},.[]'\"|\\ ";
    private static final Map<Integer, Float> GALMURI = load("/coreui-widths.properties");
    private static final Map<Integer, Float> VANILLA = load("/vanilla-ascii-widths.properties");
    private static volatile Set<Integer> vanillaChars = parse(DEFAULT_VANILLA_CHARS);

    private TextWidths() {
    }

    /** Sets which characters use the vanilla font. Only printable ASCII is accepted. */
    public static void configure(String chars) {
        vanillaChars = parse(chars);
    }

    private static Set<Integer> parse(String chars) {
        Set<Integer> set = new HashSet<>();
        for (int cp : chars.codePoints().toArray()) {
            if (!VANILLA.containsKey(cp)) throw new IllegalArgumentException(
                    "text.vanilla-chars may only contain printable ASCII: U+" + Integer.toHexString(cp));
            set.add(cp);
        }
        return Set.copyOf(set);
    }

    /** Font for a label whose top sits {@code offset} (0..8) pixels into its 9px text line. */
    static Key font(int offset) {
        return Key.key("coreui", "text_" + offset);
    }

    /** Display width rounded to whole GUI pixels, for layout (centering, right alignment). */
    public static int width(Component component) {
        return Math.round(exactWidth(component));
    }

    /** Exact display width in GUI pixels, as the client sums it. */
    public static float exactWidth(Component component) {
        return exactWidth(component, vanillaChars, false);
    }

    private static float exactWidth(Component component, Set<Integer> vanilla, boolean inheritedBold) {
        boolean bold = switch (component.decoration(TextDecoration.BOLD)) {
            case TRUE -> true;
            case FALSE -> false;
            case NOT_SET -> inheritedBold;
        };
        String value = component instanceof TextComponent text ? text.content() : plain(component.children(List.of()));
        float width = 0;
        for (int cp : value.codePoints().toArray()) {
            width += vanilla.contains(cp) ? VANILLA.get(cp) : GALMURI.getOrDefault(cp, 6f);
            if (bold) width += 1;
        }
        for (Component child : component.children()) width += exactWidth(child, vanilla, bold);
        return width;
    }

    /**
     * Invisible Galmuri glyphs (no outline) that pad a label of {@code sixteenths}/16 px up to a whole pixel, so
     * the renderer can step back with whole-pixel spaces. Fractional space-font advances cannot be used: Nexo
     * rewrites space providers with integer advances (−0.0625 becomes 0). TTF advances are computed by the client
     * from the font itself, so they survive any pack merge. U+00A0 = 51/16 and U+2800 = 77/16 px; both odd, so a
     * few of them reach any remainder.
     */
    static String wholePixelPad(int sixteenths) {
        int need = Math.floorMod(-sixteenths, SUBPIXELS);
        if (need == 0) return "";
        int nbsp = padUnit(0x00A0), blank = padUnit(0x2800);
        for (int total = 1; total <= SUBPIXELS; total++) {
            for (int i = 0; i <= total; i++) {
                int j = total - i;
                if (Math.floorMod(i * nbsp + j * blank, SUBPIXELS) == need) return " ".repeat(i) + "⠀".repeat(j);
            }
        }
        throw new IllegalStateException("No pad for " + need + "/16 px");
    }

    /** Exact advance of a pad string in sixteenths of a pixel. */
    static int padSixteenths(String pad) {
        return pad.codePoints().map(TextWidths::padUnit).sum();
    }

    private static int padUnit(int cp) {
        return Math.round(GALMURI.get(cp) * SUBPIXELS);
    }

    /** Width in CoreUi's own bitmap font, still used by the animated dropdown overlay text. */
    static int overlayWidth(Component component) {
        int width = 0;
        for (char character : plain(component).toCharArray()) {
            width += switch (character) {
                case ' ' -> 4;
                case '!', '.', ',', ':', ';', 'i', '|', '\'' -> 2;
                case '`', 'l' -> 3;
                case '"', '*', 'I', '[', ']', 't' -> 4;
                case 'f', 'k', '<', '>', '(', ')', '{', '}' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
        }
        return width;
    }

    /** JSON of {@code coreui:text_<offset>}, with each provider shifted by the label offset. */
    public static String fontJson(int offset) {
        Set<Integer> vanilla = vanillaChars;
        StringBuilder skip = new StringBuilder();
        vanilla.stream().sorted().forEach(skip::appendCodePoint);
        List<Map<String, Object>> providers = new ArrayList<>();
        // Keep Galmuri aligned with the bitmap text.
        providers.add(Map.of("type", "ttf", "file", "coreui:galmuri11.ttf", "shift", List.of(0, offset - 0.5),
                "size", 8, "oversample", 16, "skip", skip.toString()));
        if (vanilla.contains((int) ' ')) providers.add(Map.of("type", "space", "advances", Map.of(" ", 4)));
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < 16; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < 16; col++) {
                int cp = row * 16 + col;
                line.append(cp != ' ' && vanilla.contains(cp) ? (char) cp : '\u0000');
            }
            rows.add(line.toString());
        }
        providers.add(Map.of("type", "bitmap", "file", "minecraft:font/ascii.png", "ascent", 8 - offset, "chars", rows));
        return new Gson().toJson(Map.of("providers", providers));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Map<Integer, Float> load(String resource) {
        Properties properties = new Properties();
        try (InputStream input = TextWidths.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException(resource + " is missing");
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + resource, exception);
        }
        Map<Integer, Float> widths = new HashMap<>();
        properties.forEach((key, value) -> widths.put(Integer.parseInt(key.toString(), 16), Float.parseFloat(value.toString().trim())));
        return Map.copyOf(widths);
    }
}
