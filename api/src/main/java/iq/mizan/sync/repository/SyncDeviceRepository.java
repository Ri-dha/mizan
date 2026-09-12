package iq.mizan.sync.repository;

import java.util.UUID;

import iq.mizan.sync.entity.SyncDevice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncDeviceRepository extends JpaRepository<SyncDevice, UUID> {
}
