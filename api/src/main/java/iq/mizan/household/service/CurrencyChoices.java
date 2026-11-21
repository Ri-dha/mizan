package iq.mizan.household.service;

import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.market.service.CurrencyCatalogue;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/** Accepts only what the catalogue offers; a missing country is Iraq, a missing currency the country's own. */
@Component
@AllArgsConstructor
public class CurrencyChoices {

    private final CurrencyCatalogue catalogue;

    public CurrencyChoice validate(String country, String baseCurrency) {
        String chosenCountry = country == null ? CurrencyCatalogue.IRAQ : country;
        String countryCurrency = catalogue.currencyOfCountry(chosenCountry)
                .orElseThrow(() -> ApiException.of(ErrorCode.VALIDATION_FAILED, "This country is not offered"));
        String currency = baseCurrency == null ? countryCurrency : currency(baseCurrency);
        return new CurrencyChoice(chosenCountry, currency);
    }

    public String country(String country) {
        catalogue.currencyOfCountry(country).orElseThrow(() -> ApiException.of(ErrorCode.VALIDATION_FAILED, "This country is not offered"));
        return country;
    }

    public String currency(String currency) {
        if (!catalogue.offersCurrency(currency)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "This currency is not offered");
        }
        return currency;
    }
}
