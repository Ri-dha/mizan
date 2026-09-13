package iq.mizan.networth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.networth.dto.MonthCloseResponse;
import iq.mizan.networth.dto.NetWorthResponse;
import iq.mizan.networth.sync.MonthCloseSyncTable;
import iq.mizan.networth.sync.NetWorthSnapshotSyncTable;
import iq.mizan.sync.service.SyncWriter;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BR-14: closing takes an immutable snapshot; reopening is explicit and audited; closing again takes a fresh one. */
@Service
@AllArgsConstructor
public class MonthCloseService {

    private static final String SHARED = "SHARED";

    private final NetWorthCalculator calculator;
    private final SyncWriter syncWriter;
    private final NetWorthSnapshotSyncTable snapshotTable;
    private final MonthCloseSyncTable monthCloseTable;
    private final JdbcClient jdbc;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public MonthCloseResponse close(UUID householdId, UUID actorId, String monthKey) {
        Optional<Map<String, Object>> existing = find(householdId, monthKey);
        if (existing.map(MonthCloseService::isClosed).orElse(false)) {
            throw ApiException.of(ErrorCode.MONTH_ALREADY_CLOSED, "Month %s is already closed".formatted(monthKey));
        }

        NetWorthResponse figures = calculator.compute(householdId);
        Instant now = clock.instant();
        UUID snapshotId = UUID.randomUUID();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("visibility", SHARED);
        snapshot.put("monthKey", monthKey);
        snapshot.put("takenAt", now.toString());
        snapshot.put("totalAssets", figures.totalAssets());
        snapshot.put("totalLiabilities", figures.totalLiabilities());
        snapshot.put("netWorth", figures.netWorth());
        snapshot.put("liquidAssets", figures.liquidAssets());
        snapshot.put("illiquidAssets", figures.illiquidAssets());
        snapshot.put("composition", figures.composition());
        snapshot.put("rateSet", figures.rateSet());
        syncWriter.insert(snapshotTable, snapshotId, householdId, actorId, snapshot);

        Map<String, Object> close = new HashMap<>();
        close.put("snapshotId", snapshotId.toString());
        close.put("closedAt", now.toString());
        close.put("reopenedAt", null);
        UUID closeId = existing.map(row -> (UUID) row.get("id")).orElse(UUID.randomUUID());
        if (existing.isPresent()) {
            syncWriter.update(monthCloseTable, closeId, close);
        } else {
            close.put("visibility", SHARED);
            close.put("monthKey", monthKey);
            syncWriter.insert(monthCloseTable, closeId, householdId, actorId, close);
        }

        auditService.record(AuditRecord.of(AuditAction.MONTH_CLOSED, AuditEntityType.MONTH)
                .actorId(actorId).householdId(householdId).entityId(closeId)
                .after(Map.of("monthKey", monthKey, "netWorth", figures.netWorth(), "snapshotId", snapshotId.toString()))
                .build());
        return new MonthCloseResponse(monthKey, snapshotId, now, null, true);
    }

    @Transactional
    public MonthCloseResponse reopen(UUID householdId, UUID actorId, String monthKey) {
        Map<String, Object> row = find(householdId, monthKey)
                .filter(MonthCloseService::isClosed)
                .orElseThrow(() -> ApiException.of(ErrorCode.MONTH_NOT_CLOSED, "Month %s is not closed".formatted(monthKey)));
        Instant now = clock.instant();
        UUID closeId = (UUID) row.get("id");
        Map<String, Object> change = new HashMap<>();
        change.put("reopenedAt", now.toString());
        syncWriter.update(monthCloseTable, closeId, change);
        auditService.record(AuditRecord.of(AuditAction.MONTH_REOPENED, AuditEntityType.MONTH)
                .actorId(actorId).householdId(householdId).entityId(closeId)
                .after(Map.of("monthKey", monthKey))
                .build());
        return new MonthCloseResponse(monthKey, (UUID) row.get("snapshot_id"), toInstant(row.get("closed_at")), now, false);
    }

    @Transactional(readOnly = true)
    public List<MonthCloseResponse> list(UUID householdId) {
        return jdbc.sql("select month_key, snapshot_id, closed_at, reopened_at from month_close "
                        + "where household_id = ? and deleted_at is null order by month_key desc")
                .param(householdId).query().listOfRows().stream()
                .map(row -> new MonthCloseResponse((String) row.get("month_key"), (UUID) row.get("snapshot_id"),
                        toInstant(row.get("closed_at")), toInstant(row.get("reopened_at")), isClosed(row)))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isClosed(UUID householdId, String monthKey) {
        return find(householdId, monthKey).map(MonthCloseService::isClosed).orElse(false);
    }

    private Optional<Map<String, Object>> find(UUID householdId, String monthKey) {
        return jdbc.sql("select id, snapshot_id, closed_at, reopened_at from month_close "
                        + "where household_id = ? and month_key = ? and deleted_at is null")
                .param(householdId).param(monthKey).query().listOfRows().stream().findFirst();
    }

    private static boolean isClosed(Map<String, Object> row) {
        return row.get("closed_at") != null && row.get("reopened_at") == null;
    }

    private static Instant toInstant(Object value) {
        return value == null ? null : ((java.sql.Timestamp) value).toInstant();
    }
}
