package iq.mizan.networth.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import iq.mizan.domain.metal.MetalValuation;
import iq.mizan.domain.networth.NetWorth;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceQuote;
import iq.mizan.market.repository.PriceQuoteRepository;
import iq.mizan.networth.dto.NetWorthResponse;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The server's own reading of a household's ledgers (BR-05), used for snapshots. Mirrors the
 * client's valuation: latest user override beats the feed, the household's chosen dollar
 * rate converts, and the shared metal maths prices each open lot.
 */
@Service
@AllArgsConstructor
public class NetWorthCalculator {

    private static final long RATE_SCALE = 1_000_000L;
    private static final long CENTS_PER_DOLLAR = 100;
    private static final String BASE_CURRENCY = "IQD";
    private static final String USD = "USD";

    private final JdbcClient jdbc;
    private final PriceQuoteRepository quotes;
    private final Clock clock;

    public NetWorthResponse compute(UUID householdId) {
        Prices prices = prices(householdId);
        long cash = cash(householdId, prices);
        long metals = metals(householdId, prices);
        long[] debts = debts(householdId);
        NetWorth.Result result = NetWorth.compute(new NetWorth.Input(cash, metals, debts[0], debts[1]));
        return new NetWorthResponse(result.totalAssets(), result.totalLiabilities(), result.netWorth(),
                result.composition(), prices.rateSet(), clock.instant());
    }

    private record Prices(long usdIqdMicros, String rateKind, Map<Instrument, Long> spotMicros,
                          Map<Instrument, Long> overridePerGram24k, int goldPremiumBp, int silverPremiumBp,
                          Map<String, Object> rateSet) {
    }

    private Prices prices(UUID householdId) {
        Map<String, Object> setting = jdbc.sql("select rate_kind, gold_premium_basis_points, silver_premium_basis_points "
                        + "from market_setting where household_id = ? and deleted_at is null limit 1")
                .param(householdId).query().listOfRows().stream().findFirst().orElse(Map.of());
        String rateKind = String.valueOf(setting.getOrDefault("rate_kind", "PARALLEL"));
        int goldPremium = ((Number) setting.getOrDefault("gold_premium_basis_points", 0)).intValue();
        int silverPremium = ((Number) setting.getOrDefault("silver_premium_basis_points", 0)).intValue();

        Map<String, Long> overrides = new HashMap<>();
        Map<String, LocalDate> overrideDates = new HashMap<>();
        jdbc.sql("select distinct on (instrument) instrument, price_micros, effective_from from market_override "
                        + "where household_id = ? and deleted_at is null and effective_from <= ? "
                        + "order by instrument, effective_from desc")
                .param(householdId).param(LocalDate.now(clock))
                .query().listOfRows()
                .forEach(row -> {
                    overrides.put((String) row.get("instrument"), ((Number) row.get("price_micros")).longValue());
                    overrideDates.put((String) row.get("instrument"), ((java.sql.Date) row.get("effective_from")).toLocalDate());
                });

        Map<Instrument, PriceQuote> quoted = new HashMap<>();
        quotes.findAll().forEach(quote -> quoted.put(quote.getInstrument(), quote));
        Instrument rateInstrument = "OFFICIAL".equals(rateKind) ? Instrument.USDIQD_OFFICIAL : Instrument.USDIQD_PARALLEL;
        long usdIqd = overrides.containsKey("USDIQD") ? overrides.get("USDIQD")
                : Optional.ofNullable(quoted.get(rateInstrument)).map(PriceQuote::getPriceMicros).orElse(0L);

        Map<Instrument, Long> spot = new HashMap<>();
        Map<Instrument, Long> perGramOverride = new HashMap<>();
        Map<String, Object> rateSet = new LinkedHashMap<>();
        rateSet.put("rateKind", rateKind);
        rateSet.put("usdIqdMicros", usdIqd);
        rateSet.put("usdIqdSource", overrides.containsKey("USDIQD") ? "override" : source(quoted.get(rateInstrument)));
        for (Instrument metal : List.of(Instrument.XAU, Instrument.XAG)) {
            PriceQuote quote = quoted.get(metal);
            spot.put(metal, quote == null ? 0L : quote.getPriceMicros());
            Long override = overrides.get(metal.name());
            if (override != null) {
                perGramOverride.put(metal, override);
            }
            rateSet.put(metal.name().toLowerCase() + "SpotMicros", spot.get(metal));
            rateSet.put(metal.name().toLowerCase() + "Source", override != null ? "override" : source(quote));
            if (override != null) {
                rateSet.put(metal.name().toLowerCase() + "OverridePerGram24kMicros", override);
                rateSet.put(metal.name().toLowerCase() + "OverrideFrom", overrideDates.get(metal.name()).toString());
            }
        }
        rateSet.put("goldPremiumBasisPoints", goldPremium);
        rateSet.put("silverPremiumBasisPoints", silverPremium);
        return new Prices(usdIqd, rateKind, spot, perGramOverride, goldPremium, silverPremium, rateSet);
    }

