package iq.mizan.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import iq.mizan.notification.entity.PushSubscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdAndHouseholdId(UUID userId, UUID householdId);

    @Query("select distinct new iq.mizan.notification.repository.Recipient(s.userId, s.householdId) from PushSubscription s")
    List<Recipient> recipients();
}
