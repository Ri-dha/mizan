package iq.mizan.vault.dto;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** What a granting device needs about each member: their public key and which keys they already hold. */
public record KeyMemberResponse(UUID userId, String displayName, String role, boolean mayHoldHouseholdKey,
                                boolean mayHoldReportKey, @Nullable String publicKey, List<HeldKey> holds) {

    public record HeldKey(String keyKind, int keyVersion) {
    }
}