    private static long halfUp(long numerator, long divisor) {
        return Math.floorDiv(numerator + divisor / 2, divisor);
    }

    private static String source(PriceQuote quote) {
        return quote == null ? "none" : quote.getSource() + "@" + quote.getFetchedAt();
    }

    private long cash(UUID householdId, Prices prices) {
        return jdbc.sql("select balance, currency from cash_account where household_id = ? and deleted_at is null")
                .param(householdId).query().listOfRows().stream()
                .mapToLong(row -> toBase(((Number) row.get("balance")).longValue(), (String) row.get("currency"), prices))
                .sum();
    }

    private long toBase(long amount, String currency, Prices prices) {
        if (BASE_CURRENCY.equals(currency)) {
            return amount;
        }
        if (USD.equals(currency)) {
            return halfUp(amount * prices.usdIqdMicros(), RATE_SCALE * CENTS_PER_DOLLAR);
        }
        return amount;
    }

    private long metals(UUID householdId, Prices prices) {
        List<Map<String, Object>> lots = jdbc.sql("select l.id, l.metal, l.purity_basis_points, l.weight_mg, "
                        + "coalesce((select sum(d.weight_mg) from metal_disposal_lot d where d.lot_id = l.id and d.deleted_at is null), 0) as sold_mg "
                        + "from metal_lot l where l.household_id = ? and l.deleted_at is null")
                .param(householdId).query().listOfRows();
        long total = 0;
        for (Map<String, Object> lot : lots) {
            long remaining = ((Number) lot.get("weight_mg")).longValue() - ((Number) lot.get("sold_mg")).longValue();
            if (remaining <= 0) {
                continue;
            }
            boolean gold = "GOLD".equals(lot.get("metal"));
            Instrument instrument = gold ? Instrument.XAU : Instrument.XAG;
            total += MetalValuation.compute(new MetalValuation.Input(
                    prices.spotMicros().getOrDefault(instrument, 0L), prices.usdIqdMicros(),
                    ((Number) lot.get("purity_basis_points")).intValue(),
                    gold ? prices.goldPremiumBp() : prices.silverPremiumBp(), 0, remaining,
                    prices.overridePerGram24k().get(instrument))).value();
        }
        return total;
    }

    /** {receivables, liabilities}: balances derive from the payment ledger, converted at each debt's frozen rate. */
    private long[] debts(UUID householdId) {
        long receivables = 0;
        long liabilities = 0;
        for (Map<String, Object> row : jdbc.sql("select d.direction, d.principal, d.fx_rate_micros, "
                        + "coalesce((select sum(p.principal_component) from debt_payment p where p.debt_id = d.id and p.deleted_at is null), 0) as repaid "
                        + "from debt d where d.household_id = ? and d.deleted_at is null and d.status = 'ACTIVE'")
                .param(householdId).query().listOfRows()) {
            long balance = ((Number) row.get("principal")).longValue() - ((Number) row.get("repaid")).longValue();
            long base = Math.max(0, halfUp(balance * ((Number) row.get("fx_rate_micros")).longValue(), RATE_SCALE));
            if ("OWED".equals(row.get("direction"))) {
                receivables += base;
            } else {
                liabilities += base;
            }
        }
        return new long[] {receivables, liabilities};
    }
}
