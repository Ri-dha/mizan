package iq.mizan.report.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.tenancy.TenantSession;
import iq.mizan.report.dto.AnnualReportResponse;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates over the household's shared records. Runs as the system and filters on
 * visibility itself, so a restricted session (an advisor) gets the same totals a member
 * would, and private rows stay out for everyone.
 */
@Service
@AllArgsConstructor
public class ReportService {

    private static final int PERCENT_SCALE = 10;

    private final JdbcClient jdbc;
    private final TenantSession tenantSession;

    @Transactional(readOnly = true)
    public AnnualReportResponse annual(UUID householdId, int year) {
        tenantSession.elevateToSystem();
        String prefix = year + "-";
        String currency = jdbc.sql("select base_currency from household where id = ?").param(householdId).query(String.class).single();
        Map<String, Long> received = sumByMonth("select month_key, sum(base_amount) as total from income_receipt "
                + "where household_id = ? and visibility = 'SHARED' and deleted_at is null and month_key like ? group by month_key", householdId, prefix + "%");
        Map<String, Long> spent = sumByMonth("select month_key, sum(base_amount) as total from ledger_transaction "
                + "where household_id = ? and visibility = 'SHARED' and deleted_at is null and type = 'EXPENSE' and month_key like ? group by month_key", householdId, prefix + "%");

        List<AnnualReportResponse.Month> months = new ArrayList<>();
        long totalReceived = 0;
        long totalSpent = 0;
        for (int m = 1; m <= 12; m++) {
            String key = prefix + String.format("%02d", m);
            long in = received.getOrDefault(key, 0L);
            long out = spent.getOrDefault(key, 0L);
            totalReceived += in;
            totalSpent += out;
            months.add(new AnnualReportResponse.Month(key, in, out, in - out, savingRate(in, out)));
        }
        List<AnnualReportResponse.Snapshot> snapshots = jdbc.sql("select c.month_key, s.net_worth from month_close c "
                        + "join net_worth_snapshot s on s.id = c.snapshot_id where c.household_id = ? and c.closed_at is not null "
                        + "and c.reopened_at is null and c.month_key like ? order by c.month_key")
                .param(householdId).param(prefix + "%")
                .query((rs, i) -> new AnnualReportResponse.Snapshot(rs.getString("month_key"), rs.getLong("net_worth"))).list();
        return new AnnualReportResponse(year, currency, months, totalReceived, totalSpent, savingRate(totalReceived, totalSpent), snapshots);
    }

    private Map<String, Long> sumByMonth(String sql, UUID householdId, String like) {
        Map<String, Long> totals = new HashMap<>();
        jdbc.sql(sql).param(householdId).param(like).query().listOfRows()
                .forEach(row -> totals.put((String) row.get("month_key"), ((Number) row.get("total")).longValue()));
        return totals;
    }

    /** FR-RPT-05: (received − spent) ÷ received, one decimal place, half-up; null when nothing came in. */
    static Double savingRate(long received, long spent) {
        if (received <= 0) {
            return null;
        }
        return Math.floorDiv((received - spent) * 100 * PERCENT_SCALE * 2 + received, 2 * received) / (double) PERCENT_SCALE;
    }
}
