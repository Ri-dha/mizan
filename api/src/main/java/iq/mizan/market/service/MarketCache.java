package iq.mizan.market.service;

import lombok.AllArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Prices are the same for everyone and every device asks for them on each sync, so they are
 * served from memory. Clearing waits for the commit: cleared earlier, a read in between would
 * cache the old price again. Entries also expire on their own (spring.cache.caffeine.spec), which
 * bounds how stale another instance can be.
 */
@Component
@AllArgsConstructor
public class MarketCache {

    public static final String QUOTES = "market-quotes";
    public static final String HISTORY = "market-history";
    public static final String CHANGES = "market-changes";

    private final CacheManager caches;

    public void clearAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clear();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clear();
            }
        });
    }

    private void clear() {
        for (String name : new String[] {QUOTES, HISTORY, CHANGES}) {
            var cache = caches.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
