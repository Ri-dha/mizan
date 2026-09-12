package iq.mizan.sync.repository;

import java.util.List;
import java.util.UUID;

import iq.mizan.sync.entity.SyncConflict;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncConflictRepository extends JpaRepository<SyncConflict, UUID> {

    List<SyncConflict> findByDeviceIdOrderByDetectedAtDesc(UUID deviceId, Pageable pageable);
}
