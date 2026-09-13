package iq.mizan.auth.service;

import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AccountDeletionPurge {

    private final AccountDeletionService accountDeletionService;

    @Scheduled(cron = "${mizan.auth.deletion.purge-cron}")
    public void run() {
        accountDeletionService.purgeExpired();
    }
}
