package iq.mizan.networth.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import iq.mizan.domain.networth.NetWorth;

/** BR-10: the rate set names every price the figure was computed from. */
public record NetWorthResponse(
        long totalAssets,
        long totalLiabilities,
        long netWorth,
        List<NetWorth.Share> composition,
        Map<String, Object> rateSet,
        Instant computedAt) {
}
