package iq.mizan.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Each currency's latest rate per US dollar; the dinar is not listed, its rates are in the quotes. */
public record WorldRatesResponse(String source, List<WorldRate> rates) {

    public record WorldRate(String currency, BigDecimal perUsd, LocalDate day) {
    }
}
