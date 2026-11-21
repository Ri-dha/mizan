package iq.mizan.sync.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import iq.mizan.sync.dto.SyncWaitResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

/**
 * ADR sync-4: devices hold a request open until their household's feed moves, so another device's
 * change arrives in seconds rather than at the next timed sync. Held in this instance's memory: with
 * more instances, a change pushed elsewhere is seen when the wait times out and the device asks again.
 */
@Component
public class SyncSignal {

    /** More open waits than this per person (tabs, devices) end their oldest, so none is held forever. */
    static final int MAX_WAITS_PER_USER = 4;

    private record Waiter(UUID userId, DeferredResult<SyncWaitResponse> result, long openedAt) {
    }

    /** The server's own timeout stays as a backstop if this one is ever late. */
    private static final long BACKSTOP_MILLIS = 10_000;

    private final Map<UUID, Set<Waiter>> waiting = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sync-wait-timer");
        thread.setDaemon(true);
        return thread;
    });

    public DeferredResult<SyncWaitResponse> open(UUID householdId, UUID userId, long timeoutMillis) {
        DeferredResult<SyncWaitResponse> result = new DeferredResult<>(timeoutMillis + BACKSTOP_MILLIS, SyncWaitResponse.UNCHANGED);
        Waiter waiter = new Waiter(userId, result, System.nanoTime());
        Set<Waiter> household = waiting.computeIfAbsent(householdId, id -> ConcurrentHashMap.newKeySet());
        household.add(waiter);
        ScheduledFuture<?> expiry = timer.schedule(() -> {
            household.remove(waiter);
            result.setResult(SyncWaitResponse.UNCHANGED);
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        result.onCompletion(() -> {
            household.remove(waiter);
            expiry.cancel(false);
        });
        household.stream()
                .filter(w -> w.userId().equals(userId))
                .sorted((a, b) -> Long.compare(b.openedAt(), a.openedAt()))
                .skip(MAX_WAITS_PER_USER)
                .forEach(w -> {
                    household.remove(w);
                    w.result().setResult(SyncWaitResponse.UNCHANGED);
                });
        return result;
    }

    /** After a push commits: every device of the household waiting on this instance pulls now. */
    public void changed(UUID householdId) {
        Set<Waiter> household = waiting.get(householdId);
        if (household == null) {
            return;
        }
        for (Waiter waiter : Set.copyOf(household)) {
            household.remove(waiter);
            waiter.result().setResult(SyncWaitResponse.CHANGED);
        }
    }
}
