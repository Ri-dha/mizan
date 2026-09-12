package iq.mizan.sync.repository;

import java.util.UUID;

import iq.mizan.sync.entity.SyncOpReceipt;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncOpReceiptRepository extends JpaRepository<SyncOpReceipt, UUID> {
}
