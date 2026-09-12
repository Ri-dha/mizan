package iq.mizan.household.service;

import java.util.Map;
import java.util.UUID;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.household.HouseholdProperties;
import iq.mizan.household.dto.HouseholdResponse;
import iq.mizan.household.dto.UpdateHouseholdRequest;
import iq.mizan.household.entity.Household;
import iq.mizan.household.entity.Membership;
import iq.mizan.household.entity.MembershipStatus;
import iq.mizan.household.event.HouseholdCreated;
import iq.mizan.household.mapper.HouseholdMapper;
import iq.mizan.household.repository.HouseholdRepository;
import iq.mizan.household.repository.MembershipRepository;

import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdMapper householdMapper;
    private final AuditService auditService;
    private final HouseholdProperties properties;
    private final ApplicationEventPublisher events;

    /** FR-ACC-04: every account starts as Owner of its own household. */
    @Transactional
    public HouseholdSummary createForOwner(UUID userId, String ownerDisplayName) {
        Household household = householdRepository.saveAndFlush(Household.create(
                ownerDisplayName, properties.defaultCurrency(), properties.defaultMonthStartDay()));
        Membership membership = membershipRepository.save(Membership.owner(household.getId(), userId));
        events.publishEvent(new HouseholdCreated(household.getId(), userId, household.getMonthStartDay()));
        return householdMapper.toSummary(household, membership);
    }

    @Transactional(readOnly = true)
    public HouseholdSummary summaryFor(UUID userId) {
        Membership membership = activeMembership(userId);
        Household household = householdRepository.findById(membership.getHouseholdId())
                .orElseThrow(HouseholdService::notFound);
        return householdMapper.toSummary(household, membership);
    }

    @Transactional(readOnly = true)
    public HouseholdResponse current(UUID userId) {
        return householdMapper.toResponse(summaryFor(userId));
    }

    @Transactional
    public HouseholdResponse updateSettings(UUID userId, UpdateHouseholdRequest request) {
        Membership membership = activeMembership(userId);
        Household household = householdRepository.findById(membership.getHouseholdId())
                .orElseThrow(HouseholdService::notFound);

        Map<String, Object> before = Map.of(
                "name", household.getName(), "monthStartDay", household.getMonthStartDay());
        if (request.name() != null) {
            household.rename(request.name());
        }
        if (request.monthStartDay() != null) {
            household.changeMonthStartDay(request.monthStartDay());
        }
        auditService.record(AuditRecord.of(AuditAction.HOUSEHOLD_UPDATED, AuditEntityType.HOUSEHOLD)
                .actorId(userId)
                .entityId(household.getId())
                .householdId(household.getId())
                .before(before)
                .after(Map.of("name", household.getName(), "monthStartDay", household.getMonthStartDay()))
                .build());

        return householdMapper.toResponse(householdMapper.toSummary(household, membership));
    }

    private Membership activeMembership(UUID userId) {
        return membershipRepository
                .findFirstByUserIdAndStatusOrderByJoinedAtAsc(userId, MembershipStatus.ACTIVE)
                .orElseThrow(HouseholdService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.of(ErrorCode.HOUSEHOLD_NOT_FOUND, "No household for this account");
    }
}
