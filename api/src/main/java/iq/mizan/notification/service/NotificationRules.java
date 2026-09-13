package iq.mizan.notification.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import iq.mizan.domain.calendar.MonthWindow;
import iq.mizan.domain.expense.ExpenseSchedule;
import iq.mizan.domain.income.Frequency;
import iq.mizan.domain.income.PlannedIncome;
import iq.mizan.domain.plan.MonthFigures;
import iq.mizan.market.entity.Instrument;
import iq.mizan.notification.push.PushMessage;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * FR-NTF-01..05: what a member should be told today, read through their own row-level view so
 * private bills and lots only remind their owner. Each finding carries a key the log uses to
 * send it once.
 */
@Component
@AllArgsConstructor
public class NotificationRules {

    private static final long RATE_SCALE = 1_000_000L;
    private static final int BASIS_POINTS = 10_000;

    public record Finding(String dedupeKey, PushMessage message) {
    }

    private final JdbcClient jdbc;
    private final NotificationMessages messages;

    public List<Finding> evaluate(UUID householdId, String locale, NotificationSettings settings, LocalDate today) {
        if (settings.quietMode()) {
            return List.of();
        }
        Map<String, Object> household = jdbc.sql("select month_start_day, base_currency from household where id = ?")
                .param(householdId).query().singleRow();
        int startDay = ((Number) household.get("month_start_day")).intValue();
        String currency = (String) household.get("base_currency");
        String monthKey = MonthWindow.keyFor(today, startDay);

        List<Finding> findings = new ArrayList<>();
        if (settings.billDue()) {
            findings.addAll(billsDue(locale, settings.billLeadDays(), today, startDay, currency));
        }
        if (settings.payDay()) {
            findings.addAll(payDay(locale, today, monthKey, startDay, currency));
        }
        if (settings.overspend()) {
            findings.addAll(overspend(locale, monthKey, settings.overspendThresholdBp()));
        }
        if (settings.monthClose()) {
            findings.addAll(monthEnd(locale, today, monthKey, startDay));
        }
        if (settings.metalPrice()) {
            findings.addAll(metalMoves(locale, today, settings.metalMoveBp()));
        }
        return findings;
    }

    private List<Finding> billsDue(String locale, int defaultLeadDays, LocalDate today, int startDay, String currency) {
        String thisMonth = MonthWindow.keyFor(today, startDay);
        Set<String> paid = new HashSet<>();
        jdbc.sql("select recurring_expense_id, due_date from expense_occurrence where deleted_at is null and month_key in (?, ?)")
                .param(thisMonth).param(MonthWindow.next(thisMonth)).query().listOfRows()
                .forEach(row -> paid.add(row.get("recurring_expense_id") + ":" + row.get("due_date")));

        List<Finding> findings = new ArrayList<>();
        for (Map<String, Object> bill : jdbc.sql("select id, name, amount, fx_rate_micros, frequency, due_day, anchor_date, interval_days, "
                        + "active_from, active_to, reminder_days from recurring_expense where deleted_at is null").query().listOfRows()) {
            int lead = bill.get("reminder_days") == null ? defaultLeadDays : ((Number) bill.get("reminder_days")).intValue();
            ExpenseSchedule.Schedule schedule = new ExpenseSchedule.Schedule(
                    ExpenseSchedule.Frequency.valueOf((String) bill.get("frequency")),
                    (Integer) bill.get("due_day"), date(bill.get("anchor_date")), (Integer) bill.get("interval_days"),
                    date(bill.get("active_from")), date(bill.get("active_to")));
            for (String key : List.of(thisMonth, MonthWindow.next(thisMonth))) {
                for (LocalDate due : ExpenseSchedule.dueDates(schedule, MonthWindow.of(key, startDay))) {
                    long daysAway = ChronoUnit.DAYS.between(today, due);
                    if (due.isBefore(today) || daysAway > lead || paid.contains(bill.get("id") + ":" + due)) {
                        continue;
                    }
                    long amount = halfUp(((Number) bill.get("amount")).longValue() * ((Number) bill.get("fx_rate_micros")).longValue(), RATE_SCALE);
                    findings.add(new Finding(bill.get("id") + ":" + due + ":" + today,
                            messages.billDue(locale, (String) bill.get("name"), amount, currency, (int) daysAway)));
                }
            }
        }
        return findings;
    }

