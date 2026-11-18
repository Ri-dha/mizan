package iq.mizan.vault.dto;

import iq.mizan.vault.entity.KeyKind;

/** Whether a device holding this key should rotate it now (ADR e2e-4). */
public record KeyStateResponse(KeyKind keyKind, int currentVersion, boolean rotationDue) {
}
