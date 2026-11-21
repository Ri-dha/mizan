package iq.mizan.market.dto;

import java.util.List;

/** Currencies a household may count in, and the countries offered with each one's currency. */
public record CurrencyCatalogueResponse(List<String> currencies, List<Country> countries) {

    public record Country(String code, String currency) {
    }
}
