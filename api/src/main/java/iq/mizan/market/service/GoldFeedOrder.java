package iq.mizan.market.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.feed.IqWealthGoldFeed;
import iq.mizan.market.feed.PriceFeed;

/**
 * ADR prices-3: the operator picks which feed answers gold first. By its order IQWealth sits after
 * the dealers and before the keyless world feed; chosen as main, it moves to the front for gold
 * and every other feed keeps its place as a fallback.
 */
final class GoldFeedOrder {

    private static final Set<Instrument> GOLD = EnumSet.of(Instrument.XAU, Instrument.XAU_LOCAL, Instrument.XAU_LOCAL_BID);

    private GoldFeedOrder() {
    }

    static List<PriceFeed> arrange(Instrument instrument, List<PriceFeed> feeds, String primary) {
        if (!GOLD.contains(instrument) || !IqWealthGoldFeed.NAME.equals(primary)) {
            return feeds;
        }
        return Stream.concat(feeds.stream().filter(IqWealthGoldFeed.class::isInstance),
                feeds.stream().filter(feed -> !(feed instanceof IqWealthGoldFeed))).toList();
    }
}
