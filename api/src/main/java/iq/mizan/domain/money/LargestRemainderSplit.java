package iq.mizan.domain.money;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a whole amount by shares so the parts always sum to the whole (BR-02): each part
 * takes its floor, and the dinars left over go to the largest share.
 */
public final class LargestRemainderSplit {

    public static final int BASIS_POINTS = 10_000;

    private LargestRemainderSplit() {
    }

    /** @param sharesBasisPoints one entry per part, in hundredths of a percent, summing to 10,000 */
    public static List<Long> split(long total, List<Integer> sharesBasisPoints) {
        long sumShares = sharesBasisPoints.stream().mapToLong(Integer::longValue).sum();
        if (sumShares != BASIS_POINTS) {
            throw new IllegalArgumentException("Shares must sum to 10,000 basis points, got " + sumShares);
        }

        List<Long> parts = new ArrayList<>(sharesBasisPoints.size());
        long allocated = 0;
        int largest = 0;
        for (int i = 0; i < sharesBasisPoints.size(); i++) {
            long part = Math.floorDiv(total * sharesBasisPoints.get(i), BASIS_POINTS);
            parts.add(part);
            allocated += part;
            if (sharesBasisPoints.get(i) > sharesBasisPoints.get(largest)) {
                largest = i;
            }
        }
        parts.set(largest, parts.get(largest) + (total - allocated));
        return parts;
    }
}
