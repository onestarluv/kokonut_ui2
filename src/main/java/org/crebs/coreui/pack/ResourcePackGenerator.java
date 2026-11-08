package org.crebs.coreui.pack;

import com.google.gson.Gson;
import net.outmoded.packer.ResourcePack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.crebs.coreui.glyph.Glyph;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.Deflater;

public final class ResourcePackGenerator {
    private final Path directory;
    private final Path pluginJar;
    private final boolean externalTextShader;

    /**
     * @param externalTextShader true when another plugin (BetterHud) owns the 26.3 text shader and
     *                           CoreUi's logic is injected into it, so CoreUi must not ship a competing copy.
     */
    public ResourcePackGenerator(Path directory, Path pluginJar, boolean externalTextShader) {
        this.directory = directory;
        this.pluginJar = pluginJar;
        this.externalTextShader = externalTextShader;
    }

    private static final String TEXT_SHADER_26_3 = "resource-pack/coreui_26_3/assets/minecraft/shaders/core/text.";
    /** PNGs shipped in the JAR (e.g. the item dex) are copied into Glyphs/ once; existing files are never overwritten. */
    private static final String BUNDLED_GLYPHS = "bundled-glyphs/";

    /**
     * One font per glyph with a provider per allowed offset (normally all nine, each shifted one pixel down), so a
     * glyph can start anywhere in a 9px line. {@code offsetMask} bit n = offset n is available.
     */
    private static void writeFont(ResourcePack pack, Gson gson, String id, String texture, int height, int ascent, int offsetMask) throws IOException {
        List<Map<String, Object>> providers = new ArrayList<>();
        for (int offset = 0; offset < 9; offset++) {
            if ((offsetMask >> offset & 1) == 0) continue;
            providers.add(Map.of("type", "bitmap", "file", texture, "height", height, "ascent", ascent - offset,
                    "chars", List.of(String.valueOf((char) ('' + offset)))));
        }
        byte[] json = gson.toJson(Map.of("providers", providers)).getBytes(StandardCharsets.UTF_8);
        try (InputStream stream = new ByteArrayInputStream(json)) {
            pack.writeInputStream("assets/coreui/font/custom/" + id + ".json", stream);
        }
    }

