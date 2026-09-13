package iq.mizan.household.job;

import iq.mizan.household.service.HouseholdService;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class HouseholdDeletionPurge {

    private static final Logger log = LoggerFactory.getLogger(HouseholdDeletionPurge.class);

    private final HouseholdService householdService;

    @Scheduled(cron = "${mizan.household.deletion.purge-cron}")
    public void purge() {
        int purged = householdService.purgeExpired();
        if (purged > 0) {
            log.info("Purged {} households after their grace period", purged);
        }
    }
}
