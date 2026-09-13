package iq.mizan.notification.service;

import iq.mizan.notification.NotificationProperties;

/** FR-NTF-06: a member's switches, with the configured defaults when they never changed anything. */
public record NotificationSettings(boolean billDue, int billLeadDays, boolean payDay, boolean overspend, int overspendThresholdBp,
                                   boolean monthClose, boolean metalPrice, int metalMoveBp, boolean quietMode) {

    public static NotificationSettings defaults(NotificationProperties.Defaults defaults) {
        return new NotificationSettings(true, defaults.billLeadDays(), true, true, defaults.overspendThresholdBasisPoints(),
                true, false, defaults.metalMoveBasisPoints(), false);
    }
}
