package iq.mizan.notification.repository;

import java.util.UUID;

import iq.mizan.notification.entity.NotificationLog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    boolean existsByUserIdAndKindAndDedupeKey(UUID userId, String kind, String dedupeKey);
}
