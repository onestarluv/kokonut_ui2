import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.crebs.coreui.glyph.Glyph;
import org.crebs.coreui.menu.CoreMenu;
import org.crebs.coreui.menu.element.GlyphElement;
import org.crebs.coreui.menu.element.HotspotElement;
import org.crebs.coreui.menu.element.LabelElement;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Offline preview of ShopMenu and QuestMenu: builds real frames with ShopMenu.build (the "general" and "farm" shops of shops.yml, sample wallet and inventory) and paints the
 * resulting elements to PNG. Bundled glyphs come from src/main/resources/bundled-glyphs, vanilla item icons from the
 * client jar, text from galmuri11.ttf (approximate: the client's TTF rasterizer and line offsets differ slightly).
 * Hotspots are outlined in faint red when the last argument is "hotspots".
 *
 * Build first (gradlew build), then from Kokonut_UI:
 * java -cp "build/classes/java/main;build/resources/main;<compile classpath>" tools/MenuPreview.java
 *      "%APPDATA%/.minecraft/versions/26.3/26.3.jar" docs/shop-preview [hotspots]
 */
public class MenuPreview {
    static final int SCALE = 3;
    static final Path RES = Path.of("src/main/resources");
    static final Map<String, String> VANILLA = new HashMap<>();
    static final Map<String, Integer> HEIGHTS = new HashMap<>(), VANILLA_HEIGHT = new HashMap<>();
    static ZipFile client;
    static Font font;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        client = new ZipFile(args[0]);
        String out = args[1];
        boolean hotspots = args.length > 2 && args[2].equals("hotspots");
        readYaml();
        try (InputStream in = Files.newInputStream(RES.resolve("resource-pack/assets/coreui/font/galmuri11.ttf"))) {
            font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(8f * SCALE);
        }
        Map<String, Glyph> glyphs = new HashMap<>() {
            @Override public Glyph get(Object key) { return glyph((String) key); }
            @Override public boolean containsKey(Object key) { return glyph((String) key) != null; }
        };
        if (List.of(args).contains("gem")) {  // only the gem cutting screens: gem-preview-*.png
            gem(glyphs, out.replace("shop-preview", "gem-preview"), hotspots);
            return;
        }
        if (List.of(args).contains("skill")) {  // only the skill screens: skill-preview-*.png
            skill(glyphs, out.replace("shop-preview", "skill-preview"), hotspots);
            return;
        }

        Class<?> menu = Class.forName("org.crebs.coreui.menu.shop.ShopMenu");
        Class<?> catalog = Class.forName("org.crebs.coreui.menu.shop.ShopCatalog");
        Class<?> state = Class.forName("org.crebs.coreui.menu.shop.ShopMenu$State");
        Class<?> wallet = Class.forName("org.crebs.coreui.menu.shop.ShopCatalog$Wallet");
        Class<?> actions = Class.forName("org.crebs.coreui.menu.shop.ShopMenu$Actions");
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(RES.resolve("shops.yml").toFile());
        Map<String, ?> shops = (Map<String, ?>) catalog.getMethod("loadAll", org.bukkit.configuration.ConfigurationSection.class,
                java.util.function.Consumer.class, java.util.function.Predicate.class).invoke(null, yaml,
                (java.util.function.Consumer<String>) System.out::println,
                (java.util.function.Predicate<String>) key -> VANILLA.containsKey("item/" + key));
        Object buyShop = shops.get("general"), sellShop = shops.get("farm");
        Constructor<?> newState = state.getDeclaredConstructor(String.class, int.class, String.class, int.class);
        newState.setAccessible(true);
        Object purse = wallet.getConstructor(double.class, long.class, long.class).newInstance(1234.5, 18L, 7L);
        Object noop = Proxy.newProxyInstance(menu.getClassLoader(), new Class<?>[]{actions}, (p, m, a) -> null);
        Method build = menu.getDeclaredMethod("build", Map.class, catalog, state, wallet, Map.class, actions);
        build.setAccessible(true);
        Map<String, Integer> held = Map.of("wheat#0", 70, "carrot#1", 9, "potato#2", 33, "cod#8", 8, "raw_iron#10", 40);

        Object[][] frames = {
                {"buy", buyShop, newState.newInstance("", 0, "lantern#5", 3), purse, Map.of()},
                {"buy-search", buyShop, newState.newInstance("ㅂㄷ", 0, null, 1), purse, Map.of()},
                {"buy-short", buyShop, newState.newInstance("", 2, "elytra#15", 1), purse, Map.of()},
                {"sell", sellShop, newState.newInstance("", 0, "wheat#0", 4), purse, held},
                {"sell-loading", sellShop, newState.newInstance("", 0, "carrot#1", 1), null, held},
        };
        for (Object[] f : frames) {
            CoreMenu m = (CoreMenu) build.invoke(null, glyphs, f[1], f[2], f[3], f[4], noop);
            String file = out + "-" + f[0] + ".png";
            render(m, hotspots, new File(file));
            System.out.println("wrote " + file + " (" + m.getElements().size() + " elements)");
        }

        // Quest journal (QuestMenu) and NPC offer (QuestOfferMenu) with the sample catalog: quest-preview-*.png.
        Class<?> qCatalog = Class.forName("org.crebs.coreui.menu.quest.QuestCatalog");
        Class<?> qStatus = Class.forName("org.crebs.coreui.menu.quest.QuestCatalog$Status");
        Class<?> qView = Class.forName("org.crebs.coreui.menu.quest.QuestProgress$View");
        Method viewOf = Class.forName("org.crebs.coreui.menu.quest.QuestProgress").getDeclaredMethod("of", Map.class);
        viewOf.setAccessible(true);
        Object quests = qCatalog.getField("SAMPLE").get(null), progress = viewOf.invoke(null, Map.of());
        String questOut = out.replace("shop-preview", "quest-preview");

        Class<?> jMenu = Class.forName("org.crebs.coreui.menu.quest.QuestMenu");
        Class<?> jState = Class.forName("org.crebs.coreui.menu.quest.QuestMenu$State");
        Class<?> jActions = Class.forName("org.crebs.coreui.menu.quest.QuestMenu$Actions");
        Constructor<?> newJState = jState.getDeclaredConstructor(qStatus, int.class, String.class);
        newJState.setAccessible(true);
        Method jBuild = jMenu.getDeclaredMethod("build", Map.class, qCatalog, jState, qView, jActions);
        jBuild.setAccessible(true);
        Object jNoop = Proxy.newProxyInstance(jMenu.getClassLoader(), new Class<?>[]{jActions}, (p, m, a) -> null);
        Object active = Enum.valueOf((Class) qStatus, "ACTIVE"), done = Enum.valueOf((Class) qStatus, "DONE");
        Object[][] journal = {
                {"journal", newJState.newInstance(active, 0, "crystal")},
                {"journal-complete", newJState.newInstance(active, 0, "cellar")},
                {"journal-done", newJState.newInstance(done, 0, "first")},
        };
        for (Object[] f : journal) {
            CoreMenu m = (CoreMenu) jBuild.invoke(null, glyphs, quests, f[1], progress, jNoop);
            String file = questOut + "-" + f[0] + ".png";
            render(m, hotspots, new File(file));
            System.out.println("wrote " + file + " (" + m.getElements().size() + " elements)");
        }

        Class<?> oMenu = Class.forName("org.crebs.coreui.menu.quest.QuestOfferMenu");
        Class<?> oState = Class.forName("org.crebs.coreui.menu.quest.QuestOfferMenu$State");
        Class<?> oActions = Class.forName("org.crebs.coreui.menu.quest.QuestOfferMenu$Actions");
        Constructor<?> newOState = oState.getDeclaredConstructor(String.class, String.class);
        newOState.setAccessible(true);
        Method oBuild = oMenu.getDeclaredMethod("build", Map.class, qCatalog, oState, qView, oActions);
        oBuild.setAccessible(true);
        Object oNoop = Proxy.newProxyInstance(oMenu.getClassLoader(), new Class<?>[]{oActions}, (p, m, a) -> null);
        Object[][] offers = {
                {"npc-offer", newOState.newInstance("elen", "letter")},
                {"npc-report", newOState.newInstance("bori", "cellar")},
        };
        for (Object[] f : offers) {
            CoreMenu m = (CoreMenu) oBuild.invoke(null, glyphs, quests, f[1], progress, oNoop);
            String file = questOut + "-" + f[0] + ".png";
            render(m, hotspots, new File(file));
            System.out.println("wrote " + file + " (" + m.getElements().size() + " elements)");
        }
        village(glyphs, out.replace("shop-preview", "village-preview"), hotspots);
        colorNick(glyphs, out.replace("shop-preview", "colornick-preview"), hotspots);
        gem(glyphs, out.replace("shop-preview", "gem-preview"), hotspots);
    }

    /** Gem cutting (GemMenu) with a fixed roll sequence: gem-preview-{start,cutting,done}.png. */
    @SuppressWarnings("unchecked")
    static void gem(Map<String, Glyph> glyphs, String out, boolean hotspots) throws Exception {
        Class<?> menu = Class.forName("org.crebs.coreui.menu.gem.GemMenu");
        Class<?> cutting = Class.forName("org.crebs.coreui.menu.gem.GemCutting");
        Class<?> line = Class.forName("org.crebs.coreui.menu.gem.GemCutting$Line");
        Class<?> actions = Class.forName("org.crebs.coreui.menu.gem.GemMenu$Actions");
        Method build = menu.getDeclaredMethod("build", Map.class, cutting, actions);
        build.setAccessible(true);
        Method cut = cutting.getMethod("cut", line, java.util.random.RandomGenerator.class);
        Object noop = Proxy.newProxyInstance(menu.getClassLoader(), new Class<?>[]{actions}, (p, m, a) -> null);
        Object[] lines = line.getEnumConstants();
        java.util.random.RandomGenerator dice = new java.util.Random(7);
        Object start = cutting.getConstructor().newInstance();
        Object mid = cutting.getConstructor().newInstance();
        int[] order = {0, 0, 1, 2, 1, 0, 2, 1, 1, 0, 2, 2, 0, 1};
        for (int i : order) cut.invoke(mid, lines[i], dice);
        Object done = cutting.getConstructor().newInstance();
        for (int round = 0; round < 10; round++) for (Object l : lines) cut.invoke(done, l, dice);
        Object[][] frames = {{"start", start}, {"cutting", mid}, {"done", done}};
        for (Object[] f : frames) {
            CoreMenu m = (CoreMenu) build.invoke(null, glyphs, f[1], noop);
            render(m, hotspots, new File(out + "-" + f[0] + ".png"));
            System.out.println("wrote " + out + "-" + f[0] + ".png (" + m.getElements().size() + " elements)");
        }
    }

    /** Title screen with its 색 닉네임 tab and the tab's own screen: colornick-preview-*.png (needs the Kokonut-Core jar). */
    static void colorNick(Map<String, Glyph> glyphs, String out, boolean hotspots) throws Exception {
        Class<?> nick = Class.forName("kr.gomsv.kokonutCore.api.nickname.NickColor");
        Method byId = nick.getMethod("byId", String.class);
        Class<?> menu = Class.forName("org.crebs.coreui.menu.kokonut.ColorNickMenu");
        Class<?> view = Class.forName("org.crebs.coreui.menu.kokonut.ColorNickMenu$View");
        Class<?> actions = Class.forName("org.crebs.coreui.menu.kokonut.ColorNickMenu$Actions");
        Constructor<?> newView = view.getDeclaredConstructors()[0];
        newView.setAccessible(true);
        Method build = menu.getDeclaredMethod("build", Map.class, view, actions);
        build.setAccessible(true);
        Object noop = Proxy.newProxyInstance(menu.getClassLoader(), new Class<?>[]{actions}, (p, m, a) -> null);
        List<?> all = (List<?>) nick.getField("ALL").get(null);
        Object aqua = byId.invoke(null, "aqua"), gold = byId.invoke(null, "gold"), yellow = byId.invoke(null, "yellow"),
                hex = byId.invoke(null, "ff7200"), grad = byId.invoke(null, "gradient5");
        Object[][] frames = {
                {"list", newView.newInstance("핑퐁", null, null, true, false, all, gold, gold, 0)},
                {"page2", newView.newInstance("핑퐁", null, null, true, false, all, gold, aqua, 1)},
                {"hex", newView.newInstance("핑퐁", null, null, true, false, all, hex, hex, 2)},
                {"gradient", newView.newInstance("핑퐁", null, null, true, false, all, grad, grad, 4)},
                {"few", newView.newInstance("핑퐁", null, null, true, false, List.of(gold, aqua, yellow), null, yellow, 0)},
                {"empty", newView.newInstance("핑퐁", null, null, true, false, List.of(), null, null, 0)},
        };
        for (Object[] f : frames) {
            CoreMenu m = (CoreMenu) build.invoke(null, glyphs, f[1], noop);
            render(m, hotspots, new File(out + "-" + f[0] + ".png"));
            System.out.println("wrote " + out + "-" + f[0] + ".png (" + m.getElements().size() + " elements)");
        }

        Class<?> tMenu = Class.forName("org.crebs.coreui.menu.kokonut.TitleMenu");
        Class<?> tView = Class.forName("org.crebs.coreui.menu.kokonut.TitleMenu$View");
        Class<?> tActions = Class.forName("org.crebs.coreui.menu.kokonut.TitleMenu$Actions");
        Method titleOf = Class.forName("org.crebs.coreui.menu.kokonut.TitleMenu$Title").getDeclaredMethod("of", long.class, String.class, String.class);
        titleOf.setAccessible(true);
        Object first = titleOf.invoke(null, 1L, "<gold>코코넛 수집가", "코코넛을 100개 모았어요.");
        List<Object> titles = List.of(first, titleOf.invoke(null, 2L, "<aqua>첫 발걸음", "서버에 처음 접속했어요."));
        Constructor<?> newTView = tView.getDeclaredConstructors()[0];
        newTView.setAccessible(true);
        Method tBuild = tMenu.getDeclaredMethod("build", Map.class, tView, tActions);
        tBuild.setAccessible(true);
        Object tNoop = Proxy.newProxyInstance(tMenu.getClassLoader(), new Class<?>[]{tActions}, (p, m, a) -> null);
        CoreMenu t = (CoreMenu) tBuild.invoke(null, glyphs, newTView.newInstance("핑퐁", null, null, true, false, titles, titles, 1L, first, 0, "", true), tNoop);
        render(t, hotspots, new File(out + "-titles.png"));
        System.out.println("wrote " + out + "-titles.png (" + t.getElements().size() + " elements)");
    }


    /** Village screen (sample village) and the main menu with its 마을 tab: village-preview-*.png. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void village(Map<String, Glyph> glyphs, String out, boolean hotspots) throws Exception {
        Class<?> vMenu = Class.forName("org.crebs.coreui.menu.village.VillageMenu");
        Class<?> vStore = Class.forName("org.crebs.coreui.menu.village.VillageStore");
        Class<?> vOverview = Class.forName("org.crebs.coreui.menu.village.VillageStore$Overview");
        Class<?> vState = Class.forName("org.crebs.coreui.menu.village.VillageMenu$State");
        Class<?> vView = Class.forName("org.crebs.coreui.menu.village.VillageMenu$View");
        Class<?> vActions = Class.forName("org.crebs.coreui.menu.village.VillageMenu$Actions");
        Method sample = vStore.getDeclaredMethod("sample", boolean.class);
        sample.setAccessible(true);
        java.lang.reflect.Field ownerField = vStore.getDeclaredField("SAMPLE_OWNER");
        ownerField.setAccessible(true);
        java.util.UUID owner = (java.util.UUID) ownerField.get(null);
        Constructor<?> newState = vState.getDeclaredConstructor(vView, String.class, boolean.class, String.class, int.class);
        newState.setAccessible(true);
        Method build = vMenu.getDeclaredMethod("build", Map.class, vOverview, java.util.UUID.class, vState, vActions);
        build.setAccessible(true);
        Object noop = Proxy.newProxyInstance(vMenu.getClassLoader(), new Class<?>[]{vActions}, (p, m, a) -> null);
        Object[][] frames = {
                {"village", true, newState.newInstance(Enum.valueOf((Class) vView, "MAIN"), null, false, null, 0)},
                {"village-members", true, newState.newInstance(Enum.valueOf((Class) vView, "MEMBERS"), new java.util.UUID(0, 3).toString(), true, null, 0)},
                {"village-icon", true, newState.newInstance(Enum.valueOf((Class) vView, "ICON"), null, false, "cherry_sapling", 0)},
                {"village-none", false, newState.newInstance(Enum.valueOf((Class) vView, "NONE"), null, false, null, 0)},
        };
        for (Object[] f : frames) {
            CoreMenu vm = (CoreMenu) build.invoke(null, glyphs, sample.invoke(null, f[1]), owner, f[2], noop);
            render(vm, hotspots, new File(out + "-" + f[0] + ".png"));
            System.out.println("wrote " + out + "-" + f[0] + ".png (" + vm.getElements().size() + " elements)");
        }

        Class<?> main = Class.forName("org.crebs.coreui.menu.kokonut.MainMenu");
        Class<?> info = Class.forName("org.crebs.coreui.menu.kokonut.MainMenu$Info");
        Class<?> link = Class.forName("org.crebs.coreui.menu.kokonut.MainMenu$Link");
        Constructor<?> newLink = link.getDeclaredConstructor(String.class, String.class, String.class, String.class);
        newLink.setAccessible(true);
        List<Object> links = List.of(newLink.newInstance("wiki", "위키", "menu/icon/wiki", null),
                newLink.newInstance("minelist", "마인리스트", "menu/icon/minelist", null),
                newLink.newInstance("discord", "디스코드", "menu/icon/discord", null));
        Constructor<?> newInfo = info.getDeclaredConstructors()[0];
        newInfo.setAccessible(true);
        Object sampleInfo = newInfo.newInstance("핑퐁", net.kyori.adventure.text.Component.text("코코넛 수집가", net.kyori.adventure.text.format.TextColor.color(0xFFD86A)), "world", 3L, 0, 0, 0, null, null, links);
        Method mainBuild = main.getDeclaredMethod("build", Class.forName("org.crebs.coreui.CoreUi"), Map.class, info);
        mainBuild.setAccessible(true);
        CoreMenu m = (CoreMenu) mainBuild.invoke(null, null, glyphs, sampleInfo);
        String file = out + "-main-menu.png";
        render(m, hotspots, new File(file));
        System.out.println("wrote " + file + " (" + m.getElements().size() + " elements)");
    }

    /** Skill screens (sample 채광 skill): skill-preview-*.png. Run with a trailing "skill" argument. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void skill(Map<String, Glyph> glyphs, String out, boolean hotspots) throws Exception {
        Class<?> sMenu = Class.forName("org.crebs.coreui.menu.skill.SkillMenu");
        Class<?> sCatalog = Class.forName("org.crebs.coreui.menu.skill.SkillCatalog");
        Class<?> sSkill = Class.forName("org.crebs.coreui.menu.skill.SkillCatalog$Skill");
        Class<?> sProgress = Class.forName("org.crebs.coreui.menu.skill.SkillStore$Progress");
        Class<?> sState = Class.forName("org.crebs.coreui.menu.skill.SkillMenu$State");
        Class<?> sView = Class.forName("org.crebs.coreui.menu.skill.SkillMenu$View");
        Class<?> sActions = Class.forName("org.crebs.coreui.menu.skill.SkillMenu$Actions");
        var mining = sCatalog.getDeclaredField("MINING");
        mining.setAccessible(true);
        Object skill = mining.get(null);
        Constructor<?> newProgress = sProgress.getDeclaredConstructor(int.class, long.class, long.class, java.util.Set.class, java.util.Set.class);
        newProgress.setAccessible(true);
        Constructor<?> newState = sState.getDeclaredConstructor(sView, String.class, boolean.class);
        newState.setAccessible(true);
        Method build = sMenu.getDeclaredMethod("build", Map.class, sSkill, sProgress, sState, sActions);
        build.setAccessible(true);
        Object noop = Proxy.newProxyInstance(sMenu.getClassLoader(), new Class<?>[]{sActions}, (p, m, a) -> null);
        Object sample = newProgress.newInstance(46, 1840L, 2600L,
                java.util.Set.of("sense", "geology", "hawkeye", "arm", "focus", "whet"), java.util.Set.of(10, 20));
        Object grown = newProgress.newInstance(50, 0L, 0L, java.util.Set.of("sense", "geology", "resonance", "arm", "focus",
                "whet", "swift", "chain", "blessing"), java.util.Set.of(10, 20, 30));
        Object tree = Enum.valueOf((Class) sView, "TREE");
        Object[][] frames = {
                {"tree", sample, newState.newInstance(tree, "resonance", false)},
                {"tree-learned", sample, newState.newInstance(tree, "sense", false)},
                {"tree-locked", sample, newState.newInstance(tree, "frenzy", false)},
                {"tree-reset", grown, newState.newInstance(tree, "blessing", true)},
                {"reward", sample, newState.newInstance(Enum.valueOf((Class) sView, "REWARD"), "resonance", false)},
                {"info", sample, newState.newInstance(Enum.valueOf((Class) sView, "INFO"), "resonance", false)},
        };
        for (Object[] f : frames) {
            CoreMenu m = (CoreMenu) build.invoke(null, glyphs, skill, f[1], f[2], noop);
            render(m, hotspots, new File(out + "-" + f[0] + ".png"));
            System.out.println("wrote " + out + "-" + f[0] + ".png (" + m.getElements().size() + " elements)");
        }
    }

    /** {@code image} with every pixel's colour multiplied by {@code rgb}, as the client tints a glyph. */
    static BufferedImage tinted(BufferedImage image, int rgb) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int p = image.getRGB(x, y);
                int r = (p >> 16 & 255) * (rgb >> 16 & 255) / 255, gr = (p >> 8 & 255) * (rgb >> 8 & 255) / 255, b = (p & 255) * (rgb & 255) / 255;
                out.setRGB(x, y, p & 0xFF000000 | r << 16 | gr << 8 | b);
            }
        }
        return out;
    }

    static void readYaml() throws Exception {
        String key = null;
        for (String table : List.of("bundled-vanilla-items.yml", "bundled-vanilla-glyphs.yml")) {
            for (String line : Files.readAllLines(RES.resolve(table))) {
                if (line.startsWith("  ") && !line.startsWith("    ") && line.trim().endsWith(":")) key = line.trim().replace(":", "");
                else if (key != null && line.trim().startsWith("texture: ")) VANILLA.put(key, line.trim().substring(9));
                else if (key != null && line.trim().startsWith("height: ")) VANILLA_HEIGHT.put(key, Integer.parseInt(line.trim().substring(8)));
            }
        }
        key = null;
        for (String line : Files.readAllLines(RES.resolve("bundled-glyphs.yml"))) {
            if (line.startsWith("  ") && !line.startsWith("    ")) key = line.trim().replace(":", "");
            else if (key != null && line.trim().startsWith("height: ")) HEIGHTS.put(key, Integer.parseInt(line.trim().substring(8)));
        }
    }

    static Glyph glyph(String id) {
        if (id.startsWith("item/")) return VANILLA.containsKey(id) ? new Glyph(id, 16, 16, 8, 17, 1 << 2) : null;
        if (VANILLA.containsKey(id)) {
            int h = VANILLA_HEIGHT.getOrDefault(id, 16);
            return new Glyph(id, h, h, 8, h + 1);
        }
        Path png = RES.resolve("bundled-glyphs/" + id + ".png");
        if (!Files.exists(png)) return null;
        try {
            BufferedImage img = ImageIO.read(png.toFile());
            int h = HEIGHTS.getOrDefault(id, img.getHeight()), w = img.getWidth() * h / img.getHeight();
            return new Glyph(id, w, h, Math.min(8, h), w + 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static BufferedImage texture(String id) throws Exception {
        if (VANILLA.containsKey(id)) {
            String[] t = VANILLA.get(id).split(":", 2);
            var entry = client.getEntry("assets/" + t[0] + "/textures/" + t[1]);
            try (InputStream in = client.getInputStream(entry)) {
                BufferedImage img = ImageIO.read(in);
                return img.getSubimage(0, 0, img.getWidth(), img.getWidth()); // first frame of animated strips
            }
        }
        return ImageIO.read(RES.resolve("bundled-glyphs/" + id + ".png").toFile());
    }

    static void render(CoreMenu m, boolean hotspots, File file) throws Exception {
        BufferedImage img = new BufferedImage(m.getWidth() * SCALE, m.getHeight() * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x2B2B2B));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(font);
        List<HotspotElement> spots = new ArrayList<>();
        for (var e : m.getElements()) {
            var b = e.bounds();
            if (e instanceof GlyphElement ge) {
                Glyph gl = ge.glyph();
                BufferedImage texture = ge.tint() == null ? texture(gl.id()) : tinted(texture(gl.id()), ge.tint().value());
                g.drawImage(texture, b.x() * SCALE, b.y() * SCALE, gl.width() * SCALE, gl.height() * SCALE, null);
            } else if (e instanceof LabelElement le) {
                Component c = le.properties().label();
                String text = PlainTextComponentSerializer.plainText().serialize(c);
                var colour = c.color() == null ? 0x3B2A1A : c.color().value();
                g.setColor(new Color(colour));
                g.drawString(text, b.x() * SCALE, (b.y() + 7) * SCALE);
            } else if (e instanceof org.crebs.coreui.menu.element.PlayerHeadElement) {
                // The client draws the skin face; the preview shows a stand-in 8x8 square.
                g.setColor(new Color(0x8C6A4A));
                g.fillRect(b.x() * SCALE, b.y() * SCALE, 8 * SCALE, 8 * SCALE);
                g.setColor(new Color(0x3B2A1A));
                g.drawRect(b.x() * SCALE, b.y() * SCALE, 8 * SCALE - 1, 8 * SCALE - 1);
            } else if (e instanceof HotspotElement h) {
                spots.add(h);
            }
        }
        if (hotspots) {
            g.setColor(new Color(255, 0, 0, 110));
            for (var h : spots) {
                var b = h.bounds();
                g.drawRect(b.x() * SCALE, b.y() * SCALE, b.width() * SCALE - 1, b.height() * SCALE - 1);
            }
        }
        g.dispose();
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
    }

}
