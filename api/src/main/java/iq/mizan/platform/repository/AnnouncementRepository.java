package iq.mizan.platform.repository;

import java.util.List;
import java.util.UUID;

import iq.mizan.platform.entity.Announcement;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    List<Announcement> findTop50ByOrderByCreatedAtDesc();

    List<Announcement> findByRetractedAtIsNullOrderByCreatedAtDesc();
}
