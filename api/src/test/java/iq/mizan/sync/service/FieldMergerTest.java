package iq.mizan.sync.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class FieldMergerTest {

    private static final String OLDER = "1700000000000:0001:device-a";
    private static final String NEWER = "1700000000001:0000:device-b";

    @Test
    void newerClockWins() {
        var outcome = FieldMerger.merge(
                Map.of("name", "Wallet"), Map.of("name", OLDER),
                Map.of("name", "Purse"), Map.of("name", NEWER));

        assertThat(outcome.accepted()).containsEntry("name", "Purse");
        assertThat(outcome.clocks()).containsEntry("name", NEWER);
        assertThat(outcome.conflicts()).isEmpty();
    }

    @Test
    void olderClockLosesAndIsReportedWhenValuesDiffer() {
        var outcome = FieldMerger.merge(
                Map.of("name", "Wallet"), Map.of("name", NEWER),
                Map.of("name", "Purse"), Map.of("name", OLDER));

        assertThat(outcome.accepted()).isEmpty();
        assertThat(outcome.changed()).isFalse();
        assertThat(outcome.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.field()).isEqualTo("name");
            assertThat(conflict.clientValue()).isEqualTo("Purse");
            assertThat(conflict.serverValue()).isEqualTo("Wallet");
        });
    }

    @Test
    void olderClockWithSameValueIsSilentlyIgnored() {
        var outcome = FieldMerger.merge(
                Map.of("name", "Wallet"), Map.of("name", NEWER),
                Map.of("name", "Wallet"), Map.of("name", OLDER));

        assertThat(outcome.accepted()).isEmpty();
        assertThat(outcome.conflicts()).isEmpty();
    }

    @Test
    void fieldsMergeIndependently() {
        var outcome = FieldMerger.merge(
                Map.of("name", "Wallet", "balance", 100L), Map.of("name", NEWER, "balance", OLDER),
                Map.of("name", "Purse", "balance", 250L), Map.of("name", OLDER, "balance", NEWER));

        assertThat(outcome.accepted()).containsOnly(Map.entry("balance", 250L));
        assertThat(outcome.clocks()).containsEntry("name", NEWER).containsEntry("balance", NEWER);
        assertThat(outcome.conflicts()).extracting(FieldMerger.FieldConflict::field).containsExactly("name");
    }

    @Test
    void unknownStoredClockAcceptsTheIncomingValue() {
        var outcome = FieldMerger.merge(Map.of(), Map.of(), Map.of("name", "Wallet"), Map.of("name", OLDER));

        assertThat(outcome.accepted()).containsEntry("name", "Wallet");
    }
}
