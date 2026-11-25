package org.crebs.coreui.menu.shop;

import org.bukkit.configuration.ConfigurationSection;

import java.text.DecimalFormat;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;

/**
 * Daily sell tiers (경제 기획서 8절): what a player sells in {@code daily-tier} shops is added up per player per day
 * at the base price ("하루 누적 판매액"), and each part of a sale is paid at the rate of the bracket it falls into.
 * The default 0~3,200 = 100 %, 3,200~6,400 = 50 %, above = 10 % caps the full-rate take at 4,800 silver a day.
 * The day turns over at midnight in {@code zone}. Read from {@code sell-tiers:} in {@code shops.yml}.
 */
public record SellTiers(ZoneId zone, List<Tier> tiers) {
    /** Sales up to {@code until} (base silver, cumulative for the day) pay {@code rate}; the last tier has no end. */
    public record Tier(double until, double rate) {}

    public static final SellTiers DEFAULT = new SellTiers(ZoneId.of("Asia/Seoul"),
            List.of(new Tier(3200, 1.0), new Tier(6400, 0.5), new Tier(Double.POSITIVE_INFINITY, 0.1)));

    public SellTiers {
        Objects.requireNonNull(zone, "zone");
        tiers = List.copyOf(tiers);
        if (tiers.isEmpty() || tiers.getLast().until() != Double.POSITIVE_INFINITY)
            throw new IllegalArgumentException("the last tier must have no upper bound");
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /** What selling {@code gross} (base price) pays when {@code before} was already sold today; rounded to 0.01. */
    public double payout(double before, double gross) {
        double paid = 0, from = Math.max(0, before), to = from + Math.max(0, gross), low = 0;
        for (Tier t : tiers) {
            double part = Math.min(to, t.until()) - Math.max(from, low);
            if (part > 0) paid += part * t.rate();
            low = t.until();
            if (low >= to) break;
        }
        return Math.round(paid * 100) / 100.0;
    }

    /** The tier the next silver sold falls into. */
    public int index(double sold) {
        for (int i = 0; i < tiers.size(); i++) if (sold < tiers.get(i).until()) return i;
        return tiers.size() - 1;
    }

    public Tier at(double sold) {
        return tiers.get(index(sold));
    }

    /** Hover text listing the brackets, e.g. "0 ~ 3,200 실버: 100%". */
    public String describe() {
        DecimalFormat n = new DecimalFormat("#,##0");
        StringBuilder text = new StringBuilder("하루 판매 단가 구간 (매일 0시 초기화)");
        double low = 0;
        for (Tier t : tiers) {
            text.append('\n').append(n.format(low)).append(t.until() == Double.POSITIVE_INFINITY ? " 실버 초과" : " ~ " + n.format(t.until()) + " 실버")
                    .append(": ").append(Math.round(t.rate() * 100)).append('%');
            low = t.until();
        }
        return text.toString();
    }

    /**
     * {@code sell-tiers: {zone: Asia/Seoul, tiers: [{until: 3200, rate: 1.0}, {until: 6400, rate: 0.5}, {rate: 0.1}]}}.
     * Missing or broken settings fall back to {@link #DEFAULT} with a warning.
     */
    public static SellTiers load(ConfigurationSection root, Consumer<String> warn) {
        ConfigurationSection s = root == null ? null : root.getConfigurationSection("sell-tiers");
        if (s == null) return DEFAULT;
        ZoneId zone;
        try {
            zone = ZoneId.of(s.getString("zone", "Asia/Seoul"));
        } catch (DateTimeException bad) {
            warn.accept("sell-tiers.zone is not a time zone; using Asia/Seoul");
            zone = DEFAULT.zone();
        }
        List<Tier> tiers = new ArrayList<>();
        double last = 0;
        List<Map<?, ?>> raw = s.getMapList("tiers");
        for (int i = 0; i < raw.size(); i++) {
            Map<?, ?> t = raw.get(i);
            double rate = t.get("rate") instanceof Number r ? r.doubleValue() : -1;
            boolean open = !(t.get("until") instanceof Number);
            double until = open ? Double.POSITIVE_INFINITY : ((Number) t.get("until")).doubleValue();
            if (rate < 0 || rate > 1 || until <= last || (open && i != raw.size() - 1)) {
                warn.accept("sell-tiers.tiers[" + i + "]: needs rate 0~1 and increasing until (only the last tier has no until); using defaults");
                return new SellTiers(zone, DEFAULT.tiers());
            }
            tiers.add(new Tier(until, rate));
            last = until;
        }
        if (tiers.isEmpty() || tiers.getLast().until() != Double.POSITIVE_INFINITY) {
            warn.accept("sell-tiers.tiers must end with a tier without until; using defaults");
            return new SellTiers(zone, DEFAULT.tiers());
        }
        return new SellTiers(zone, tiers);
    }
}
