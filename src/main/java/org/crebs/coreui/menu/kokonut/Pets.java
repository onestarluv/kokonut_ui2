package org.crebs.coreui.menu.kokonut;

import fr.nocsy.mcpets.api.MCPetsAPI;
import fr.nocsy.mcpets.api.PetInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.crebs.coreui.CoreUi;

import java.util.List;

/**
 * Kokonut_Pets' {@link MCPetsAPI} behind {@link PetMenu.Catalog}, and the hook that makes {@code /펫} and right-clicking
 * your own pet open {@link PetMenu} instead of the chest menus. Only load this class when Kokonut_Pets is enabled.
 */
public final class Pets implements PetMenu.Catalog, PetPickupMenu.Catalog {
    /** Plugin name of Kokonut_Pets (provides MCPets). */
    public static final String PLUGIN = "Kokonut_Pets";
    static final Pets INSTANCE = new Pets();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Pets() {
    }

    /** Kokonut_Pets falls back to its chest menus when this returns false (glyphs not loaded). */
    public static void register(CoreUi coreUi) {
        MCPetsAPI.setMenuOverride((player, select) -> PetMenu.open(coreUi, INSTANCE, player, select));
    }

    /** The pet screen (main menu 펫 button); false when it could not be opened. */
    public static boolean openScreen(Player player) {
        MCPetsAPI.openMenu(player);
        return true;
    }

        public static void unregister() {
        MCPetsAPI.setMenuOverride(null);
    }

    @Override
    public List<PetMenu.Pet> pets(Player player) {
        return MCPetsAPI.infos(player).stream().map(Pets::pet).toList();
    }

    private static PetMenu.Pet pet(PetInfo i) {
        PetInfo.Stats s = i.stats();
        PetMenu.Stats stats = s == null ? null : new PetMenu.Stats(text(s.levelName()),
                s.nextLevelName() == null ? null : text(s.nextLevelName()), s.health(), s.maxHealth(), s.regeneration(),
                s.damage(), s.resistance(), s.power(), s.experience(), s.levelStart(), s.levelEnd());
        return new PetMenu.Pet(i.id(), i.icon(), grade(i.rarity()), text(i.name()), i.nickname() == null ? null : text(i.nickname()),
                i.description().stream().map(Pets::text).toList(), i.active(), PetMenu.Status.valueOf(i.status().name()),
                i.timeLeft(), stats, i.pickup(), i.nameable(), i.skins(), i.inventory(), i.mountable(), i.signals());
    }

    /** Core item rarity label in its colour (커먼 … 미스틱, same as fish), or null. */
    private static Component grade(String name) {
        if (name == null) return null;
        try {
            var rarity = kr.gomsv.kokonutCore.api.item.ItemRarity.valueOf(name);
            return Component.text(rarity.label(), rarity.color());
        } catch (RuntimeException | LinkageError unknown) {
            return null;
        }
    }

    /** Kokonut_Pets strings carry legacy § codes; item-lore italics are dropped. */
    private static Component text(String legacy) {
        return LEGACY.deserialize(legacy == null ? "" : legacy).decoration(TextDecoration.ITALIC, false);
    }

    @Override public void summon(Player player, String id) {
        fr.nocsy.mcpets.data.Pet pet = MCPetsAPI.getObjectPet(id);
        if (pet != null) MCPetsAPI.summon(player, pet);
    }
    @Override public boolean revoke(Player player, String id) { return MCPetsAPI.revoke(player, id); }
    @Override public boolean setName(Player player, String id, String name) { return MCPetsAPI.setName(player, id, name); }
    @Override public int maxNameLength() { return MCPetsAPI.maxNameLength(); }
    @Override public boolean skins(Player player, String id) { return MCPetsAPI.skins(player, id); }
    @Override public boolean inventory(Player player, String id) { return MCPetsAPI.inventory(player, id); }
    @Override public boolean mount(Player player, String id) { return MCPetsAPI.mount(player, id); }
    @Override public boolean signalStick(Player player, String id) { return MCPetsAPI.signalStick(player, id); }

    // ---- auto pickup settings ----

    @Override
    public PetPickupMenu.View pickup(Player player, String petId, String petName, int radius) {
        var filter = MCPetsAPI.pickupFilter(player, petId);
        var entries = filter.entries().stream().map(e -> new PetPickupMenu.Entry(e.key(), e.icon(), itemName(e))).toList();
        return new PetPickupMenu.View(petId, petName, radius, PetPickupMenu.Mode.valueOf(filter.mode().name()), entries,
                fr.nocsy.mcpets.data.livingpets.PickupFilters.MAX_ENTRIES);
    }

    /** Nexo items keep their name; vanilla items use the client translation. */
    private static Component itemName(fr.nocsy.mcpets.data.livingpets.PickupFilters.Entry e) {
        if (!e.name().isEmpty()) return Component.text(e.name());
        String key = fr.nocsy.mcpets.data.livingpets.PickupFilters.translationKey(e.icon());
        return key == null ? Component.text(e.key()) : Component.translatable(key);
    }

    @Override
    public void mode(Player player, String petId, PetPickupMenu.Mode mode) {
        MCPetsAPI.setPickupMode(player, petId, fr.nocsy.mcpets.data.livingpets.PickupFilters.Mode.valueOf(mode.name()));
    }

    @Override
    public List<PetPickupMenu.Candidate> candidates(Player player, String petId, int limit) {
        return MCPetsAPI.pickupCandidates(player, petId, limit).stream().map(c -> new PetPickupMenu.Candidate(
                new PetPickupMenu.Entry(c.entry().key(), c.entry().icon(), itemName(c.entry())), c.listed())).toList();
    }

    @Override
    public String toggle(Player player, String petId, String key) {
        return switch (MCPetsAPI.togglePickupItem(player, petId, key)) {
            case ADDED, DUPLICATE -> null;
            case EMPTY_HAND -> "그 아이템이 더 이상 인벤토리에 없어요.";
            case FULL -> "목록이 가득 찼어요. (최대 " + fr.nocsy.mcpets.data.livingpets.PickupFilters.MAX_ENTRIES + "개)";
        };
    }

    @Override public void remove(Player player, String petId, String key) { MCPetsAPI.removePickupItem(player, petId, key); }
    @Override public void clear(Player player, String petId) { MCPetsAPI.clearPickupItems(player, petId); }
}
