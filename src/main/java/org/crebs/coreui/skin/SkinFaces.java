package org.crebs.coreui.skin;

import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * A player's face as 64 RGB colours (8x8, row by row), read from their skin so menus can draw it at any size with
 * tinted dots. The client's own head object ({@code Component.object}) is fixed at 8x8.
 *
 * Like the client's face renderer: the face layer (8,8)-(15,15) is opaque, the hat layer (40,8)-(47,15) is blended
 * over it by its alpha. Skins are fetched off the server thread from textures.minecraft.net only and cached by
 * texture URL; {@link #face} never blocks and returns null until the face is ready (menus then fall back to the 8x8
 * head object). Faces are prefetched on join, so the first menu usually has them.
 */
public final class SkinFaces implements Listener {
    private static final int MAX_BYTES = 1 << 20;
    private static final int MAX_CACHED = 2048;
    private final Plugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, int[]> faces = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public SkinFaces(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The face colours (0xRRGGBB), or null while unknown; a lookup starts the download when needed. */
    public int[] face(Player player) {
        String url = skinUrl(player);
        if (url == null) return null;
        int[] face = faces.get(url);
        if (face == null) fetch(url);
        return face;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        face(event.getPlayer());
    }

    private void fetch(String url) {
        if (faces.size() >= MAX_CACHED) faces.clear();
        if (!pending.add(url)) return;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            try {
                if (error != null || response.statusCode() != 200 || response.body().length > MAX_BYTES) return;
                BufferedImage skin = ImageIO.read(new ByteArrayInputStream(response.body()));
                int[] face = skin == null ? null : face(skin);
                if (face != null) faces.put(url, face);
            } catch (Exception failure) {
                plugin.getLogger().log(Level.FINE, "Skin face download failed: " + url, failure);
            } finally {
                pending.remove(url);
            }
        });
    }

    /** Face layer (opaque) with the hat layer blended over it; null for images that are not skins. */
    static int[] face(BufferedImage skin) {
        if (skin.getWidth() != 64 || skin.getHeight() != 64 && skin.getHeight() != 32) return null;
        int[] out = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = skin.getRGB(8 + x, 8 + y), hat = skin.getRGB(40 + x, 8 + y);
                int alpha = hat >>> 24;
                int r = blend(base >> 16 & 255, hat >> 16 & 255, alpha);
                int g = blend(base >> 8 & 255, hat >> 8 & 255, alpha);
                int b = blend(base & 255, hat & 255, alpha);
                out[y * 8 + x] = r << 16 | g << 8 | b;
            }
        }
        return out;
    }

    private static int blend(int under, int over, int alpha) {
        return (under * (255 - alpha) + over * alpha + 127) / 255;
    }

    /** The SKIN url from the signed "textures" property, only when it points at Mojang's texture server. */
    static String skinUrl(Player player) {
        for (var property : player.getPlayerProfile().getProperties()) {
            if (!property.getName().equals("textures")) continue;
            try {
                String json = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
                var skin = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
                if (skin == null || !skin.has("url")) return null;
                String url = skin.get("url").getAsString();
                URI uri = URI.create(url);
                boolean mojang = "textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                        && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
                return mojang ? "https://textures.minecraft.net" + uri.getRawPath() : null;
            } catch (RuntimeException malformed) {
                return null;
            }
        }
        return null;
    }
}
