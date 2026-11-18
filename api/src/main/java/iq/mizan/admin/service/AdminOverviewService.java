package iq.mizan.admin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import iq.mizan.common.tenancy.TenantSession;
import iq.mizan.market.feed.IqWealthFxFeed;
import iq.mizan.market.service.MarketService;
import iq.mizan.sync.table.SyncTable;
import iq.mizan.sync.table.SyncTableRegistry;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts across the whole service for operators (ADR admin-1). Runs as the system so row-level
 * security does not hide households, and reads only counts, states and sizes, never record values
 * (which are ciphertext anyway, ADR e2e-1).
 */
@Service
@AllArgsConstructor
public class AdminOverviewService {

    public record Overview(Map<String, Object> accounts, Map<String, Object> households, Map<String, Object> notifications,
                           Map<String, Object> storage, List<FeedStatus> feeds, IqWealthFxFeed.Quota iqWealth) {
    }

    public record FeedStatus(String name, java.time.Instant lastSuccessAt, java.time.Instant lastFailureAt, String lastError) {
    }

    private final JdbcClient jdbc;
    private final TenantSession tenantSession;
    private final SyncTableRegistry tables;
    private final MarketService marketService;
    private final IqWealthFxFeed iqWealth;

    @Transactional(readOnly = true)
    public Overview overview() {
        tenantSession.elevateToSystem();
        Map<String, Object> accounts = row("""
                select count(*) as total,
                       count(*) filter (where created_at > now() - interval '7 days') as joined_last_7_days,
                       count(*) filter (where created_at > now() - interval '30 days') as joined_last_30_days,
                       count(*) filter (where auth_scheme = 'LEGACY') as on_legacy_password,
                       count(*) filter (where suspended_at is not null) as suspended,
                       count(*) filter (where account_status = 'DELETION_REQUESTED') as deletion_requested,
                       count(*) filter (where platform_role = 'OPERATOR') as operators
                from app_user""");
        Map<String, Object> households = new java.util.LinkedHashMap<>(row("""
                select count(*) as total,
                       (select count(*) from vault_record) as sealed_records,
                       (select count(*) from household_key_grant) as key_grants
                from household"""));
        households.put("with_plain_rows", householdsWithPlainRows());
        Map<String, Object> notifications = row("""
                select (select count(*) from push_subscription) as subscribed_devices,
                       (select count(*) from notification_schedule where sent_at is null) as reminders_waiting,
                       (select count(*) from notification_schedule where sent_at > now() - interval '1 day') as reminders_sent_last_day""");
        Map<String, Object> storage = row("""
                select pg_size_pretty(pg_database_size(current_database())) as database_size,
                       (select count(*) from price_tick) as price_changes,
                       (select max(observed_at) from price_tick) as last_price_change,
                       (select count(*) from audit_entry) as audit_entries""");
        List<FeedStatus> feeds = new ArrayList<>();
        Map<String, MarketService.FeedHealth> health = marketService.feedHealth();
        for (String name : marketService.feedNames()) {
            MarketService.FeedHealth h = health.get(name);
            feeds.add(new FeedStatus(name, h == null ? null : h.lastSuccessAt(), h == null ? null : h.lastFailureAt(), h == null ? null : h.lastError()));
        }
        return new Overview(accounts, households, notifications, storage, feeds, iqWealth.quota());
    }

    private Map<String, Object> row(String sql) {
        return jdbc.sql(sql).query().singleRow();
    }

    /** Households migration has not finished for: they still have rows in a table from before ADR e2e-1. */
    private long householdsWithPlainRows() {
        List<String> selects = new ArrayList<>();
        for (SyncTable table : tables.all()) {
            boolean exists = jdbc.sql("select to_regclass(?) is not null").param("public." + table.name()).query(Boolean.class).single();
            if (exists) {
                selects.add("select household_id from " + table.name());
            }
        }
        if (selects.isEmpty()) {
            return 0;
        }
        return jdbc.sql("select count(distinct household_id) from (" + String.join(" union ", selects) + ") plain").query(Long.class).single();
    }
}
