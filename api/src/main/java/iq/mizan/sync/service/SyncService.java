package iq.mizan.sync.service;

import java.util.ArrayList;
import java.util.Comparator;
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
import iq.mizan.common.security.Permission;
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
import iq.mizan.sync.table.SyncColumnType;
import iq.mizan.sync.table.SyncTable;
import iq.mizan.sync.table.SyncTableRegistry;

import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Push stores ciphertext the server cannot read and merges it per field by clock alone
 * (ADR sync-1, ADR e2e-1). Pull serves encrypted rows, and legacy plain rows until their
 * household has migrated.
 */
@Service
@AllArgsConstructor
public class SyncService {

    private static final int CONFLICT_PAGE = 200;
    private static final String VAULT_PREFIX = "vault:";

    private final SyncTableRegistry registry;
    private final SyncRowRepository legacyRows;
    private final VaultRowRepository vault;
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

        Map<UUID, Long> vaultSeq = new LinkedHashMap<>();
        Map<String, Map<UUID, Long>> legacySeq = new LinkedHashMap<>();
        for (SyncLogRepository.Entry entry : entries) {
            if (entry.tableName().startsWith(VAULT_PREFIX)) {
                vaultSeq.put(entry.rowId(), entry.seq());
            } else {
                legacySeq.computeIfAbsent(entry.tableName(), t -> new LinkedHashMap<>()).put(entry.rowId(), entry.seq());
            }
        }

        List<SyncRecordResponse> records = new ArrayList<>();
        vault.fetch(vaultSeq.keySet()).forEach(row -> records.add(new SyncRecordResponse(
                row.tableName(), row.id(), row.ownerId(), row.fields(), row.clocks(), vaultSeq.get(row.id()), true)));
        legacySeq.forEach((tableName, seqByRow) -> {
            SyncTable table = registry.require(tableName);
            legacyRows.fetch(table, seqByRow.keySet()).forEach(row -> records.add(new SyncRecordResponse(
                    tableName, row.id(), row.ownerId(), row.fields(), row.clocks(), seqByRow.get(row.id()), false)));
        });
        records.sort(Comparator.comparingLong(SyncRecordResponse::seq));

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
        Map<String, Object> incoming = validated(table, op);
        Map<String, String> clocks = op.fields().keySet().stream().collect(Collectors.toMap(
                Function.identity(), field -> HybridLogicalClock.validated(op.clocks().get(field), field),
                (a, b) -> a, LinkedHashMap::new));

        VaultRow stored = vault.lockForUpdate(op.rowId()).orElse(null);
        if (stored != null && !stored.tableName().equals(op.table())) {
            throw ApiException.of(ErrorCode.SYNC_TABLE_MISMATCH,
                    "Record %s belongs to '%s'".formatted(op.rowId(), stored.tableName())).property("opId", op.opId());
        }
        if (stored != null && table.appendOnly()) {
            throw ApiException.of(ErrorCode.SYNC_RECORD_IMMUTABLE,
                    "Records of '%s' cannot change once written".formatted(op.table())).property("opId", op.opId());
        }
        FieldMerger.Outcome outcome = stored == null
                ? FieldMerger.merge(Map.of(), Map.of(), incoming, clocks)
                : FieldMerger.merge(stored.fields(), stored.clocks(), incoming, clocks);

        Map<String, Object> merged = new LinkedHashMap<>(stored == null ? Map.of() : stored.fields());
        merged.putAll(outcome.accepted());
        refuseSharedWritesFromRestrictedSessions(user, merged, op);

        try {
            if (stored == null) {
                vault.insert(op.rowId(), user.householdId(), user.userId(), op.table(), merged, outcome.clocks());
                audit(AuditAction.SYNC_INSERTED, user, op, outcome.accepted());
            } else if (outcome.changed()) {
                vault.update(op.rowId(), merged, outcome.clocks());
                audit(AuditAction.SYNC_UPDATED, user, op, outcome.accepted());
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

    /** Plain fields keep their meaning; every other field must arrive as ciphertext. */
    private Map<String, Object> validated(SyncTable table, SyncOp op) {
        Map<String, Object> values = new LinkedHashMap<>();
        op.fields().forEach((field, value) -> {
            if (!table.fields().containsKey(field)) {
                throw ApiException.of(ErrorCode.SYNC_FIELD_UNKNOWN,
                        "Table '%s' has no field '%s'".formatted(op.table(), field));
            }
            if (VaultFields.VISIBILITY.equals(field)) {
                if (!VaultFields.VISIBILITIES.contains(String.valueOf(value))) {
                    throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Visibility must be SHARED or PRIVATE");
                }
                values.put(field, value);
            } else if (VaultFields.DELETED_AT.equals(field)) {
                values.put(field, SyncColumnType.TIMESTAMP.normalize(value, jsonMapper));
            } else if (VaultFields.isCiphertext(value)) {
                values.put(field, value);
            } else {
                throw ApiException.of(ErrorCode.SYNC_PLAIN_VALUE_REFUSED,
                        "Field '%s' of '%s' must be encrypted on the device".formatted(field, op.table()))
                        .property("opId", op.opId());
            }
        });
        return values;
    }

    /** Dependents never hold the household key, so nothing they write may be readable by the household. */
    private static void refuseSharedWritesFromRestrictedSessions(CurrentUser user, Map<String, Object> merged, SyncOp op) {
        boolean shared = !"PRIVATE".equals(merged.getOrDefault(VaultFields.VISIBILITY, VaultFields.SHARED));
        if (shared && !user.permissions().contains(Permission.HOUSEHOLD_VIEW)) {
            throw ApiException.of(ErrorCode.SYNC_SHARED_WRITE_REFUSED,
                    "This role may only write private records").property("opId", op.opId());
        }
    }

    /** Field names only: the values are ciphertext and would only bloat the trail. */
    private void audit(AuditAction action, CurrentUser user, SyncOp op, Map<String, Object> accepted) {
        auditService.record(AuditRecord.of(action, AuditEntityType.SYNC_RECORD)
                .actorId(user.userId())
                .householdId(user.householdId())
                .entityId(op.rowId())
                .after(Map.of("table", op.table(), "fields", List.copyOf(accepted.keySet())))
                .build());
    }
}
