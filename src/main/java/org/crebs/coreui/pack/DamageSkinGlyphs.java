package org.crebs.coreui.pack;

import org.crebs.coreui.glyph.Glyph;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Glyphs for the 꾸미기 데미지 스킨 screen, made from the other plugins' own images on every pack build (nothing is
 * copied into Glyphs/, so a new skin PNG shows up after {@code /coreui reload}):
 * <ul>
 *   <li>{@code damage/digit/<skin>/<0-9>}: kokonut_damage's {@code assets/skins/<skin>.png}, ten equal cells
 *       {@code 0123456789} side by side, each cut out and trimmed to its visible columns, drawn {@value #DIGIT_HEIGHT} px tall.</li>
 *   <li>{@code damage/icon/<skin>}: the skin coupon icon from Nexo's pack ({@code textures/damage_skin/<skin>.png}),
 *       {@value #ICON_SIZE} px.</li>
 * </ul>
 * A PNG with the same id in Glyphs/ wins. Unreadable or oddly sized images are skipped with a warning.
 */
final class DamageSkinGlyphs {
    static final int DIGIT_HEIGHT = 16, ICON_SIZE = 16;
    private static final Logger LOG = Logger.getLogger("CoreUi");

    /** One glyph image: id, PNG bytes and the size it is drawn at. */
    record Entry(String id, byte[] png, int width, int height) {}

    private DamageSkinGlyphs() {
    }

    /** {@code plugins} is the server's plugins folder (the parent of CoreUi's data folder). */
    static List<Entry> collect(Path plugins) throws IOException {
        List<Entry> out = new java.util.ArrayList<>();
        for (Path file : pngs(plugins.resolve("kokonut_damage/assets/skins"))) {
            String skin = skinId(file);
            BufferedImage strip = read(file);
            if (skin == null || strip == null) continue;
            if (strip.getWidth() % 10 != 0) {
                LOG.warning("Skipped damage skin " + file.getFileName() + ": its width must be ten equal digit cells");
                continue;
            }
            out.addAll(digits(skin, strip));
        }
        for (Path file : pngs(plugins.resolve("Nexo/pack/assets/minecraft/textures/damage_skin"))) {
            String skin = skinId(file);
            BufferedImage icon = read(file);
            if (skin == null || icon == null) continue;
            if (icon.getWidth() > 256 || icon.getHeight() > 256) continue;
            int width = Math.max(1, Math.round((float) icon.getWidth() * ICON_SIZE / icon.getHeight()));
            out.add(new Entry("damage/icon/" + skin, png(icon), width, ICON_SIZE));
        }
        return out;
    }

    /** The ten digit cells of one strip, each trimmed to its visible columns (an empty cell is left out). */
    static List<Entry> digits(String skin, BufferedImage strip) throws IOException {
        List<Entry> out = new java.util.ArrayList<>();
        int cell = strip.getWidth() / 10, height = strip.getHeight();
        if (cell < 1 || cell > 256 || height > 256) return out;
        for (int digit = 0; digit < 10; digit++) {
            int left = -1, right = -1;
            for (int x = 0; x < cell; x++) {
                if (!columnEmpty(strip, digit * cell + x, height)) {
                    if (left < 0) left = x;
                    right = x;
                }
            }
            if (left < 0) continue;
            BufferedImage cut = strip.getSubimage(digit * cell + left, 0, right - left + 1, height);
            int width = Math.max(1, Math.round((float) cut.getWidth() * DIGIT_HEIGHT / height));
            out.add(new Entry("damage/digit/" + skin + "/" + digit, png(cut), width, DIGIT_HEIGHT));
        }
        return out;
    }

    /** Writes the textures and fonts; ids already in {@code loaded} (Glyphs/ PNGs) are kept. */
    static void write(net.outmoded.packer.ResourcePack pack, com.google.gson.Gson gson, List<Entry> entries,
                      Map<String, Glyph> loaded, FontWriter fonts) throws IOException {
        for (Entry entry : entries) {
            if (loaded.containsKey(entry.id())) continue;
            String texture = "assets/coreui/textures/custom/" + entry.id() + ".png";
            try (var stream = new java.io.ByteArrayInputStream(entry.png())) {
                pack.writeInputStream(texture, stream);
            }
            // Ascent 8 like the bundled UI kit: the glyph's top lands on the element's y.
            fonts.write(entry.id(), "coreui:custom/" + entry.id() + ".png", entry.height(), 8);
            loaded.put(entry.id(), new Glyph(entry.id(), entry.width(), entry.height(), 8, entry.width() + 1));
        }
    }

    interface FontWriter {
        void write(String id, String texture, int height, int ascent) throws IOException;
    }

    private static boolean columnEmpty(BufferedImage image, int x, int height) {
        for (int y = 0; y < height; y++) if ((image.getRGB(x, y) >>> 24) != 0) return false;
        return true;
    }

    private static List<Path> pngs(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList();
        }
    }

    /** Skin id from the file name, or null (with a warning) when it is not a valid glyph name. */
    private static String skinId(Path file) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - 4);
        if (id.matches("[a-z0-9_-]+")) return id;
        LOG.warning("Skipped damage skin image " + name + ": names must use lowercase letters, numbers, _ or -");
        return null;
    }

    private static BufferedImage read(Path file) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) LOG.warning("Skipped damage skin image " + file + ": not a PNG");
            return image;
        } catch (IOException failure) {
            LOG.warning("Skipped damage skin image " + file + ": " + failure.getMessage());
            return null;
        }
    }

    private static byte[] png(BufferedImage image) throws IOException {
        // Copy into ARGB first: sub-images and palette PNGs are written as-is otherwise.
        BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = argb.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(argb, "png", bytes);
        return bytes.toByteArray();
    }
}
