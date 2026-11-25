package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.currency.CurrencyChangeContext;
import kr.gomsv.kokonutCore.api.util.CurrencyUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.menu.furniture.FurnitureCatalog;
import org.crebs.coreui.menu.furniture.FurnitureCatalog.Type;
import org.crebs.coreui.menu.furniture.FurnitureCatalog.Wood;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Furniture workbench crafting: kokonut through Kokonut-Core's currency API, the wood from the player's inventory,
 * the piece as its Nexo item ({@code tmf_<wood>_<type>}).
 * <p>Order (same idea as a shop purchase in {@link ShopTrades}): check the materials and a free slot → take the
 * kokonut off the main thread (Core refuses when the balance is short) → back on the main thread take the materials
 * and give the piece in one go. If the player left or the materials changed while the payment ran, the kokonut is
 * given back, so nothing is ever taken without the piece being handed over.</p>
 * One craft per player at a time. Only load this class after confirming Kokonut-Core is enabled.
 */
public final class FurnitureCrafts {
    private static final Set<UUID> BUSY = new HashSet<>();

    private FurnitureCrafts() {
    }

    /** Crafts one piece; {@code done} runs on the main thread when it has finished (or failed). */
    public static void craft(CoreUi plugin, Player player, Wood wood, Type type, Runnable done) {
        UUID id = player.getUniqueId();
        if (BUSY.contains(id)) return;
        String name = FurnitureCatalog.name(wood, type);
        ItemStack result = result(plugin, wood, type);
        if (result == null) {
            finish(player, false, "가구 아이템(" + FurnitureCatalog.itemId(wood, type) + ")을 찾지 못해서 만들 수 없어요.", done);
            return;
        }
        List<FurnitureCatalog.Material> materials = FurnitureCatalog.materials(wood, type);
        String missing = missing(player.getInventory(), materials);
        if (missing != null) {
            finish(player, false, missing + "이(가) 부족해요.", done);
            return;
        }
        if (ShopTrades.space(player.getInventory(), result) < 1) {
            finish(player, false, "인벤토리에 가구가 들어갈 자리를 비워 주세요.", done);
            return;
        }
        BUSY.add(id);
        int cost = type.kokonut;
        var context = new CurrencyChangeContext("furniture:craft", "가구 제작 " + name, id);
        ShopTrades.async(plugin, () -> CurrencyUtil.takeKokonut(id, cost, context), (paid, error) -> {
            BUSY.remove(id);
            if (error != null) { finish(player, false, "결제 중 오류가 났어요. 잠시 후 다시 시도해 주세요.", done); return; }
            if (!paid) { finish(player, false, "코코넛이 부족해요.", done); return; }
            // Paid. The inventory may have changed while the payment ran: take the materials now or undo the payment.
            if (!player.isOnline() || missing(player.getInventory(), materials) != null) {
                refund(plugin, id, cost, name);
                finish(player, false, "재료가 바뀌어서 제작을 취소하고 코코넛을 돌려드렸어요.", done);
                return;
            }
            for (FurnitureCatalog.Material m : materials) ShopTrades.removePlain(player.getInventory(), plain(m), m.amount());
            ShopTrades.give(player, result, 1);
            finish(player, true, name + "을(를) 만들었어요. (-" + cost + " 코코넛)", done);
        });
    }

    /** The Nexo item of the piece, or null when Nexo is off or does not know it. */
    private static ItemStack result(CoreUi plugin, Wood wood, Type type) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Nexo")) return null;
        try {
            return NexoLookup.item(FurnitureCatalog.itemId(wood, type));
        } catch (LinkageError | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Nexo item lookup failed for " + FurnitureCatalog.itemId(wood, type), failure);
            return null;
        }
    }

    /** Kept apart so Nexo's classes are only touched after the plugin check above. */
    private static final class NexoLookup {
        static ItemStack item(String id) {
            var builder = com.nexomc.nexo.api.NexoItems.itemFromId(id);
            return builder == null ? null : builder.build();
        }
    }

    private static ItemStack plain(FurnitureCatalog.Material material) {
        org.bukkit.Material type = org.bukkit.Material.matchMaterial(material.item());
        return type == null ? null : new ItemStack(type);
    }

    /** Name of the first ingredient the storage slots hold too few plain items of; null when all are there. */
    private static String missing(PlayerInventory inventory, List<FurnitureCatalog.Material> materials) {
        for (FurnitureCatalog.Material m : materials) {
            ItemStack plain = plain(m);
            if (plain == null || ShopTrades.held(inventory, plain) < m.amount()) return m.name();
        }
        return null;
    }

    private static void refund(CoreUi plugin, UUID id, int amount, String name) {
        var context = new CurrencyChangeContext("furniture:craft", "가구 제작 환불 (" + name + ", 지급 전 취소)", id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CurrencyUtil.addKokonut(id, amount, context);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, "Furniture craft refund failed for " + id + ": kokonut " + amount, failure);
            }
        });
    }

    private static void finish(Player player, boolean ok, String message, Runnable done) {
        if (!player.isOnline()) return;
        Messages.send(player, message);
        if (ok) player.playSound(player, Sound.UI_STONECUTTER_TAKE_RESULT, SoundCategory.BLOCKS, 0.8f, 1f);
        else player.playSound(player, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1f);
        done.run();
    }
}
