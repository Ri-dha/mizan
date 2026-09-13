package iq.mizan.market;

import java.util.Map;

import iq.mizan.market.feed.LocalDealerPriceFeed;

final class LocalDealerPriceFeedTestAccess {

    private LocalDealerPriceFeedTestAccess() {
    }

    static Object dig(Map<?, ?> body, String path) {
        try {
            var method = LocalDealerPriceFeed.class.getDeclaredMethod("dig", Map.class, String.class);
            method.setAccessible(true);
            return method.invoke(null, body, path);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
