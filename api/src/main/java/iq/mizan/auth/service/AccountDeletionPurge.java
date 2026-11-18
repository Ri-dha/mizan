package iq.mizan.auth.service;

import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import iq.mizan.common.jobs.JobLock;
import java.time.Duration;

@Component
@AllArgsConstructor
public class AccountDeletionPurge {

    private final AccountDeletionService accountDeletionService;
    private final JobLock jobLock;

    private static final Duration RUNS_AT_MOST = Duration.ofHours(1);
    private static final Duration HELD_AT_LEAST = Duration.ofMinutes(5);

    @Scheduled(cron = "${mizan.auth.deletion.purge-cron}")
    public void run() {
        jobLock.runExclusively("account-deletion-purge", RUNS_AT_MOST, HELD_AT_LEAST, accountDeletionService::purgeExpired);
    }
}
