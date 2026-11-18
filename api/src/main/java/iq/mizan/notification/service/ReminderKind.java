package iq.mizan.notification.service;

/** The reminders a device may schedule; each has fixed wording because the server knows no details. */
public enum ReminderKind {
    BILL_DUE,
    PAY_DAY,
    OVERSPEND,
    MONTH_CLOSE
}
