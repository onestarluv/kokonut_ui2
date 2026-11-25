package org.crebs.coreui.menu.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.crebs.coreui.menu.shop.ShopCatalog.Goods;
import org.crebs.coreui.menu.shop.ShopCatalog.Product;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Which inventory items a product stands for, for sell shops. Only the 36 storage slots count (as before).
 * <ul>
 *   <li>Vanilla: a plain stack ({@code isSimilar} to a fresh item), so named, enchanted or plugin items never match.</li>
 *   <li>Nexo: the item's Nexo id ({@code NexoItems.idFromItem}); nothing matches while Nexo is off.</li>
 *   <li>Fish: Kokonut-Fishing's {@code kokonut_fishing:item = fish_<species>} tag (or the Nexo id {@code fish_<species>}),
 *       graded through the plugin's own {@code fish.tsv}, read from its JAR so there is no compile dependency.</li>
 * </ul>
 * Main thread only.
 */
public final class ShopGoods {
    private static final NamespacedKey FISH_ITEM = new NamespacedKey("kokonut_fishing", "item");
    private static Map<String, String> fishGrades;

    private ShopGoods() {
    }

    /** Forgets the fish table so the next lookup reads it again (on {@code /coreui reload}). */
    static void reset() {
        fishGrades = null;
    }

    public static boolean matches(Product product, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        Goods goods = product.goods();
        return switch (goods.kind()) {
            case VANILLA -> {
                Material type = product.material();
                yield type != null && stack.getType() == type && stack.isSimilar(new ItemStack(type));
            }
            case NEXO -> stack.hasItemMeta() && goods.key().equals(nexoId(stack));
            case FISH -> goods.key().equals(fishGrade(stack));
        };
    }

    /** How many matching items the storage slots hold. */
    public static int held(PlayerInventory inventory, Product product) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) if (matches(product, item)) count += item.getAmount();
        return count;
    }

    /**
     * Takes {@code count} matching items from the storage slots and returns what was taken (so a failed payout can
     * give the very same items back); null and nothing taken when there are fewer than {@code count}.
     */
    public static List<ItemStack> remove(PlayerInventory inventory, Product product, int count) {
        if (held(inventory, product) < count) return null;
        List<ItemStack> taken = new ArrayList<>();
        int slots = inventory.getStorageContents().length; // storage slots are inventory slots 0..35
        for (int slot = 0; slot < slots && count > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!matches(product, item)) continue;
            int take = Math.min(count, item.getAmount());
            count -= take;
            taken.add(item.asQuantity(take));
            inventory.setItem(slot, take == item.getAmount() ? null : item.asQuantity(item.getAmount() - take));
        }
        return taken;
    }

    private static String nexoId(ItemStack stack) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) return null;
        try {
            return NexoLookup.id(stack);
        } catch (LinkageError | RuntimeException failure) {
            return null;
        }
    }

    /** "common" … "mystic" for a Kokonut-Fishing fish, else null. */
    private static String fishGrade(ItemStack stack) {
        if (!stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(FISH_ITEM, PersistentDataType.STRING);
        if (id == null) id = nexoId(stack);
        if (id == null || !id.startsWith("fish_")) return null;
        return fishGrades().get(id.substring(5));
    }

    /** species id → grade, from Kokonut-Fishing's bundled {@code fish.tsv} (id, grade, …); empty without the plugin. */
    private static Map<String, String> fishGrades() {
        if (fishGrades != null) return fishGrades;
        Plugin fishing = Bukkit.getPluginManager().getPlugin("Kokonut-Fishing");
        if (fishing == null) return Map.of(); // not cached: the plugin may simply not be there on this server
        Map<String, String> grades = new HashMap<>();
        try (InputStream in = fishing.getResource("fish.tsv")) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] cells = line.split("\t");
                    if (cells.length >= 2) grades.put(cells[0].strip(), cells[1].strip().toLowerCase(Locale.ROOT));
                }
            }
        } catch (java.io.IOException failure) {
            Bukkit.getLogger().warning("[Kokonut_UI] Could not read Kokonut-Fishing fish.tsv: " + failure);
        }
        if (grades.isEmpty()) Bukkit.getLogger().warning("[Kokonut_UI] Kokonut-Fishing fish.tsv has no fish; fish shops will not match anything");
        fishGrades = Map.copyOf(grades);
        return fishGrades;
    }

    /** Kept apart so Nexo's classes are only touched after the plugin check above. */
    private static final class NexoLookup {
        static String id(ItemStack stack) {
            return com.nexomc.nexo.api.NexoItems.idFromItem(stack);
        }
    }
}
