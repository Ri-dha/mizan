package iq.mizan.market.feed;

/** Product rules on which currencies and countries Mizan offers (ADR currency-1). */
public final class CurrencyRules {

    public static final String EXCLUDED_CURRENCY = "ILS";
    public static final String EXCLUDED_COUNTRY = "IL";

    /** Palestine's ISO currency is the shekel; the Jordanian dinar is the one offered there. */
    public static final String PALESTINE = "PS";
    public static final String PALESTINE_CURRENCY = "JOD";

    private CurrencyRules() {
    }
}