    private YamlConfiguration bundledYaml(String name) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            var entry = jar.getJarEntry(name);
            if (entry == null) return yaml;
            try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                yaml.load(reader);
            } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
                throw new IOException("Invalid " + name, exception);
            }
        }
        return yaml;
    }

    private static void installBundledGlyphs(JarFile jar, Path input) throws IOException {
        Path root = input.toAbsolutePath().normalize();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().startsWith(BUNDLED_GLYPHS)) continue;
            Path target = root.resolve(entry.getName().substring(BUNDLED_GLYPHS.length())).normalize();
            if (!target.startsWith(root) || Files.exists(target)) continue;
            Files.createDirectories(target.getParent());
            try (InputStream stream = jar.getInputStream(entry)) {
                Files.copy(stream, target);
            }
        }
    }

    public record Result(Path path, Map<String, Glyph> glyphs) {}

    public Result generate() throws IOException {
        Path input = directory.resolve("Glyphs");
        Path output = directory.resolve("Pack");
        Files.createDirectories(input);
        Files.createDirectories(output);
        Path settings = directory.resolve("glyphs.yml");
        if (Files.notExists(settings)) Files.writeString(settings, "glyphs: {}\n");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(settings.toFile());
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IOException("Invalid glyphs.yml", exception);
        }
        ResourcePack pack = new ResourcePack();
        // Bundled glyph sets ship their own height/ascent defaults; entries in glyphs.yml still win.
        YamlConfiguration defaults = bundledYaml("bundled-glyphs.yml");
        List<YamlConfiguration> vanillaTables = List.of(bundledYaml("bundled-vanilla-glyphs.yml"), bundledYaml("bundled-vanilla-items.yml"));
        try (JarFile jar = new JarFile(pluginJar.toFile())) {
            Path example = input.resolve("example-arrow.png");
            if (Files.notExists(example)) {
                var entry = jar.getJarEntry("resource-pack/assets/coreui/textures/glyphs/arrow_up.png");
                if (entry == null) throw new IOException("Bundled example glyph is missing");
                try (InputStream stream = jar.getInputStream(entry)) {
                    Files.copy(stream, example);
                }
            }
            installBundledGlyphs(jar, input);
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (externalTextShader && entry.getName().startsWith(TEXT_SHADER_26_3)) continue;
                if (!entry.isDirectory() && entry.getName().startsWith("resource-pack/")) {
                    try (InputStream stream = jar.getInputStream(entry)) {
                        pack.writeInputStream(entry.getName().substring("resource-pack/".length()), stream);
                    }
                }
            }
        }


        //TODO: kill this code
        Map<String, Glyph> loaded = new TreeMap<>();
        Gson gson = new Gson();
        try (var files = Files.walk(input)) {
            for (Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                String relative = input.relativize(file).toString().replace('\\', '/');
                if (!relative.endsWith(".png")) continue;
                String id = relative.substring(0, relative.length() - 4);
                if (!id.matches("[a-z0-9_/-]+")) throw new IOException("Glyph names must use lowercase letters, numbers, _, - or /: " + relative);
                var image = ImageIO.read(file.toFile());
                if (image == null) throw new IOException("Invalid PNG: " + relative);
                if (image.getWidth() > 256 || image.getHeight() > 256) {
                    // The client draws a missing-glyph box for larger bitmaps and Nexo replaces them with a placeholder.
                    // Usually a leftover from an older bundled set (bundled files are never deleted): tile or remove it.
                    java.util.logging.Logger.getLogger("CoreUi").warning("Skipped glyph " + relative + " ("
                            + image.getWidth() + "x" + image.getHeight() + "): images must be at most 256x256");
                    continue;
                }
                String key = "glyphs." + id;
                int height = config.getInt(key + ".height", defaults.getInt(key + ".height", image.getHeight()));
                int ascent = config.getInt(key + ".ascent", defaults.getInt(key + ".ascent", height));
                if (height < 1 || height > 256 || ascent < -256 || ascent > height) throw new IOException("Invalid height/ascent for glyph: " + id);
                int width = (int) Math.round((double) image.getWidth() * height / image.getHeight());
                int right = 0;
                for (int x = image.getWidth() - 1; x >= 0 && right == 0; x--) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        if ((image.getRGB(x, y) >>> 24) != 0) {
                            right = x + 1;
                            break;
                        }
                    }
                }
                int advance = (int) (right * ((float) height / image.getHeight()) + 0.5F) + 1;
                Glyph glyph = new Glyph(id, width, height, ascent, advance);
                loaded.put(id, glyph);
                pack.write("assets/coreui/textures/custom/" + relative, file);
                writeFont(pack, gson, id, "coreui:custom/" + relative, height, ascent, Glyph.ALL_OFFSETS);
            }
        }
        // Glyphs that draw the client's own textures (e.g. vanilla item icons): only a font is written, sizes come
        // from bundled-vanilla-glyphs.yml (tools/VanillaGlyphs.java) and bundled-vanilla-items.yml (one icon per
        // vanilla item, tools/VanillaItemIcons.java). A PNG with the same id wins. "offsets" limits the providers.
        for (YamlConfiguration vanilla : vanillaTables) {
            var vanillaSection = vanilla.getConfigurationSection("vanilla");
            if (vanillaSection == null) continue;
            for (String id : vanillaSection.getKeys(false)) {
                if (loaded.containsKey(id)) continue;
                var entry = vanillaSection.getConfigurationSection(id);
                if (entry == null || !id.matches("[a-z0-9_/-]+")) throw new IOException("Invalid vanilla glyph: " + id);
                String texture = Objects.requireNonNull(entry.getString("texture"), "texture of " + id);
                int height = entry.getInt("height"), ascent = entry.getInt("ascent", height);
                if (height < 1 || height > 256 || ascent < -256 || ascent > height) throw new IOException("Invalid height/ascent for glyph: " + id);
                int mask = Glyph.ALL_OFFSETS;
                if (entry.isList("offsets")) {
                    mask = 0;
                    for (int offset : entry.getIntegerList("offsets")) {
                        if (offset < 0 || offset > 8) throw new IOException("Invalid offset for glyph: " + id);
                        mask |= 1 << offset;
                    }
                }
                loaded.put(id, new Glyph(id, entry.getInt("width", height), height, ascent, entry.getInt("advance", height + 1), mask));
                writeFont(pack, gson, id, texture, height, ascent, mask);
            }
        }
        // Label fonts follow the configured vanilla/Galmuri split (see TextWidths), so they are generated here.
        for (int offset = 0; offset < 9; offset++) {
            byte[] json = org.crebs.coreui.menu.render.TextWidths.fontJson(offset).getBytes(StandardCharsets.UTF_8);
            try (InputStream stream = new ByteArrayInputStream(json)) {
                pack.writeInputStream("assets/coreui/font/text_" + offset + ".json", stream);
            }
        }
        Path temporary = Files.createTempFile(output, "coreui-", ".zip");
        try {
            pack.generate(temporary.toString(), Deflater.DEFAULT_COMPRESSION, false).join();
            Path target = output.resolve("CoreUi.zip");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Result(target, Map.copyOf(loaded));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
