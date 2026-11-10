package org.crebs.coreui.menu.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Consumer;

/**
 * One shop for {@link ShopMenu}: a flat product list (no categories) and a {@link Mode}. A shop either only sells to
 * players ({@link Mode#BUY}, the player pays) or only buys from them ({@link Mode#SELL}, the player is paid). Every
 * product names its own currency. Shops are read from {@code plugins/Kokonut_UI/shops.yml} ({@link #loadAll}).
 */
public record ShopCatalog(String id, String name, Mode mode, List<Product> products) {
    /** Labels follow the player's point of view: in a BUY shop the player buys. */
    public enum Mode {
        BUY("구매 상점", "emerald", "구매", "구매하기"), SELL("판매 상점", "hopper", "판매", "판매하기");

        public final String title, icon, verb, button;

        Mode(String title, String icon, String verb, String button) {
            this.title = title;
            this.icon = icon;
            this.verb = verb;
            this.button = button;
        }
    }

    /** The three Kokonut currencies; {@code icon} is the main menu's 16 px currency glyph ({@code art/icons}). */
    public enum Currency {
        SILVER("실버", "menu/icon/silver"), GOLD("골드", "menu/icon/gold"), KOKONUT("코코넛", "menu/icon/kokonut");

        public final String label;
        public final String icon;

        Currency(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }

        /** Silver is fractional (Core stores a double, shown with up to 2 decimals); gold and kokonut are whole. */
        public boolean whole() {
            return this != SILVER;
        }

        public String format(double amount) {
            return new DecimalFormat(whole() ? "#,##0" : "#,##0.##").format(amount);
        }
    }

    /**
     * One product, always traded one item at a time: {@code price} is per item (paid by the player in a BUY shop,
     * paid to the player in a SELL shop) and the screen's quantity is the item count. {@code item} is a vanilla item
     * key (icon {@code item/<key>}); {@code description} lines are drawn as they are (two lines, under about 200 px).
     */
    public record Product(String id, String item, String name, double price, Currency currency, List<String> description) {
        public Product {
            Objects.requireNonNull(currency, "currency");
            description = List.copyOf(description);
        }

        public Material material() {
            return Material.matchMaterial(item);
        }
    }

    /** A snapshot of the player's balances (Core's non-blocking UI snapshot); never use it to authorize a payment. */
    public record Wallet(double silver, long gold, long kokonut) {
        public double of(Currency currency) {
            return switch (currency) {
                case SILVER -> silver;
                case GOLD -> gold;
                case KOKONUT -> kokonut;
            };
        }
    }

    public ShopCatalog {
        Objects.requireNonNull(mode, "mode");
        products = List.copyOf(products);
    }

    /**
     * Reads every shop under {@code shops:}. Invalid products (unknown item, bad price or amount, fractional gold or
     * kokonut price) are skipped and reported through {@code warn}, so one typo does not take a whole shop down.
     */
    public static Map<String, ShopCatalog> loadAll(ConfigurationSection root, Consumer<String> warn) {
        return loadAll(root, warn, item -> {
            Material material = Material.matchMaterial(item);
            return material != null && material.isItem() && !material.isAir();
        });
    }

    /** {@code validItem} decides which item keys exist (the offline preview has no server registry to ask). */
    public static Map<String, ShopCatalog> loadAll(ConfigurationSection root, Consumer<String> warn, java.util.function.Predicate<String> validItem) {
        Map<String, ShopCatalog> shops = new LinkedHashMap<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("shops");
        if (section == null) return shops;
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;
            Mode mode;
            try {
                mode = Mode.valueOf(s.getString("mode", "buy").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException bad) {
                warn.accept("shops." + id + ".mode must be buy or sell");
                continue;
            }
            List<Product> products = new ArrayList<>();
            List<Map<?, ?>> raw = s.getMapList("products");
            for (int i = 0; i < raw.size(); i++) {
                String where = "shops." + id + ".products[" + i + "]";
                Map<?, ?> p = raw.get(i);
                String item = String.valueOf(p.get("item")).toLowerCase(Locale.ROOT).replace("minecraft:", "");
                if (!validItem.test(item)) { warn.accept(where + ": unknown item " + item); continue; }
                double price = p.get("price") instanceof Number n ? n.doubleValue() : -1;
                Currency currency;
                try {
                    currency = Currency.valueOf(String.valueOf(p.get("currency")).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException bad) { warn.accept(where + ": currency must be silver, gold or kokonut"); continue; }
                if (!(price > 0) || !Double.isFinite(price)) { warn.accept(where + ": price must be positive"); continue; }
                if (currency.whole() && price != Math.rint(price)) { warn.accept(where + ": " + currency.label + " price must be whole"); continue; }
                if (!currency.whole() && Math.abs(price * 100 - Math.rint(price * 100)) > 1e-9) { warn.accept(where + ": silver price has at most 2 decimals"); continue; }
                Object name = p.get("name");
                List<String> description = p.get("description") instanceof List<?> lines
                        ? lines.stream().map(String::valueOf).toList()
                        : p.get("description") == null ? List.of() : List.of(String.valueOf(p.get("description")));
                if (p.containsKey("amount")) warn.accept(where + ": amount is ignored (products are sold one item at a time; price is per item)");
                products.add(new Product(item + "#" + i, item, name == null ? item : String.valueOf(name), price, currency, description));
            }
            shops.put(id, new ShopCatalog(id, s.getString("name", id), mode, products));
        }
        return shops;
    }
}
