package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutParticle.api.MoveEffectService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * kokonut_particle's {@link MoveEffectService} behind {@link AfterimageMenu.Catalog}. Every call is in-memory.
 * Only load this class when kokonut_particle is enabled.
 */
final class Afterimages implements AfterimageMenu.Catalog {
    static final Afterimages INSTANCE = new Afterimages();

    private Afterimages() {
    }

    private static MoveEffectService service() {
        return Bukkit.getServicesManager().load(MoveEffectService.class);
    }

    static boolean available() {
        return service() != null;
    }

    @Override
    public List<AfterimageMenu.Effect> effects(Player player) {
        MoveEffectService s = service();
        if (s == null) return List.of();
        return s.effects().stream()
                .map(e -> new AfterimageMenu.Effect(e.id(), e.name(), e.icon(), s.canUse(player, e.id())))
                .toList();
    }

    @Override
    public String selected(Player player) {
        MoveEffectService s = service();
        return s == null ? null : s.selected(player);
    }

    @Override
    public boolean select(Player player, String id) {
        MoveEffectService s = service();
        return s != null && s.select(player, id);
    }

    @Override
    public void clear(Player player) {
        MoveEffectService s = service();
        if (s != null) s.clear(player);
    }
}
