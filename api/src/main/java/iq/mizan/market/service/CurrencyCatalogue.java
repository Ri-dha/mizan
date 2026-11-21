package iq.mizan.market.service;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import iq.mizan.market.dto.CurrencyCatalogueResponse;
import iq.mizan.market.feed.CurrencyRules;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Which currencies a household may count in and which countries a person may pick: those with a
 * rate (Frankfurter's list, plus the dollar and the dinar), each country with its own currency.
 */
@Service
@RequiredArgsConstructor
public class CurrencyCatalogue {

    public static final String IRAQ = "IQ";
    private static final Set<String> ALWAYS = Set.of("USD", "IQD");

    private final JdbcClient jdbc;

    @Cacheable(MarketCache.CURRENCIES)
    public CurrencyCatalogueResponse catalogue() {
        Set<String> currencies = currencies();
        List<CurrencyCatalogueResponse.Country> countries = new ArrayList<>();
        for (String code : Locale.getISOCountries()) {
            currencyOf(code).filter(currencies::contains)
                    .ifPresent(currency -> countries.add(new CurrencyCatalogueResponse.Country(code, currency)));
        }
        return new CurrencyCatalogueResponse(List.copyOf(currencies), countries);
    }

    public boolean offersCurrency(String currency) {
        return currency != null && catalogue().currencies().contains(currency);
    }

    public Optional<String> currencyOfCountry(String country) {
        return catalogue().countries().stream().filter(c -> c.code().equals(country)).map(CurrencyCatalogueResponse.Country::currency).findFirst();
    }

    private Set<String> currencies() {
        Set<String> codes = new TreeSet<>(ALWAYS);
        codes.addAll(jdbc.sql("select distinct currency from world_rate").query(String.class).list());
        codes.remove(CurrencyRules.EXCLUDED_CURRENCY);
        return codes;
    }

    static Optional<String> currencyOf(String country) {
        if (CurrencyRules.EXCLUDED_COUNTRY.equals(country)) {
            return Optional.empty();
        }
        if (CurrencyRules.PALESTINE.equals(country)) {
            return Optional.of(CurrencyRules.PALESTINE_CURRENCY);
        }
        try {
            Currency currency = Currency.getInstance(Locale.of("", country));
            return Optional.ofNullable(currency).map(Currency::getCurrencyCode);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
