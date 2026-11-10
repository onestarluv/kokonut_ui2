package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.currency.CurrencyChangeContext;
import kr.gomsv.kokonutCore.api.util.CurrencyUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.crebs.coreui.CoreUi;
import org.crebs.coreui.Messages;
import org.crebs.coreui.menu.shop.ShopCatalog;
import org.crebs.coreui.menu.shop.ShopCatalog.Currency;
import org.crebs.coreui.menu.shop.ShopCatalog.Mode;
import org.crebs.coreui.menu.shop.ShopCatalog.Product;

import java.util.*;
import java.util.logging.Level;

/**
 * Shop trades through Kokonut-Core's currency API. Core's take/add calls hit the database, so they run off the main
 * thread (as Core's own /재화 command does); inventory changes stay on the main thread.
 * <ul>
 *   <li>Buy: check inventory space → take the currency (Core refuses when the balance is short) → give the items.
 *       If the player left in between, the payment is refunded.</li>
 *   <li>Sell: take the plain items from the storage slots → add the currency. If paying fails the items go back.</li>
 * </ul>
 * One trade per player at a time. Only load this class after confirming Kokonut-Core is enabled.
 */
public final class ShopTrades {
    private static final Set<UUID> BUSY = new HashSet<>();

    private ShopTrades() {
    }

    public static boolean busy(Player player) {
        return BUSY.contains(player.getUniqueId());
    }

    /** Trades {@code units} single items; {@code done} runs on the main thread when it has finished (or failed). */
    public static void trade(CoreUi plugin, Player player, ShopCatalog shop, Product product, int units, Runnable done) {
        Material type = product.material();
        if (type == null || units < 1) { done.run(); return; }
        if (!BUSY.add(player.getUniqueId())) return;
        int count = units;
        double total = total(product.currency(), product.price() * units);
        UUID id = player.getUniqueId();
        var context = new CurrencyChangeContext("shop:" + shop.id(),
                "상점 " + shop.mode().verb + " " + shop.name() + " · " + product.name() + " " + count + "개", id);
        ItemStack plain = new ItemStack(type);
        if (shop.mode() == Mode.BUY) {
            if (space(player.getInventory(), plain) < count) {
                finish(player, false, "인벤토리 공간이 부족해요. " + count + "개가 들어갈 자리를 비워 주세요.", done);
                return;
            }
            async(plugin, () -> take(id, product.currency(), total, context), (paid, error) -> {
                if (error != null) { finish(player, false, "결제 중 오류가 났어요. 잠시 후 다시 시도해 주세요.", done); return; }
                if (!paid) { finish(player, false, product.currency().label + "이(가) 부족해요.", done); return; }
                if (!player.isOnline()) { refund(plugin, id, product.currency(), total, shop); BUSY.remove(id); return; }
                give(player, plain, count);
                finish(player, true, product.name() + " " + count + "개를 샀어요. (-" + product.currency().format(total) + " " + product.currency().label + ")", done);
            });
        } else {
            if (!removePlain(player.getInventory(), plain, count)) {
                finish(player, false, product.name() + "이(가) " + count + "개 필요해요.", done);
                return;
            }
            async(plugin, () -> { add(id, product.currency(), total, context); return true; }, (ok, error) -> {
                if (error != null) {
                    if (player.isOnline()) give(player, plain, count);
                    else plugin.getLogger().severe("Shop sell payout failed and " + player.getName() + " is offline; " + count + " " + type + " were not returned");
                    finish(player, false, "정산 중 오류가 나서 아이템을 돌려드렸어요.", done);
                    return;
                }
                finish(player, true, product.name() + " " + count + "개를 팔았어요. (+" + product.currency().format(total) + " " + product.currency().label + ")", done);
            });
        }
    }

    private static double total(Currency currency, double raw) {
        return currency.whole() ? Math.round(raw) : Math.round(raw * 100) / 100.0;
    }

    private static boolean take(UUID id, Currency currency, double amount, CurrencyChangeContext context) {
        return switch (currency) {
            case SILVER -> CurrencyUtil.takeSilver(id, amount, context);
            case GOLD -> CurrencyUtil.takeGold(id, (long) amount, context);
            case KOKONUT -> CurrencyUtil.takeKokonut(id, (long) amount, context);
        };
    }

    private static void add(UUID id, Currency currency, double amount, CurrencyChangeContext context) {
        switch (currency) {
            case SILVER -> CurrencyUtil.addSilver(id, amount, context);
            case GOLD -> CurrencyUtil.addGold(id, (long) amount, context);
            case KOKONUT -> CurrencyUtil.addKokonut(id, (long) amount, context);
        }
    }

    private static void refund(CoreUi plugin, UUID id, Currency currency, double amount, ShopCatalog shop) {
        var context = new CurrencyChangeContext("shop:" + shop.id(), "상점 구매 환불 (지급 전 접속 종료)", id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                add(id, currency, amount, context);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, "Shop refund failed for " + id + ": " + currency + " " + amount, failure);
            }
        });
    }

    private interface Work<T> { T run(); }
    private interface Then<T> { void accept(T value, Throwable error); }

    private static <T> void async(CoreUi plugin, Work<T> work, Then<T> then) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            T value = null;
            Throwable error = null;
            try {
                value = work.run();
            } catch (Throwable failure) {
                error = failure;
                plugin.getLogger().log(Level.SEVERE, "Shop currency call failed", failure);
            }
            T v = value;
            Throwable e = error;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> then.accept(v, e));
        });
    }

    private static void finish(Player player, boolean ok, String message, Runnable done) {
        BUSY.remove(player.getUniqueId());
        if (!player.isOnline()) return;
        Messages.send(player, message);
        if (ok) player.playSound(player, Sound.ENTITY_VILLAGER_TRADE, SoundCategory.NEUTRAL, 0.7f, 1f);
        else player.playSound(player, Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.8f, 1f);
        done.run();
    }

    /** How many {@code plain} items still fit in the 36 storage slots. */
    static int space(PlayerInventory inventory, ItemStack plain) {
        int max = plain.getMaxStackSize(), free = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) free += max;
            else if (item.isSimilar(plain)) free += Math.max(0, max - item.getAmount());
        }
        return free;
    }

    /** How many plain (no custom name, enchantments, ...) items the storage slots hold. */
    public static int held(PlayerInventory inventory, ItemStack plain) {
        int count = 0;
        for (ItemStack item : inventory.getStorageContents()) if (item != null && item.isSimilar(plain)) count += item.getAmount();
        return count;
    }

    private static boolean removePlain(PlayerInventory inventory, ItemStack plain, int count) {
        if (held(inventory, plain) < count) return false;
        int slots = inventory.getStorageContents().length; // storage slots are inventory slots 0..35
        for (int slot = 0; slot < slots && count > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !item.isSimilar(plain)) continue;
            int take = Math.min(count, item.getAmount());
            count -= take;
            inventory.setItem(slot, take == item.getAmount() ? null : item.asQuantity(item.getAmount() - take));
        }
        return true;
    }

    /** Gives {@code count} items in full stacks; anything that no longer fits drops at the player's feet. */
    private static void give(Player player, ItemStack plain, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int left = count; left > 0; left -= plain.getMaxStackSize()) stacks.add(plain.asQuantity(Math.min(left, plain.getMaxStackSize())));
        for (ItemStack rest : player.getInventory().addItem(stacks.toArray(ItemStack[]::new)).values())
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
    }
}
