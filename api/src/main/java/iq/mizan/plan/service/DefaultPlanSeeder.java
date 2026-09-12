package iq.mizan.plan.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.tenancy.TenantSession;
import iq.mizan.domain.calendar.MonthWindow;
import iq.mizan.household.event.HouseholdCreated;
import iq.mizan.plan.PlanProperties;
import iq.mizan.plan.sync.BucketSyncTable;
import iq.mizan.plan.sync.PlanSyncTable;
import iq.mizan.sync.service.SyncWriter;

import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every new household starts with the default split (FR-PLN-02), effective from the current month. */
@Service
@AllArgsConstructor
public class DefaultPlanSeeder {

    private static final String SHARED = "SHARED";

    private final SyncWriter syncWriter;
    private final TenantSession tenantSession;
    private final PlanSyncTable planTable;
    private final BucketSyncTable bucketTable;
    private final PlanProperties properties;
    private final Clock clock;

    @EventListener
    @Transactional
    public void seed(HouseholdCreated event) {
        tenantSession.bind(event.ownerId(), event.householdId());

        UUID planId = UUID.randomUUID();
        Map<String, Object> plan = new HashMap<>();
        plan.put("visibility", SHARED);
        plan.put("effectiveFrom", MonthWindow.keyFor(LocalDate.now(clock), event.monthStartDay()));
        plan.put("effectiveTo", null);
        syncWriter.insert(planTable, planId, event.householdId(), event.ownerId(), plan);

        int order = 0;
        for (PlanProperties.DefaultBucket bucket : properties.defaultBuckets()) {
            Map<String, Object> fields = new HashMap<>();
            fields.put("visibility", SHARED);
            fields.put("planId", planId.toString());
            fields.put("name", bucket.name());
            fields.put("colour", bucket.colour());
            fields.put("shareBasisPoints", bucket.shareBasisPoints());
            fields.put("carryOver", false);
            fields.put("sortOrder", order++);
            syncWriter.insert(bucketTable, UUID.randomUUID(), event.householdId(), event.ownerId(), fields);
        }
    }
}
