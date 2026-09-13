package iq.mizan.household.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "household")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Household {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "month_start_day", nullable = false)
    private int monthStartDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "deletion_requested_by")
    private UUID deletionRequestedBy;

    public static Household create(String name, String baseCurrency, int monthStartDay) {
        Household household = new Household();
        household.id = UUID.randomUUID();
        household.name = name;
        household.baseCurrency = baseCurrency;
        household.monthStartDay = monthStartDay;
        household.createdAt = Instant.now();
        household.updatedAt = household.createdAt;
        return household;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeMonthStartDay(int monthStartDay) {
        this.monthStartDay = monthStartDay;
    }

    public void requestDeletion(UUID byUserId, Instant now) {
        this.deletionRequestedAt = now;
        this.deletionRequestedBy = byUserId;
    }

    public void cancelDeletion() {
        this.deletionRequestedAt = null;
        this.deletionRequestedBy = null;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
