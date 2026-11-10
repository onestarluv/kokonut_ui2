package org.crebs.coreui;

import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.demo.DemoMenu;
import org.crebs.coreui.menu.style.ThemeColour;
import org.crebs.coreui.menu.element.DropdownElement;
import org.crebs.coreui.menu.listener.MenuListener;
import org.crebs.coreui.menu.render.MenuRenderer;
import org.crebs.coreui.menu.session.MenuSession;
import org.crebs.coreui.menu.session.MenuValues;
import java.util.*;
import org.crebs.coreui.glyph.GlyphManager;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Path;

public final class CoreUi extends JavaPlugin {
    private final Map<UUID, MenuSession> sessions = new HashMap<>();
    private final Map<UUID, ThemeColour> themes = new HashMap<>();
    private final Map<UUID, Map<String, MenuValues>> rememberedValues = new HashMap<>();
    private final MenuRenderer renderer = new MenuRenderer();
    private GlyphManager glyphs;
    private org.crebs.coreui.skin.SkinFaces skins;

    /** Players' faces from their skins, for menus that draw the face larger than the 8x8 head object. */
    public org.crebs.coreui.skin.SkinFaces getSkinFaces() {
        return Objects.requireNonNull(skins, "CoreUi is not enabled");
    }

    @Override public void onLoad() {
        try { LegacyPluginData.migrate(getDataFolder().toPath(), "CoreUi"); }
        catch (java.io.IOException failure) { throw new IllegalStateException("기존 CoreUi 설정·팩 데이터 이전 실패", failure); }
    }

    public GlyphManager getGlyphs() {
        return Objects.requireNonNull(glyphs, "CoreUi is not enabled");
    }

