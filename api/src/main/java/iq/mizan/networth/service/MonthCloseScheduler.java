package iq.mizan.networth.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import iq.mizan.domain.calendar.MonthWindow;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** FR-NET-03: the month that just ended is snapshotted automatically unless someone already closed it. */
@Component
@AllArgsConstructor
public class MonthCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthCloseScheduler.class);

    private final MonthCloseService monthCloseService;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Scheduled(cron = "${mizan.networth.auto-close-cron}")
    @Transactional
    public void closeEndedMonths() {
        jdbc.sql("select set_config('app.system', 'on', true)").query().singleRow();
        LocalDate today = LocalDate.now(clock);
        for (Map<String, Object> household : jdbc.sql("select h.id, h.month_start_day, m.user_id from household h "
                + "join membership m on m.household_id = h.id and m.role = 'OWNER'").query().listOfRows()) {
            UUID householdId = (UUID) household.get("id");
            String ended = MonthWindow.previous(MonthWindow.keyFor(today, ((Number) household.get("month_start_day")).intValue()));
            if (monthCloseService.isClosed(householdId, ended)) {
                continue;
            }
            try {
                monthCloseService.close(householdId, (UUID) household.get("user_id"), ended);
            } catch (RuntimeException e) {
                log.warn("Auto-close of {} for household {} failed: {}", ended, householdId, e.getMessage());
            }
        }
    }
}
