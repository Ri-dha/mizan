package iq.mizan.household.job;

import iq.mizan.household.service.HouseholdService;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import iq.mizan.common.jobs.JobLock;
import java.time.Duration;

@Component
@AllArgsConstructor
public class HouseholdDeletionPurge {

    private static final Logger log = LoggerFactory.getLogger(HouseholdDeletionPurge.class);

    private final HouseholdService householdService;
    private final JobLock jobLock;

    private static final Duration RUNS_AT_MOST = Duration.ofHours(1);
    private static final Duration HELD_AT_LEAST = Duration.ofMinutes(5);

    @Scheduled(cron = "${mizan.household.deletion.purge-cron}")
    public void purge() {
        jobLock.runExclusively("household-deletion-purge", RUNS_AT_MOST, HELD_AT_LEAST, () -> {
            int purged = householdService.purgeExpired();
            if (purged > 0) {
                log.info("Purged {} households after their grace period", purged);
            }
        });
    }
}
