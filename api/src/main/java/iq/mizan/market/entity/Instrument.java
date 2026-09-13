package iq.mizan.market.entity;

/**
 * Prices in micros: XAU and XAG in USD per troy ounce, the dollar rates in IQD per USD, and the
 * local-market instruments in IQD per gram of pure metal as a dealer quotes them.
 */
public enum Instrument {
    XAU,
    XAG,
    USDIQD_OFFICIAL,
    USDIQD_PARALLEL,
    XAU_LOCAL,
    XAG_LOCAL
}
