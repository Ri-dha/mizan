package iq.mizan.sync.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.sync.SyncProperties;
import iq.mizan.sync.dto.ConflictResponse;
import iq.mizan.sync.dto.SyncOp;
import iq.mizan.sync.dto.SyncPullResponse;
import iq.mizan.sync.dto.SyncPushRequest;
import iq.mizan.sync.dto.SyncPushResponse;
import iq.mizan.sync.dto.SyncRecordResponse;
import iq.mizan.sync.entity.SyncConflict;
import iq.mizan.sync.entity.SyncDevice;
import iq.mizan.sync.entity.SyncOpReceipt;
import iq.mizan.sync.mapper.SyncMapper;
import iq.mizan.sync.repository.SyncConflictRepository;
import iq.mizan.sync.repository.SyncOpReceiptRepository;
import iq.mizan.sync.table.SyncTable;
import iq.mizan.sync.table.SyncTableRegistry;

import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@AllArgsConstructor
public class SyncService {

    private static final int CONFLICT_PAGE = 200;

    private final SyncTableRegistry registry;
    private final SyncRowRepository rows;
    private final SyncLogRepository log;
    private final SyncConflictRepository conflictRepository;
    private final SyncOpReceiptRepository receiptRepository;
    private final DeviceService deviceService;
    private final SyncMapper syncMapper;
    private final AuditService auditService;
    private final JsonMapper jsonMapper;
    private final SyncProperties properties;

    /** Applies a batch atomically; every op is acknowledged exactly once, however often it is sent. */
    @Transactional
    public SyncPushResponse push(CurrentUser user, SyncPushRequest request) {
        if (request.ops().size() > properties.maxPushOps()) {
            throw ApiException.of(ErrorCode.SYNC_BATCH_TOO_LARGE,
                    "Push at most %d ops per request".formatted(properties.maxPushOps()));
        }
        SyncDevice device = deviceService.requireOwned(user, request.deviceId());

        List<UUID> applied = new ArrayList<>();
        List<ConflictResponse> conflicts = new ArrayList<>();
        for (SyncOp op : request.ops()) {
            if (receiptRepository.existsById(op.opId())) {
                applied.add(op.opId());
                continue;
            }
            conflicts.addAll(apply(user, device, op));
            receiptRepository.save(SyncOpReceipt.of(op.opId(), device.getId()));
            applied.add(op.opId());
        }
        device.recordPush();
        return new SyncPushResponse(applied, conflicts);
    }

    @Transactional
    public SyncPullResponse pull(CurrentUser user, UUID deviceId, long since, Integer requestedLimit) {
        SyncDevice device = deviceService.requireOwned(user, deviceId);
        int limit = requestedLimit == null
                ? properties.pullPageSize() : Math.min(requestedLimit, properties.pullPageSize());

        List<SyncLogRepository.Entry> entries = log.after(since, limit + 1);
        boolean hasMore = entries.size() > limit;
        if (hasMore) {
            entries = entries.subList(0, limit);
        }

        Map<String, Map<UUID, Long>> latestSeqByTable = new LinkedHashMap<>();
        entries.forEach(entry -> latestSeqByTable
                .computeIfAbsent(entry.tableName(), t -> new LinkedHashMap<>())
                .put(entry.rowId(), entry.seq()));

        List<SyncRecordResponse> records = new ArrayList<>();
        latestSeqByTable.forEach((tableName, seqByRow) -> {
            SyncTable table = registry.require(tableName);
            rows.fetch(table, seqByRow.keySet()).forEach(row -> records.add(new SyncRecordResponse(
                    tableName, row.id(), row.ownerId(), row.fields(), row.clocks(), seqByRow.get(row.id()))));
        });
        records.sort(java.util.Comparator.comparingLong(SyncRecordResponse::seq));

        long nextSeq = entries.isEmpty() ? since : entries.getLast().seq();
        device.recordPull(nextSeq);
        return new SyncPullResponse(records, nextSeq, hasMore);
    }

    @Transactional(readOnly = true)
    public List<ConflictResponse> conflicts(CurrentUser user, UUID deviceId) {
        SyncDevice device = deviceService.requireOwned(user, deviceId);
        return conflictRepository
                .findByDeviceIdOrderByDetectedAtDesc(device.getId(), PageRequest.of(0, CONFLICT_PAGE))
                .stream()
                .map(syncMapper::toResponse)
                .toList();
    }

    private List<ConflictResponse> apply(CurrentUser user, SyncDevice device, SyncOp op) {
        SyncTable table = registry.require(op.table());
        if (table.readOnly()) {
            throw ApiException.of(ErrorCode.SYNC_TABLE_READ_ONLY,
                    "Table '%s' is written by the server only".formatted(op.table())).property("opId", op.opId());
        }
        Map<String, Object> incoming = normalised(table, op);
        Map<String, String> clocks = op.fields().keySet().stream().collect(Collectors.toMap(
                Function.identity(), field -> HybridLogicalClock.validated(op.clocks().get(field), field),
                (a, b) -> a, LinkedHashMap::new));

        StoredRow stored = rows.lockForUpdate(table, op.rowId()).orElse(null);
        FieldMerger.Outcome outcome = stored == null
                ? FieldMerger.merge(Map.of(), Map.of(), incoming, clocks)
                : FieldMerger.merge(stored.fields(), stored.clocks(), incoming, clocks);

        try {
            if (stored == null) {
                rows.insert(table, op.rowId(), user.householdId(), user.userId(), outcome.accepted(), outcome.clocks());
                audit(AuditAction.SYNC_INSERTED, user, op, null, outcome.accepted());
            } else if (outcome.changed()) {
                rows.update(table, op.rowId(), outcome.accepted(), outcome.clocks());
                audit(AuditAction.SYNC_UPDATED, user, op, stored.fields(), outcome.accepted());
            }
        } catch (DataIntegrityViolationException e) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "The record violates a constraint of table '%s'".formatted(op.table()))
                    .property("opId", op.opId());
        }

        return outcome.conflicts().stream()
                .map(conflict -> conflictRepository.save(SyncConflict.builder()
                        .householdId(user.householdId())
                        .deviceId(device.getId())
                        .tableName(op.table())
                        .rowId(op.rowId())
                        .field(conflict.field())
                        .clientValue(jsonMapper.writeValueAsString(conflict.clientValue()))
                        .serverValue(jsonMapper.writeValueAsString(conflict.serverValue()))
                        .clientClock(conflict.clientClock())
                        .serverClock(conflict.serverClock())
                        .build()))
                .map(syncMapper::toResponse)
                .toList();
    }

    private Map<String, Object> normalised(SyncTable table, SyncOp op) {
        Map<String, Object> normalised = new LinkedHashMap<>();
        op.fields().forEach((field, value) -> {
            var type = table.fields().get(field);
            if (type == null) {
                throw ApiException.of(ErrorCode.SYNC_FIELD_UNKNOWN,
                        "Table '%s' has no field '%s'".formatted(op.table(), field));
            }
            normalised.put(field, type.normalize(value, jsonMapper));
        });
        return normalised;
    }

    private void audit(AuditAction action, CurrentUser user, SyncOp op,
                       Map<String, Object> before, Map<String, Object> after) {
        auditService.record(AuditRecord.of(action, AuditEntityType.SYNC_RECORD)
                .actorId(user.userId())
                .householdId(user.householdId())
                .entityId(op.rowId())
                .before(before)
                .after(Map.of("table", op.table(), "fields", after))
                .build());
    }
}
