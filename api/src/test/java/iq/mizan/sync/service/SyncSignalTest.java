package iq.mizan.sync.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import iq.mizan.sync.dto.SyncWaitResponse;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.DeferredResult;

class SyncSignalTest {

    private final SyncSignal signal = new SyncSignal();
    private final UUID household = UUID.randomUUID();
    private final UUID person = UUID.randomUUID();

    @Test
    void aChangeWakesEveryWaitOfTheHouseholdAndNoOther() {
        DeferredResult<SyncWaitResponse> mine = signal.open(household, person, 60_000);
        DeferredResult<SyncWaitResponse> elsewhere = signal.open(UUID.randomUUID(), UUID.randomUUID(), 60_000);

        signal.changed(household);

        assertThat(mine.getResult()).isEqualTo(SyncWaitResponse.CHANGED);
        assertThat(elsewhere.hasResult()).isFalse();
    }

    @Test
    void tooManyWaitsForOnePersonEndTheOldest() {
        List<DeferredResult<SyncWaitResponse>> waits = new ArrayList<>();
        for (int i = 0; i <= SyncSignal.MAX_WAITS_PER_USER; i++) {
            waits.add(signal.open(household, person, 60_000));
        }
        assertThat(waits.getFirst().getResult()).isEqualTo(SyncWaitResponse.UNCHANGED);
        assertThat(waits.subList(1, waits.size())).noneMatch(DeferredResult::hasResult);
    }

    @Test
    void aWaitEndsUnchangedWhenItsTimeIsUp() throws InterruptedException {
        DeferredResult<SyncWaitResponse> wait = signal.open(household, person, 50);
        Thread.sleep(300);
        assertThat(wait.getResult()).isEqualTo(SyncWaitResponse.UNCHANGED);
    }
}