    public CompletableFuture<Path> reloadPack() {
        return getGlyphs().reload();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyTextConfig();
        boolean betterHud = hookBetterHud();
        glyphs = new GlyphManager(getDataFolder().toPath(), getFile().toPath(), betterHud);
        reloadPack().whenComplete((path, error) -> {
            if (error == null) getLogger().info("Resource pack generated: " + path);
            else getLogger().log(java.util.logging.Level.SEVERE, "Resource pack generation failed", error);
        });
        org.crebs.coreui.menu.shop.ShopRegistry.load(this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        skins = new org.crebs.coreui.skin.SkinFaces(this);
        getServer().getPluginManager().registerEvents(skins, this);
        for (Player online : getServer().getOnlinePlayers()) skins.face(online);
        if (getServer().getPluginManager().isPluginEnabled("Kokonut-Mail")) {
            try {
                org.crebs.coreui.menu.kokonut.MailBridge.registerView(this);
                getLogger().info("Kokonut-Mail mailbox now opens as a CoreUi dialog");
            } catch (LinkageError olderMailPlugin) {
                getLogger().warning("Kokonut-Mail is too old for the dialog mailbox; it keeps its chest GUI");
            }
        }
        if (getServer().getPluginManager().isPluginEnabled("Kokonut-Core")) {
            try {
                org.crebs.coreui.menu.kokonut.CoreViews.register(this);
                getLogger().info("Kokonut-Core /칭호 and Shift+F now open CoreUi screens (title screen, main menu)");
            } catch (LinkageError olderCore) {
                getLogger().warning("Kokonut-Core is too old for CoreUi title/main menu views; it keeps its chest GUIs");
            }
        }
        getLogger().info("CoreUI menus online");
        Objects.requireNonNull(getCommand("coreui")).setExecutor((sender, command, label, args) -> {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("coreui.reload")) {
                    Messages.send(sender, "You do not have permission to reload CoreUi");
                    return true;
                }
                Messages.send(sender, "Rebuilding CoreUi resource pack...");
                applyTextConfig();
                org.crebs.coreui.menu.shop.ShopRegistry.load(this);
                reloadPack().whenComplete((path, error) -> {
                    if (!isEnabled()) return;
                    getServer().getScheduler().runTask(this, () -> {
                        if (error == null) Messages.send(sender, "Loaded " + glyphs.all().size() + " glyphs. Pack: " + path);
                        else {
                            Messages.send(sender, "Pack rebuild failed. Check the console for details");
                            getLogger().log(java.util.logging.Level.SEVERE, "Resource pack generation failed", error);
                        }
                    });
                });
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("quest")) {
                // /coreui quest                     : quest journal (prototype, coreui.demo)
                // /coreui quest npc <giver> [player]: NPC quest offer screen, e.g. from a Citizens console command
                boolean npc = args.length >= 3 && args.length <= 4 && args[1].equalsIgnoreCase("npc");
                if (args.length != 1 && !npc) {
                    Messages.send(sender, "Usage: /coreui quest | /coreui quest npc <" + String.join("|", org.crebs.coreui.menu.quest.QuestOfferMenu.givers()) + "> [player]");
                    return true;
                }
                Player target;
                if (npc && args.length == 4) {
                    if (!sender.hasPermission("coreui.quest.others")) {
                        Messages.send(sender, "You do not have permission to open quest screens for other players");
                        return true;
                    }
                    target = getServer().getPlayerExact(args[3]);
                    if (target == null) {
                        Messages.send(sender, "플레이어 '" + args[3] + "'이(가) 접속해 있지 않아요.");
                        return true;
                    }
                } else if (sender instanceof Player self) {
                    if (!sender.hasPermission("coreui.demo")) {
                        Messages.send(sender, "You do not have permission to open the quest prototype");
                        return true;
                    }
                    target = self;
                } else {
                    Messages.send(sender, "Usage from the console: /coreui quest npc <giver> <player>");
                    return true;
                }
                if (npc && !org.crebs.coreui.menu.quest.QuestOfferMenu.givers().contains(args[2])) {
                    Messages.send(sender, "NPC '" + args[2] + "'이(가) 없어요: " + String.join(", ", org.crebs.coreui.menu.quest.QuestOfferMenu.givers()));
                    return true;
                }
                boolean opened = npc ? org.crebs.coreui.menu.quest.QuestOfferMenu.open(this, target, args[2])
                        : org.crebs.coreui.menu.quest.QuestMenu.open(this, target);
                if (!opened) Messages.send(sender, "퀘스트 이미지를 아직 불러오지 못했어요. 잠시 후 다시 시도하거나 /coreui reload 하세요.");
                return true;
            }
            if (args.length >= 1 && args.length <= 3 && args[0].equalsIgnoreCase("shop")) {
                // /coreui shop <id>            : open a shop for yourself (coreui.shop)
                // /coreui shop <id> <player>   : open it for someone else, e.g. a Citizens NPC console command (coreui.shop.others)
                if (args.length == 1) {
                    Messages.send(sender, "Usage: /coreui shop <id> [player] · shops: " + String.join(", ", org.crebs.coreui.menu.shop.ShopRegistry.ids()));
                    return true;
                }
                var shop = org.crebs.coreui.menu.shop.ShopRegistry.get(args[1]).orElse(null);
                if (shop == null) {
                    Messages.send(sender, "상점 '" + args[1] + "'이(가) shops.yml에 없어요.");
                    return true;
                }
                Player target;
                if (args.length == 3) {
                    if (!sender.hasPermission("coreui.shop.others")) {
                        Messages.send(sender, "You do not have permission to open shops for other players");
                        return true;
                    }
                    target = getServer().getPlayerExact(args[2]);
                    if (target == null) {
                        Messages.send(sender, "플레이어 '" + args[2] + "'이(가) 접속해 있지 않아요.");
                        return true;
                    }
                } else if (sender instanceof Player self) {
                    if (!sender.hasPermission("coreui.shop")) {
                        Messages.send(sender, "You do not have permission to open shops");
                        return true;
                    }
                    target = self;
                } else {
                    Messages.send(sender, "Usage from the console: /coreui shop <id> <player>");
                    return true;
                }
                if (!org.crebs.coreui.menu.shop.ShopMenu.open(this, target, shop)) {
                    Messages.send(sender, "상점 이미지를 아직 불러오지 못했어요. 잠시 후 다시 시도하거나 /coreui reload 하세요.");
                }
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("demo")) {
                if (!sender.hasPermission("coreui.demo")) {
                    Messages.send(sender, "You do not have permission to open the CoreUi demo");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "Open the demo in-game: /coreui demo [light|dark]");
                    return true;
                }
                if (args.length > 2 || args.length == 2
                        && !args[1].equalsIgnoreCase("light") && !args[1].equalsIgnoreCase("dark")) {
                    Messages.send(sender, "Usage: /coreui demo [light|dark]");
                    return true;
                }
                var example = glyphs.all().get("example-arrow");
                if (example == null) {
                    Messages.send(sender, "Demo resources are not ready. Wait for pack generation or run /coreui reload");
                    return true;
                }
                ThemeColour theme = args.length == 2 && args[1].equalsIgnoreCase("light")
                        ? ThemeColour.LIGHT : ThemeColour.DARK;
                open(player, DemoMenu.create(example, theme, this::close, this::setTheme));
                Messages.send(sender, "CoreUi demo opened. Apply plugins/CoreUi/Pack/CoreUi.zip to your client resource packs");
                return true;
            }
            Messages.send(sender, "Usage: /coreui demo [light|dark] | /coreui quest [npc <giver> [player]] | /coreui shop <id> [player] | /coreui reload");
            return true;
        });
        Objects.requireNonNull(getCommand("coreui")).setTabCompleter((sender, command, label, args) -> {
            if (args.length == 1) {
                List<String> suggestions = new ArrayList<>();
                if (sender.hasPermission("coreui.demo")) { suggestions.add("demo"); suggestions.add("quest"); }
                if (sender.hasPermission("coreui.shop") || sender.hasPermission("coreui.shop.others")) suggestions.add("shop");
                if (sender.hasPermission("coreui.reload")) suggestions.add("reload");
                return suggestions.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("quest")) {
                return List.of("npc").stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("quest") && args[1].equalsIgnoreCase("npc")) {
                return org.crebs.coreui.menu.quest.QuestOfferMenu.givers().stream()
                        .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("shop") && sender.hasPermission("coreui.shop")) {
                return org.crebs.coreui.menu.shop.ShopRegistry.ids().stream()
                        .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("demo") && sender.hasPermission("coreui.demo")) {
                return List.of("light", "dark").stream()
                        .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            return List.of();
        });
        Objects.requireNonNull(getCommand("메인메뉴")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, "플레이어만 사용할 수 있습니다.");
                return true;
            }
            if (!org.crebs.coreui.menu.kokonut.MainMenu.open(this, player)) {
                Messages.send(sender, "메뉴 이미지를 아직 불러오지 못했어요. 잠시 후 다시 시도하거나 /coreui reload 하세요.");
            }
            return true;
        });
        Objects.requireNonNull(getCommand("도감")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                Messages.send(sender, "플레이어만 사용할 수 있습니다.");
                return true;
            }
            if (!org.crebs.coreui.menu.dex.ItemDexMenu.open(this, player)) {
                Messages.send(sender, "도감 이미지를 아직 불러오지 못했어요. 잠시 후 다시 시도하거나 /coreui reload 하세요.");
            }
            return true;
        });
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (UUID id : new ArrayList<>(sessions.keySet())) {
                MenuSession session = sessions.get(id);
                if (session != null && session.expired()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        close(player);
                    } else {
                        remember(id);
                        sessions.remove(id);
                    }
                }
            }
        }, 200L, 200L);
    }

    /** Reads which ASCII characters use the vanilla label font. */
    private void applyTextConfig() {
        reloadConfig();
        String chars = getConfig().getString("text.vanilla-chars", org.crebs.coreui.menu.render.TextWidths.DEFAULT_VANILLA_CHARS);
        try {
            org.crebs.coreui.menu.render.TextWidths.configure(chars);
        } catch (IllegalArgumentException exception) {
            getLogger().warning(exception.getMessage() + " - using the default character set");
            org.crebs.coreui.menu.render.TextWidths.configure(org.crebs.coreui.menu.render.TextWidths.DEFAULT_VANILLA_CHARS);
        }
    }

    /** Injects CoreUi's GUI text-shader logic into BetterHud's generated shader when BetterHud is installed. */
    private boolean hookBetterHud() {
        for (String name : List.of("Kokonut_HUD", "BetterHud")) {
            if (!getServer().getPluginManager().isPluginEnabled(name)) continue;
            try {
                org.crebs.coreui.pack.BetterHudShaderHook.register();
                getLogger().info("Injected CoreUi shaders into " + name + "; the 26.3 text shader is left out of CoreUi.zip");
                return true;
            } catch (LinkageError | RuntimeException exception) {
                getLogger().log(java.util.logging.Level.WARNING, "Could not hook " + name + " shaders", exception);
            }
        }
        return false;
    }

    public void open(Player player, CoreMenu menu) {
        org.crebs.coreui.menu.kokonut.MailRequests.invalidate(player);
        requireMainThread();
        remember(player.getUniqueId());
        MenuValues saved = menu.remembersValues() ? rememberedValues.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(menu.getId(), MenuValues.EMPTY) : MenuValues.EMPTY;
        MenuSession session = new MenuSession(menu, saved);
        sessions.put(player.getUniqueId(), session);
        draw(player, session);
    }

    public Optional<MenuSession> getSession(Player player) {
        requireMainThread();
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    public void refresh(Player player) {
        requireMainThread();
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.touch();
        draw(player, session);
    }

    private void draw(Player player, MenuSession session) {
        player.showDialog(renderer.render(session, player.getWorld().getGameTime()));
    }

    public void navigate(Player player, String page) {
        requireMainThread();
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.navigate(page);
            refresh(player);
        }
    }

    public void focus(Player player, String elementId) {
        requireMainThread();
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.focus(elementId);
            refresh(player);
        }
    }

    public ThemeColour getTheme(Player player) {
        return themes.getOrDefault(player.getUniqueId(), ThemeColour.LIGHT);
    }

    public void setTheme(Player player, ThemeColour theme) {
        requireMainThread();
        themes.put(player.getUniqueId(), Objects.requireNonNull(theme));
        MenuSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.setTheme(theme);
        }
    }

    public void close(Player player) {
        org.crebs.coreui.menu.kokonut.MailRequests.invalidate(player);
        requireMainThread();
        removeSession(player);
        player.closeDialog();
    }

    public void removeSession(Player player) {
        org.crebs.coreui.menu.kokonut.MailRequests.invalidate(player);
        requireMainThread();
        remember(player.getUniqueId());
        sessions.remove(player.getUniqueId());
    }

    private void remember(UUID playerId) {
        MenuSession session = sessions.get(playerId);
        if (session != null && session.getMenu().remembersValues()) {
            rememberedValues.computeIfAbsent(playerId, ignored -> new HashMap<>())
                    .put(session.getMenu().getId(), session.snapshot());
        }
    }

    public void handleClick(Player player, Key key) {
        requireMainThread();
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.expired() || key.equals(MenuRenderer.CLOSE)) {
            close(player);
            return;
        }
        if (!session.getPressedId().isEmpty()) {
            return;
        }
        session.renderTick(player.getWorld().getGameTime());
        MenuSession.Interaction interaction = session.consumeClick(key);
        if (interaction == null) {
            return;
        }
        session.press(interaction.elementId(), interaction.part());
        if (session.getMenu().getElements().stream().anyMatch(element ->
                element.id().equals(interaction.elementId()) && (element instanceof DropdownElement || element instanceof org.crebs.coreui.menu.element.HotspotElement))) {
            release(player, session, interaction);
            return;
        }
        refresh(player);
        getServer().getScheduler().runTaskLater(this, () -> release(player, session, interaction), 2L);
    }

    private void release(Player player, MenuSession session, MenuSession.Interaction interaction) {
        if (sessions.get(player.getUniqueId()) != session || !player.isOnline()) {
            return;
        }
        if (!session.getPressedId().equals(interaction.elementId())) {
            return;
        }
        session.release();
        try {
            session.renderTick(player.getWorld().getGameTime());
            // Every menu control (buttons, tabs, hotspots) clicks audibly, only for the player who pressed it.
            player.playSound(player, org.bukkit.Sound.UI_BUTTON_CLICK, org.bukkit.SoundCategory.MASTER, 0.4f, 1f);
            interaction.callback().accept(player);
            if (sessions.get(player.getUniqueId()) == session && player.isOnline()) {
                refresh(player);
            }
        } catch (RuntimeException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Menu click failed: " + interaction.elementId(), exception);
            if (sessions.get(player.getUniqueId()) == session) {
                close(player);
            }
        }
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("CoreUI requires the server thread");
        }
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                close(player);
            }
        }
        sessions.clear();
        rememberedValues.clear();
        themes.clear();
    }
}
