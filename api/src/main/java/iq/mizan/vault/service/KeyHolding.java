package iq.mizan.vault.service;

import iq.mizan.common.security.Permission;
import iq.mizan.household.service.MemberAccess;
import iq.mizan.vault.entity.KeyKind;

import org.jspecify.annotations.Nullable;

/** Members and owners hold both shared keys, advisors only the report key, dependents neither. */
final class KeyHolding {

    private KeyHolding() {
    }

    static Permission permissionToHold(KeyKind kind) {
        return kind == KeyKind.HOUSEHOLD ? Permission.HOUSEHOLD_VIEW : Permission.REPORTS_VIEW;
    }

    static boolean mayHold(@Nullable MemberAccess member, KeyKind kind) {
        return member != null && member.permissions().contains(permissionToHold(kind));
    }
}
