package iq.mizan.sync.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Per-field last-write-wins (FR-TRX-07). A field is taken when its clock is newer than the
 * stored one; when it loses and the values differ, that loss is reported as a conflict so
 * the client can show what its edit was overridden by.
 */
public final class FieldMerger {

    private FieldMerger() {
    }

    public record FieldConflict(String field, Object clientValue, Object serverValue,
                                String clientClock, String serverClock) {
    }

    public record Outcome(Map<String, Object> accepted, Map<String, String> clocks, List<FieldConflict> conflicts) {

        public boolean changed() {
            return !accepted.isEmpty();
        }
    }

    public static Outcome merge(
            Map<String, Object> storedFields, Map<String, String> storedClocks,
            Map<String, Object> incomingFields, Map<String, String> incomingClocks) {

        Map<String, Object> accepted = new LinkedHashMap<>();
        Map<String, String> clocks = new LinkedHashMap<>(storedClocks);
        List<FieldConflict> conflicts = new ArrayList<>();

        incomingFields.forEach((field, incomingValue) -> {
            String incomingClock = incomingClocks.get(field);
            String storedClock = storedClocks.get(field);
            if (HybridLogicalClock.isAfter(incomingClock, storedClock)) {
                accepted.put(field, incomingValue);
                clocks.put(field, incomingClock);
                return;
            }
            Object storedValue = storedFields.get(field);
            if (!Objects.equals(storedValue, incomingValue)) {
                conflicts.add(new FieldConflict(field, incomingValue, storedValue, incomingClock, storedClock));
            }
        });

        return new Outcome(accepted, clocks, conflicts);
    }
}
