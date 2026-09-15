package iq.mizan.market.entity;

/**
 * Prices in micros: XAU and XAG in USD per troy ounce, the dollar rates in IQD per USD, and the
 * local-market instruments in IQD per gram of pure metal: what a dealer sells at (ask) and what
 * a dealer pays (bid).
 */
public enum Instrument {
    XAU,
    XAG,
    USDIQD_OFFICIAL,
    USDIQD_PARALLEL,
    XAU_LOCAL,
    XAG_LOCAL,
    XAU_LOCAL_BID,
    XAG_LOCAL_BID
}
