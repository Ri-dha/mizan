package iq.mizan.common.audit;

import java.util.Map;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes in the caller's transaction so a mutation and its record commit or roll back together. */
@Service
@AllArgsConstructor
public class AuditService {

    private final AuditEntryRepository repository;
    private final JsonMapper jsonMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditRecord record) {
        repository.save(AuditEntry.from(record, toJson(record.before()), toJson(record.after())));
    }

    /**
     * Commits on its own, for records that must survive the caller throwing — a security
     * incident recorded moments before the 401 that ends the request.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordNow(AuditRecord record) {
        repository.save(AuditEntry.from(record, toJson(record.before()), toJson(record.after())));
    }

    private String toJson(Map<String, Object> state) {
        return state == null ? null : jsonMapper.writeValueAsString(state);
    }
}
