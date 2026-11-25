package org.crebs.coreui.menu.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shops and sell tiers from {@code plugins/Kokonut_UI/shops.yml}; loaded on enable and on {@code /coreui reload}. Main thread only. */
public final class ShopRegistry {
    private static Map<String, ShopCatalog> shops = Map.of();
    private static volatile SellTiers tiers = SellTiers.DEFAULT;

    private ShopRegistry() {
    }

    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "shops.yml");
        if (!file.exists()) plugin.saveResource("shops.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        shops = java.util.Collections.unmodifiableMap(ShopCatalog.loadAll(yaml, problem -> plugin.getLogger().warning("shops.yml: " + problem)));
        tiers = SellTiers.load(yaml, problem -> plugin.getLogger().warning("shops.yml: " + problem));
        ShopGoods.reset();
        plugin.getLogger().info("Loaded " + shops.size() + " shop(s) from shops.yml");
    }

    public static Optional<ShopCatalog> get(String id) {
        return Optional.ofNullable(shops.get(id));
    }

    public static Set<String> ids() {
        return shops.keySet();
    }

    /** The daily sell tiers; safe to read from any thread. */
    public static SellTiers tiers() {
        return tiers;
    }
}
