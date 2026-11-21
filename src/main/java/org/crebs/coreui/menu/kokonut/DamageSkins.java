package org.crebs.coreui.menu.kokonut;

import kr.toxicity.damage.api.BetterDamage;
import kr.toxicity.damage.api.skin.DamageSkin;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Kokonut_Damage's public API ({@code kr.toxicity.damage.api}) behind {@link DamageSkinMenu.Catalog}. Ownership is the
 * {@code betterdamage.skin.<id>} permission and the selection lives in Kokonut_Damage's player data (saved by it on
 * quit), so every call is in-memory and safe on the main thread. Only load this class when kokonut_damage is enabled.
 */
final class DamageSkins implements DamageSkinMenu.Catalog {
    static final DamageSkins INSTANCE = new DamageSkins();

    private DamageSkins() {
    }

    private static kr.toxicity.damage.api.player.DamagePlayerData data(Player player) {
        var damagePlayer = BetterDamage.inst().playerManager().player(player.getUniqueId());
        return damagePlayer == null ? null : damagePlayer.data();
    }

    private static DamageSkinMenu.Skin skin(DamageSkin skin) {
        return new DamageSkinMenu.Skin(skin.name(), skin.displayName());
    }

    @Override
    public boolean ready(Player player) {
        return data(player) != null;
    }

    @Override
    public List<DamageSkinMenu.Skin> owned(Player player) {
        var data = data(player);
        if (data == null) return List.of();
        return data.skins().stream().map(DamageSkins::skin)
                .sorted(Comparator.comparing(DamageSkinMenu.Skin::name)).toList();
    }

    @Override
    public int total() {
        return BetterDamage.inst().damageSkinManager().allNames().size();
    }

    @Override
    public String equipped(Player player) {
        var data = data(player);
        DamageSkin selected = data == null ? null : data.selectedSkin();
        return selected == null ? null : selected.name();
    }

    @Override
    public boolean equip(Player player, String id) {
        var data = data(player);
        DamageSkin skin = BetterDamage.inst().damageSkinManager().skin(Objects.requireNonNull(id));
        return data != null && skin != null && data.select(skin);
    }

    @Override
    public void unequip(Player player) {
        var data = data(player);
        if (data != null) data.clearSelection();
    }
}
