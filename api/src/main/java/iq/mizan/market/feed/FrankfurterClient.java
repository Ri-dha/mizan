package iq.mizan.market.feed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import iq.mizan.market.WorldRateProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * api.frankfurter.dev v2: keyless daily rates collected from central banks. Only real currencies
 * are kept (the same API also lists crypto and metals), not the shekel (ADR currency-1), and neither the dollar, which is the
 * base, nor the dinar, whose Mizan rates come from the Baghdad market and the Central Bank.
 */
@Component
public class FrankfurterClient {

    public static final String NAME = "frankfurter.dev";

    private static final Set<String> SKIPPED = Set.of("USD", "IQD", CurrencyRules.EXCLUDED_CURRENCY);

    public record DayRate(String currency, LocalDate day, BigDecimal perUsd) {
    }

    private final RestClient client;

    public FrankfurterClient(RestClient.Builder builder, WorldRateProperties properties) {
        this.client = builder.baseUrl(properties.url()).build();
    }

    /** Every day's rate from {@code from} to the latest published, one request for the list of currencies and one for the rates. */
    public List<DayRate> since(LocalDate from) {
        Set<String> currencies = currencies();
        List<?> body = client.get()
                .uri(uri -> uri.path("/v2/rates").queryParam("base", "USD").queryParam("from", from.toString()).build())
                .retrieve()
                .body(List.class);
        List<DayRate> rates = new ArrayList<>();
        for (Object item : body == null ? List.of() : body) {
            if (item instanceof Map<?, ?> row
                    && row.get("quote") instanceof String code && currencies.contains(code)
                    && row.get("date") instanceof String date
                    && row.get("rate") instanceof Number rate && rate.doubleValue() > 0) {
                rates.add(new DayRate(code, LocalDate.parse(date), new BigDecimal(rate.toString())));
            }
        }
        return rates;
    }

    private Set<String> currencies() {
        List<?> body = client.get().uri("/v2/currencies").retrieve().body(List.class);
        Set<String> codes = new HashSet<>();
        for (Object item : body == null ? List.of() : body) {
            if (item instanceof Map<?, ?> row && "currency".equals(row.get("type"))
                    && row.get("iso_code") instanceof String code && code.length() == 3 && !SKIPPED.contains(code)) {
                codes.add(code);
            }
        }
        if (codes.isEmpty()) {
            throw new IllegalStateException("Frankfurter listed no currencies");
        }
        return codes;
    }
}
