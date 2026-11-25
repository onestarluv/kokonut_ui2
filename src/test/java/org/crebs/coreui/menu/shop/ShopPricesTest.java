package org.crebs.coreui.menu.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.crebs.coreui.menu.shop.ShopCatalog.Goods;
import org.crebs.coreui.menu.shop.ShopCatalog.Product;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The bundled shops.yml price sheet (경제 기획서) and the daily sell tiers. */
class ShopPricesTest {
    private static YamlConfiguration bundled() {
        var in = ShopPricesTest.class.getResourceAsStream("/shops.yml");
        assertNotNull(in, "shops.yml on the test classpath");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static Product product(ShopCatalog shop, String key) {
        return shop.products().stream().filter(p -> p.goods().key().equals(key)).findFirst().orElseThrow(() -> new AssertionError(key));
    }

    @Test
    void bundledSheetLoadsWithoutWarnings() {
        List<String> warnings = new ArrayList<>();
        Map<String, ShopCatalog> shops = ShopCatalog.loadAll(bundled(), warnings::add, item -> true);
        assertEquals(List.of(), warnings);
        assertEquals(List.of("general", "farm", "ranch", "mine", "fish"), List.copyOf(shops.keySet()));
        assertTrue(shops.get("farm").tiered());
        assertTrue(shops.get("ranch").tiered());
        assertFalse(shops.get("mine").tiered());
        assertFalse(shops.get("fish").tiered());
    }

    @Test
    void pricesFollowThePlan() {
        Map<String, ShopCatalog> shops = ShopCatalog.loadAll(bundled(), w -> {}, item -> true);
        ShopCatalog farm = shops.get("farm"), ranch = shops.get("ranch"), mine = shops.get("mine"), fish = shops.get("fish");
        assertEquals(3, product(farm, "wheat").price());
        assertEquals(1.8, product(farm, "carrot").price());
        assertEquals(Goods.Kind.NEXO, product(farm, "onion").goods().kind());
        assertEquals(4, product(ranch, "beef").price());
        assertEquals(16, ranch.products().stream().filter(p -> p.goods().key().endsWith("_wool")).count());
        assertEquals(10, product(mine, "raw_iron").price());
        // Blocks are exactly nine times their material (기획서 5.2).
        String[][] blocks = {{"coal", "coal_block"}, {"raw_iron", "raw_iron_block"}, {"raw_copper", "raw_copper_block"},
                {"raw_gold", "raw_gold_block"}, {"redstone", "redstone_block"}, {"lapis_lazuli", "lapis_block"}, {"diamond", "diamond_block"}};
        for (String[] b : blocks) assertEquals(product(mine, b[0]).price() * 9, product(mine, b[1]).price(), 1e-9, b[1]);
        assertEquals(ShopCatalog.Currency.GOLD, product(fish, "mystic").currency());
        assertFalse(fish.tieredFor(product(fish, "common")));
        assertTrue(farm.tieredFor(product(farm, "wheat")));
    }

    @Test
    void buyShopsRejectNonVanillaGoods() {
        var yaml = new YamlConfiguration();
        yaml.set("shops.x.mode", "buy");
        yaml.set("shops.x.products", List.of(Map.of("nexo", "onion", "price", 1, "currency", "silver"),
                Map.of("fish", "huge", "price", 1, "currency", "silver")));
        List<String> warnings = new ArrayList<>();
        assertTrue(ShopCatalog.loadAll(yaml, warnings::add, i -> true).get("x").products().isEmpty());
        assertEquals(2, warnings.size());
    }

    @Test
    void tiersSplitASaleAcrossBrackets() {
        SellTiers t = SellTiers.DEFAULT;
        assertEquals(300, t.payout(0, 300));
        assertEquals(100 + 50, t.payout(3100, 200));           // 100 at 100 %, 100 at 50 %
        assertEquals(1600 + 10, t.payout(6400 - 3200, 3300));  // 3,200 at 50 %, 100 at 10 %
        assertEquals(4800, t.payout(0, 6400));                 // the full-rate cap from 기획서 8절
        assertEquals(1, t.payout(10000, 10));
        assertEquals(1, t.index(3200));
        assertEquals(2, t.index(7000));
    }

    @Test
    void tiersLoadFromTheSheet() {
        SellTiers t = SellTiers.load(bundled(), w -> fail(w));
        assertEquals(SellTiers.DEFAULT, t);
    }
}
