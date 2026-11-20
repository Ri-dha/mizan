package iq.mizan.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.feed.IqWealthGoldFeed;
import iq.mizan.market.feed.PriceFeed;

import org.junit.jupiter.api.Test;

/** ADR prices-3: the operator's main gold source goes first for gold only; the rest stay as fallbacks. */
class GoldFeedOrderTest {

    private final PriceFeed bullionHouse = mock(PriceFeed.class);
    private final PriceFeed goldApiCom = mock(PriceFeed.class);
    private final PriceFeed iqWealthGold = mock(IqWealthGoldFeed.class);
    private final List<PriceFeed> byOrder = List.of(bullionHouse, iqWealthGold, goldApiCom);

    @Test
    void iqWealthAsMainLeadsForGoldAndTheOthersFollow() {
        assertThat(GoldFeedOrder.arrange(Instrument.XAU_LOCAL, byOrder, IqWealthGoldFeed.NAME)).containsExactly(iqWealthGold, bullionHouse, goldApiCom);
        assertThat(GoldFeedOrder.arrange(Instrument.XAU, byOrder, IqWealthGoldFeed.NAME)).containsExactly(iqWealthGold, bullionHouse, goldApiCom);
        assertThat(GoldFeedOrder.arrange(Instrument.XAU_LOCAL_BID, byOrder, IqWealthGoldFeed.NAME)).containsExactly(iqWealthGold, bullionHouse, goldApiCom);
    }

    @Test
    void otherwiseTheFeedsKeepTheirOrder() {
        assertThat(GoldFeedOrder.arrange(Instrument.XAU_LOCAL, byOrder, "baghdadbullionhouse.com")).containsExactly(bullionHouse, iqWealthGold, goldApiCom);
        assertThat(GoldFeedOrder.arrange(Instrument.XAG, byOrder, IqWealthGoldFeed.NAME)).containsExactly(bullionHouse, iqWealthGold, goldApiCom);
        assertThat(GoldFeedOrder.arrange(Instrument.XAU_LOCAL_BID, byOrder, "baghdadbullionhouse.com")).containsExactly(bullionHouse, iqWealthGold, goldApiCom);
    }
}
