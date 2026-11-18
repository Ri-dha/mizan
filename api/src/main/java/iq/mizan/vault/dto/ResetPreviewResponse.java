package iq.mizan.vault.dto;

import java.util.List;
import java.util.UUID;

/**
 * What starting over would remove (ADR e2e-3). {@code soleKeyHolder} households lose their shared
 * records as well, because nobody else can open them.
 */
public record ResetPreviewResponse(long privateRecords, List<Household> households) {

    public record Household(UUID householdId, String name, boolean soleKeyHolder, long sharedRecordsLost) {
    }
}
