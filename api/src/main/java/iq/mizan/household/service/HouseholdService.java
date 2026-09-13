package iq.mizan.household.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.HashingService;
import iq.mizan.common.security.PasswordVerifier;
import iq.mizan.household.HouseholdProperties;
import iq.mizan.household.dto.HouseholdResponse;
import iq.mizan.household.dto.InvitationPreviewResponse;
import iq.mizan.household.dto.InvitationResponse;
import iq.mizan.household.dto.MemberResponse;
import iq.mizan.household.dto.MembershipResponse;
import iq.mizan.household.dto.UpdateHouseholdRequest;
import iq.mizan.household.entity.Household;
import iq.mizan.household.entity.HouseholdInvitation;
import iq.mizan.household.entity.HouseholdRole;
import iq.mizan.household.entity.Membership;
import iq.mizan.household.entity.MembershipStatus;
import iq.mizan.household.event.HouseholdCreated;
import iq.mizan.household.mapper.HouseholdMapper;
import iq.mizan.household.repository.HouseholdInvitationRepository;
import iq.mizan.household.repository.HouseholdRepository;
import iq.mizan.household.repository.MembershipRepository;

import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class HouseholdService {

    private static final int TOKEN_BYTES = 32;

    private final HouseholdRepository householdRepository;
    private final MembershipRepository membershipRepository;
    private final HouseholdInvitationRepository invitationRepository;
    private final HouseholdMapper householdMapper;
    private final AuditService auditService;
    private final HouseholdProperties properties;
    private final ApplicationEventPublisher events;
    private final HashingService hashingService;
    private final PasswordVerifier passwordVerifier;
    private final InvitationSender invitationSender;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

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
        Membership membership = currentMembership(userId);
        return householdMapper.toSummary(household(membership.getHouseholdId()), membership);
    }

    @Transactional(readOnly = true)
    public HouseholdResponse current(UUID userId) {
        return householdMapper.toResponse(summaryFor(userId));
    }

    @Transactional
    public HouseholdResponse updateSettings(UUID userId, UpdateHouseholdRequest request) {
        Membership membership = currentMembership(userId);
        Household household = household(membership.getHouseholdId());

        Map<String, Object> before = Map.of(
                "name", household.getName(), "monthStartDay", household.getMonthStartDay());
        if (request.name() != null) {
            household.rename(request.name());
        }
        if (request.monthStartDay() != null) {
            household.changeMonthStartDay(request.monthStartDay());
        }
        audit(AuditAction.HOUSEHOLD_UPDATED, AuditEntityType.HOUSEHOLD, userId, household.getId(), household.getId(),
                before, Map.of("name", household.getName(), "monthStartDay", household.getMonthStartDay()));

        return householdMapper.toResponse(householdMapper.toSummary(household, membership));
    }

    // --- Members (FR-ACC-05, FR-ACC-06) ---

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID userId, UUID householdId) {
        return jdbc.sql("select m.user_id, u.display_name, coalesce(u.email, u.phone) as contact, m.role, m.joined_at "
                        + "from membership m join app_user u on u.id = m.user_id "
                        + "where m.household_id = ? and m.status = 'ACTIVE' order by m.joined_at")
                .param(householdId)
                .query((rs, i) -> new MemberResponse(rs.getObject("user_id", UUID.class), rs.getString("display_name"),
                        rs.getString("contact"), HouseholdRole.valueOf(rs.getString("role")),
                        rs.getTimestamp("joined_at").toInstant(), userId.equals(rs.getObject("user_id", UUID.class))))
                .list();
    }

    @Transactional
    public InvitationResponse invite(UUID userId, UUID householdId, HouseholdRole role, String contact) {
        Household household = household(householdId);
        requireNotDeleting(household);
        String token = newToken();
        HouseholdInvitation invitation = invitationRepository.save(HouseholdInvitation.issue(
                householdId, userId, role, blankToNull(contact), hashingService.hash(token), properties.invitation().ttl(), clock.instant()));
        if (invitation.getContact() != null) {
            invitationSender.send(invitation.getContact(), household.getName(), token);
        }
        audit(AuditAction.MEMBER_INVITED, AuditEntityType.INVITATION, userId, invitation.getId(), householdId,
                null, Map.of("role", role.name()));
        return new InvitationResponse(invitation.getId(), role, invitation.getContact(), invitation.getExpiresAt(), token);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> openInvitations(UUID householdId) {
        Instant now = clock.instant();
        return invitationRepository.findByHouseholdIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(householdId).stream()
                .filter(invitation -> invitation.isOpen(now))
                .map(invitation -> new InvitationResponse(invitation.getId(), invitation.getRole(), invitation.getContact(), invitation.getExpiresAt(), null))
                .toList();
    }

    @Transactional
    public void revokeInvitation(UUID userId, UUID householdId, UUID invitationId) {
        HouseholdInvitation invitation = invitationRepository.findById(invitationId)
                .filter(found -> found.getHouseholdId().equals(householdId))
                .orElseThrow(HouseholdService::invitationNotFound);
        invitation.revoke(clock.instant());
        audit(AuditAction.INVITATION_REVOKED, AuditEntityType.INVITATION, userId, invitationId, householdId, null, null);
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse preview(String token) {
        HouseholdInvitation invitation = openInvitation(token);
        String invitedBy = jdbc.sql("select display_name from app_user where id = ?").param(invitation.getInvitedBy())
                .query(String.class).optional().orElse("");
        return new InvitationPreviewResponse(household(invitation.getHouseholdId()).getName(), invitedBy, invitation.getRole(), invitation.getExpiresAt());
    }

    /** Joining makes the new household current; the user's own household stays and can be switched back to. */
    @Transactional
    public HouseholdResponse accept(UUID userId, String token) {
        HouseholdInvitation invitation = openInvitation(token);
        UUID householdId = invitation.getHouseholdId();
        Membership membership = membershipRepository.findByHouseholdIdAndUserId(householdId, userId).orElse(null);
        if (membership != null && membership.isActive()) {
            throw ApiException.of(ErrorCode.ALREADY_A_MEMBER, "You are already a member of this household");
        }
        if (membership == null) {
            membership = membershipRepository.save(Membership.join(householdId, userId, invitation.getRole()));
        } else {
            membership.reactivate(invitation.getRole());
        }
        invitation.accept(userId, clock.instant());
        makeCurrent(userId, membership);
        audit(AuditAction.INVITATION_ACCEPTED, AuditEntityType.MEMBERSHIP, userId, membership.getId(), householdId,
                null, Map.of("role", invitation.getRole().name()));
        return householdMapper.toResponse(householdMapper.toSummary(household(householdId), membership));
    }

    @Transactional
    public MemberResponse changeRole(UUID actorId, UUID householdId, UUID memberUserId, HouseholdRole role) {
        if (role == HouseholdRole.OWNER) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Use transfer-ownership to make someone the owner");
        }
        Membership membership = activeMember(householdId, memberUserId);
        if (membership.getRole() == HouseholdRole.OWNER) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "The owner's role cannot be changed");
        }
        HouseholdRole before = membership.getRole();
        membership.changeRole(role);
        membershipRepository.flush();
        audit(AuditAction.MEMBER_ROLE_CHANGED, AuditEntityType.MEMBERSHIP, actorId, membership.getId(), householdId,
                Map.of("role", before.name()), Map.of("role", role.name()));
        return members(actorId, householdId).stream().filter(m -> m.userId().equals(memberUserId)).findFirst().orElseThrow();
    }

    @Transactional
    public void removeMember(UUID actorId, UUID householdId, UUID memberUserId) {
        Membership membership = activeMember(householdId, memberUserId);
        if (membership.getRole() == HouseholdRole.OWNER) {
            throw ApiException.of(ErrorCode.OWNER_CANNOT_LEAVE, "Transfer ownership before removing the owner");
        }
        membership.remove();
        fallBackToOwnHousehold(memberUserId);
        audit(AuditAction.MEMBER_REMOVED, AuditEntityType.MEMBERSHIP, actorId, membership.getId(), householdId, null, null);
    }

    @Transactional
    public HouseholdResponse leave(UUID userId, UUID householdId) {
        Membership membership = activeMember(householdId, userId);
        if (membership.getRole() == HouseholdRole.OWNER) {
            throw ApiException.of(ErrorCode.OWNER_CANNOT_LEAVE, "Transfer ownership before leaving");
        }
        if (membershipRepository.findByUserIdAndStatusOrderByJoinedAtAsc(userId, MembershipStatus.ACTIVE).size() == 1) {
            throw ApiException.of(ErrorCode.ONLY_HOUSEHOLD, "This is your only household; you cannot leave it");
        }
        membership.remove();
        fallBackToOwnHousehold(userId);
        audit(AuditAction.MEMBER_LEFT, AuditEntityType.MEMBERSHIP, userId, membership.getId(), householdId, null, null);
        return current(userId);
    }

    @Transactional
    public void transferOwnership(UUID ownerId, UUID householdId, UUID newOwnerId) {
        Membership owner = activeMember(householdId, ownerId);
        Membership successor = activeMember(householdId, newOwnerId);
        if (owner.getRole() != HouseholdRole.OWNER || ownerId.equals(newOwnerId)) {
            throw ApiException.of(ErrorCode.ACCESS_DENIED, "Only the owner can transfer ownership to another member");
        }
        successor.changeRole(HouseholdRole.OWNER);
        owner.changeRole(HouseholdRole.MEMBER);
        audit(AuditAction.OWNERSHIP_TRANSFERRED, AuditEntityType.HOUSEHOLD, ownerId, householdId, householdId,
                Map.of("owner", ownerId.toString()), Map.of("owner", newOwnerId.toString()));
    }

    // --- Several households per user ---

    @Transactional(readOnly = true)
    public List<MembershipResponse> memberships(UUID userId) {
        return membershipRepository.findByUserIdAndStatusOrderByJoinedAtAsc(userId, MembershipStatus.ACTIVE).stream()
                .map(m -> new MembershipResponse(m.getHouseholdId(), household(m.getHouseholdId()).getName(), m.getRole(), m.isCurrent()))
                .toList();
    }

    @Transactional
    public HouseholdResponse switchTo(UUID userId, UUID householdId) {
        Membership membership = activeMember(householdId, userId);
        makeCurrent(userId, membership);
        audit(AuditAction.HOUSEHOLD_SWITCHED, AuditEntityType.MEMBERSHIP, userId, membership.getId(), householdId, null, null);
        return householdMapper.toResponse(householdMapper.toSummary(household(householdId), membership));
    }

    // --- Deletion with a grace period (FR-ACC-07) ---

    @Transactional
    public HouseholdResponse requestDeletion(UUID ownerId, UUID householdId, String password) {
        Membership owner = activeMember(householdId, ownerId);
        if (!passwordVerifier.matches(ownerId, password)) {
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "The password is incorrect");
        }
        Household household = household(householdId);
        household.requestDeletion(ownerId, clock.instant());
        audit(AuditAction.HOUSEHOLD_DELETION_REQUESTED, AuditEntityType.HOUSEHOLD, ownerId, householdId, householdId, null, null);
        return householdMapper.toResponse(householdMapper.toSummary(household, owner));
    }

    @Transactional
    public HouseholdResponse cancelDeletion(UUID ownerId, UUID householdId) {
        Membership owner = activeMember(householdId, ownerId);
        Household household = household(householdId);
        household.cancelDeletion();
        audit(AuditAction.HOUSEHOLD_DELETION_CANCELLED, AuditEntityType.HOUSEHOLD, ownerId, householdId, householdId, null, null);
        return householdMapper.toResponse(householdMapper.toSummary(household, owner));
    }

    /** Members lose the household from their list; those who had it current fall back to their own. */
    @Transactional
    public int purgeExpired() {
        Instant cutoff = clock.instant().minus(properties.deletion().gracePeriod());
        List<UUID> due = jdbc.sql("select id from household where deletion_requested_at is not null and deletion_requested_at < ?")
                .param(java.sql.Timestamp.from(cutoff)).query(UUID.class).list();
        for (UUID householdId : due) {
            List<UUID> members = membershipRepository.findByHouseholdIdAndStatusOrderByJoinedAtAsc(householdId, MembershipStatus.ACTIVE)
                    .stream().map(Membership::getUserId).toList();
            jdbc.sql("delete from household where id = ?").param(householdId).update();
            members.forEach(this::fallBackToOwnHousehold);
            auditService.record(AuditRecord.of(AuditAction.HOUSEHOLD_PURGED, AuditEntityType.HOUSEHOLD)
                    .entityId(householdId).householdId(householdId).build());
        }
        return due.size();
    }

    // --- helpers ---

    private Membership currentMembership(UUID userId) {
        return membershipRepository.findFirstByUserIdAndStatusAndCurrentTrue(userId, MembershipStatus.ACTIVE)
                .or(() -> membershipRepository.findFirstByUserIdAndStatusOrderByJoinedAtAsc(userId, MembershipStatus.ACTIVE))
                .orElseThrow(HouseholdService::notFound);
    }

    private Membership activeMember(UUID householdId, UUID userId) {
        return membershipRepository.findByHouseholdIdAndUserId(householdId, userId)
                .filter(Membership::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.MEMBER_NOT_FOUND, "No such member in this household"));
    }

    private void makeCurrent(UUID userId, Membership target) {
        membershipRepository.findByUserIdAndStatusOrderByJoinedAtAsc(userId, MembershipStatus.ACTIVE)
                .forEach(m -> m.makeCurrent(m.getId().equals(target.getId())));
    }

    private void fallBackToOwnHousehold(UUID userId) {
        List<Membership> remaining = membershipRepository.findByUserIdAndStatusOrderByJoinedAtAsc(userId, MembershipStatus.ACTIVE);
        if (remaining.stream().noneMatch(Membership::isCurrent) && !remaining.isEmpty()) {
            remaining.getFirst().makeCurrent(true);
        }
    }

    private HouseholdInvitation openInvitation(String token) {
        HouseholdInvitation invitation = invitationRepository.findByTokenHash(hashingService.hash(token))
                .orElseThrow(HouseholdService::invitationNotFound);
        Instant now = clock.instant();
        if (invitation.isExpired(now)) {
            throw ApiException.of(ErrorCode.INVITATION_EXPIRED, "This invitation has expired; ask for a new one");
        }
        if (!invitation.isOpen(now)) {
            throw invitationNotFound();
        }
        return invitation;
    }

    private Household household(UUID householdId) {
        return householdRepository.findById(householdId).orElseThrow(HouseholdService::notFound);
    }

    private static void requireNotDeleting(Household household) {
        if (household.getDeletionRequestedAt() != null) {
            throw ApiException.of(ErrorCode.HOUSEHOLD_DELETION_PENDING, "This household is scheduled for deletion");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(AuditAction action, AuditEntityType type, UUID actorId, UUID entityId, UUID householdId,
                       Map<String, Object> before, Map<String, Object> after) {
        auditService.record(AuditRecord.of(action, type).actorId(actorId).entityId(entityId).householdId(householdId)
                .before(before).after(after).build());
    }

    private static ApiException notFound() {
        return ApiException.of(ErrorCode.HOUSEHOLD_NOT_FOUND, "No household for this account");
    }

    private static ApiException invitationNotFound() {
        return ApiException.of(ErrorCode.INVITATION_NOT_FOUND, "This invitation link is not valid");
    }
}
