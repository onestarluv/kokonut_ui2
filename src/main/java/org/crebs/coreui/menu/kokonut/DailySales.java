package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.database.CoreRedis;
import org.bukkit.Bukkit;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.menu.shop.SellTiers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Each player's base-price silver sold today in {@code daily-tier} shops (경제 기획서 8절), shared by every server
 * through Core's Redis: key {@code <prefix>shop:sold:<yyyy-MM-dd>:<uuid>}, changed only with an atomic
 * {@code INCRBYFLOAT} and kept for three days. Without Core Redis the count lives in this server's memory (lost on
 * restart, not shared) and a warning is logged once.
 * <p>
 * {@link #add} and {@link #fetch} hit Redis: call them off the main thread. {@link #peek} only reads the cache the
 * menu draws from. Only load this class after confirming Kokonut-Core is enabled.
 */
public final class DailySales {
    private static final String SCRIPT = "local v = redis.call('INCRBYFLOAT', KEYS[1], ARGV[1]) "
            + "redis.call('EXPIRE', KEYS[1], ARGV[2]) return v";
    private static final int TTL_SECONDS = 3 * 24 * 3600;
    private record Seen(LocalDate day, double sold) {}

    private static final Map<UUID, Seen> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Double> LOCAL = new ConcurrentHashMap<>();
    private static volatile boolean warned;

    private DailySales() {
    }

    /** Today's total from the last read or sale; null while it has not been read yet today. Any thread. */
    public static Double peek(UUID player, SellTiers tiers) {
        Seen seen = CACHE.get(player);
        return seen != null && seen.day().equals(tiers.today()) ? seen.sold() : null;
    }

    /** Reads today's total (off the main thread). */
    public static double fetch(CoreUi plugin, UUID player, SellTiers tiers) {
        return add(plugin, player, tiers, 0);
    }

    /** Adds {@code gross} (negative to undo) to today's total and returns the new total (off the main thread). */
    public static double add(CoreUi plugin, UUID player, SellTiers tiers, double gross) {
        LocalDate day = tiers.today();
        double total;
        CoreRedis redis = plugin.getServer().getServicesManager().load(CoreRedis.class);
        if (redis != null && redis.enabled()) {
            String key = redis.keyPrefix() + "shop:sold:" + day + ":" + player;
            Object value = redis.eval(SCRIPT, List.of(key), List.of(Double.toString(gross), Integer.toString(TTL_SECONDS)));
            total = Double.parseDouble(String.valueOf(value));
        } else {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Core Redis is off: daily sell tiers are counted in this server's memory only");
            }
            total = LOCAL.merge(day + ":" + player, gross, Double::sum);
        }
        total = Math.max(0, total);
        CACHE.put(player, new Seen(day, total));
        return total;
    }

    /** Loads today's total for the menu in the background; {@code then} runs on the main thread when it is known. */
    public static void load(CoreUi plugin, UUID player, SellTiers tiers, Runnable then) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                fetch(plugin, player, tiers);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not read today's sales for " + player, failure);
                return;
            }
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, then);
        });
    }

    public static void forget(UUID player) {
        CACHE.remove(player);
    }
}
