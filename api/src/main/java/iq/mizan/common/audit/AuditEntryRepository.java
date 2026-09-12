package iq.mizan.common.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    List<AuditEntry> findByActorIdAndActionOrderByOccurredAtDesc(UUID actorId, AuditAction action);
}
