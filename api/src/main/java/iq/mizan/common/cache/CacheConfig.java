package iq.mizan.common.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Caching wraps outside the transaction advisor (order 100), so a cache hit borrows no connection
 * and runs no tenancy statement. Only data that is the same for every caller may be cached.
 */
@Configuration
@EnableCaching(order = CacheConfig.CACHE_ADVISOR_ORDER)
public class CacheConfig {

    static final int CACHE_ADVISOR_ORDER = 50;
}