    private List<Finding> payDay(String locale, LocalDate today, String monthKey, int startDay, String currency) {
        List<Map<String, Object>> sources = jdbc.sql("select id, name, amount, fx_rate_micros, frequency, pay_day, anchor_date, active_from, active_to "
                + "from income_source where deleted_at is null").query().listOfRows();
        Set<Object> received = new HashSet<>(jdbc.sql("select income_source_id from income_receipt where deleted_at is null and month_key = ?")
                .param(monthKey).query(UUID.class).list());
        List<PlannedIncome.Source> planned = sources.stream().map(row -> new PlannedIncome.Source(
                halfUp(((Number) row.get("amount")).longValue() * ((Number) row.get("fx_rate_micros")).longValue(), RATE_SCALE),
                Frequency.valueOf((String) row.get("frequency")), (Integer) row.get("pay_day"), date(row.get("anchor_date")),
                date(row.get("active_from")), date(row.get("active_to")), List.of())).toList();

        List<Finding> findings = new ArrayList<>();
        for (PlannedIncome.Occurrence occurrence : PlannedIncome.compute(planned, MonthWindow.of(monthKey, startDay)).occurrences()) {
            Map<String, Object> source = sources.get(occurrence.sourceIndex());
            if (occurrence.date().equals(today) && !received.contains(source.get("id"))) {
                findings.add(new Finding(source.get("id") + ":" + today, messages.payDay(locale, (String) source.get("name"), occurrence.amount(), currency)));
            }
        }
        return findings;
    }

    private List<Finding> overspend(String locale, String monthKey, int thresholdBp) {
        Map<String, Object> plan = jdbc.sql("select id from plan where deleted_at is null and effective_from <= ? "
                        + "and (effective_to is null or effective_to >= ?) order by effective_from desc limit 1")
                .param(monthKey).param(monthKey).query().listOfRows().stream().findFirst().orElse(null);
        if (plan == null) {
            return List.of();
        }
        List<Map<String, Object>> buckets = jdbc.sql("select id, name, share_basis_points, fixed_amount from bucket "
                        + "where plan_id = ? and deleted_at is null order by sort_order").param(plan.get("id")).query().listOfRows();
        long received = jdbc.sql("select coalesce(sum(base_amount), 0) from income_receipt where deleted_at is null and month_key = ?")
                .param(monthKey).query(Long.class).single();
        Map<String, Long> spent = new HashMap<>();
        jdbc.sql("select bucket_id, sum(base_amount) as spent from ledger_transaction where deleted_at is null and type = 'EXPENSE' "
                        + "and month_key = ? and bucket_id is not null group by bucket_id").param(monthKey).query().listOfRows()
                .forEach(row -> spent.put(row.get("bucket_id").toString(), ((Number) row.get("spent")).longValue()));

        MonthFigures.Result figures = MonthFigures.compute(new MonthFigures.Input(received, received,
                buckets.stream().map(b -> new MonthFigures.Bucket(b.get("id").toString(), ((Number) b.get("share_basis_points")).intValue(),
                        b.get("fixed_amount") == null ? null : ((Number) b.get("fixed_amount")).longValue())).toList(),
                Map.of(), Map.of(), spent, Map.of(), Map.of()));

        List<Finding> findings = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            MonthFigures.BucketFigures figure = figures.buckets().get(i);
            if (figure.allocated() <= 0 || figure.spent() * BASIS_POINTS < figure.allocated() * (long) thresholdBp) {
                continue;
            }
            int percent = (int) (figure.spent() * 100 / figure.allocated());
            findings.add(new Finding(figure.id() + ":" + monthKey, messages.overspend(locale, (String) buckets.get(i).get("name"), percent)));
        }
        return findings;
    }

    private List<Finding> monthEnd(String locale, LocalDate today, String monthKey, int startDay) {
        if (MonthWindow.keyFor(today.plusDays(1), startDay).equals(monthKey)) {
            return List.of();
        }
        boolean closed = jdbc.sql("select count(*) from month_close where month_key = ? and closed_at is not null and reopened_at is null")
                .param(monthKey).query(Long.class).single() > 0;
        return closed ? List.of() : List.of(new Finding(monthKey, messages.monthClose(locale, monthKey)));
    }

    private List<Finding> metalMoves(String locale, LocalDate today, int thresholdBp) {
        Set<String> held = new HashSet<>(jdbc.sql("select distinct l.metal from metal_lot l where l.deleted_at is null and l.weight_mg > "
                + "coalesce((select sum(d.weight_mg) from metal_disposal_lot d where d.lot_id = l.id and d.deleted_at is null), 0)")
                .query(String.class).list());
        List<Finding> findings = new ArrayList<>();
        for (String metal : held) {
            Instrument instrument = "GOLD".equals(metal) ? Instrument.XAU : Instrument.XAG;
            List<Map<String, Object>> points = jdbc.sql("select day, price_micros from price_history where instrument = ? and day in (?, ?) order by day")
                    .param(instrument.name()).param(today.minusDays(1)).param(today).query().listOfRows();
            if (points.size() < 2) {
                continue;
            }
            long before = ((Number) points.get(0).get("price_micros")).longValue();
            long now = ((Number) points.get(1).get("price_micros")).longValue();
            if (before <= 0 || Math.abs(now - before) * BASIS_POINTS < before * (long) thresholdBp) {
                continue;
            }
            findings.add(new Finding(instrument.name() + ":" + today, messages.metalMove(locale, metal, (now - before) * 100.0 / before)));
        }
        return findings;
    }

    private static LocalDate date(Object value) {
        return value == null ? null : ((java.sql.Date) value).toLocalDate();
    }

    private static long halfUp(long numerator, long divisor) {
        return Math.floorDiv(numerator + divisor / 2, divisor);
    }
}
