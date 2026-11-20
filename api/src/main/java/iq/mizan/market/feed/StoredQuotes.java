package iq.mizan.market.feed;

import java.util.Optional;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.repository.PriceQuoteRepository;

/**
 * Only the instance holding the poll lock keeps IQWealth's answer in memory (ADR
 * csp-and-job-lock-1); the others answer with what that instance stored, rather than letting a
 * later feed overwrite it. Only while a key is set, so removing it lets the other feeds answer.
 */
final class StoredQuotes {

    private final PriceQuoteRepository quotes;
    private final IqWealthClient iqWealth;

    StoredQuotes(PriceQuoteRepository quotes, IqWealthClient iqWealth) {
        this.quotes = quotes;
        this.iqWealth = iqWealth;
    }

    Optional<FetchedPrice> last(Instrument instrument) {
        if (!iqWealth.configured()) {
            return Optional.empty();
        }
        return quotes.findById(instrument)
                .filter(quote -> IqWealthClient.NAME.equals(quote.getSource()))
                .map(quote -> new FetchedPrice(quote.getPriceMicros(), quote.getQuotedAt()));
    }
}
