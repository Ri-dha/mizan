package iq.mizan.market.feed;

import java.util.Optional;

import iq.mizan.market.entity.Instrument;

/**
 * One provider of spot or FX quotes (FR-MKT-06). Feeds are tried in bean order and the first
 * that answers wins, so adding a provider is adding a bean.
 */
public interface PriceFeed {

    String name();

    boolean supports(Instrument instrument);

    /** Empty when the provider has no price right now; throw for a transport failure. */
    Optional<FetchedPrice> fetch(Instrument instrument);
}
