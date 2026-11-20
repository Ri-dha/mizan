package iq.mizan.notification.service;

import iq.mizan.notification.push.PushMessage;

import org.springframework.stereotype.Component;

/**
 * The wording of each reminder in the member's language. Under end-to-end encryption the server
 * never knows which bill, which source or how much, so the text is generic and the app shows the
 * details once it is opened and unlocked (ADR e2e-1).
 */
@Component
public class NotificationMessages {

    public PushMessage reminder(String locale, ReminderKind kind) {
        boolean arabic = arabic(locale);
        return switch (kind) {
            case BILL_DUE -> arabic
                    ? new PushMessage(kind.name(), "فاتورة مستحقة", "لديك فاتورة مستحقة. افتح ميزان لرؤيتها.", "/bills")
                    : new PushMessage(kind.name(), "Bill due", "A bill is due. Open Mizan to see it.", "/bills");
            case PAY_DAY -> arabic
                    ? new PushMessage(kind.name(), "يوم الراتب", "دخل متوقع اليوم. علّمه كمستلم لتوزيعه.", "/income")
                    : new PushMessage(kind.name(), "Pay day", "Income is expected today. Mark it received to allocate it.", "/income");
            case OVERSPEND -> arabic
                    ? new PushMessage(kind.name(), "اقتربت من الحد", "أحد أقسامك قارب حده هذا الشهر.", "/plan")
                    : new PushMessage(kind.name(), "Close to the limit", "One of your buckets is close to its limit this month.", "/plan");
            case MONTH_CLOSE -> arabic
                    ? new PushMessage(kind.name(), "ينتهي الشهر غداً", "راجع شهرك قبل إقفاله.", "/networth")
                    : new PushMessage(kind.name(), "The month ends tomorrow", "Review your month before it closes.", "/networth");
        };
    }

    /** Public market data, so the move itself can be named. */
    public PushMessage metalMove(String locale, String metal, double percent) {
        String direction = percent >= 0 ? (arabic(locale) ? "ارتفع" : "rose") : (arabic(locale) ? "انخفض" : "fell");
        String metalName = arabic(locale) ? ("GOLD".equals(metal) ? "الذهب" : "الفضة") : ("GOLD".equals(metal) ? "Gold" : "Silver");
        return arabic(locale)
                ? new PushMessage("METAL_PRICE", "تحرك السعر", "%s %s بنسبة %.1f%% منذ الأمس.".formatted(metalName, direction, Math.abs(percent)), "/metals")
                : new PushMessage("METAL_PRICE", "Price move", "%s %s %.1f%% since yesterday.".formatted(metalName, direction, Math.abs(percent)), "/metals");
    }

    public PushMessage test(String locale) {
        return arabic(locale)
                ? new PushMessage("TEST", "ميزان", "الإشعارات تعمل على هذا الجهاز.", "/settings")
                : new PushMessage("TEST", "Mizan", "Notifications work on this device.", "/settings");
    }

    public PushMessage supportReply(String locale, java.util.UUID reportId) {
        String url = "/support/" + reportId;
        return arabic(locale)
                ? new PushMessage("SUPPORT_REPLY", "رد من فريق ميزان", "وصل رد على بلاغك.", url)
                : new PushMessage("SUPPORT_REPLY", "Mizan support", "There is a reply to your report.", url);
    }

    private static boolean arabic(String locale) {
        return locale != null && locale.startsWith("ar");
    }
}
