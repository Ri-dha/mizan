package iq.mizan.notification.service;

import java.text.NumberFormat;
import java.util.Locale;

import iq.mizan.notification.push.PushMessage;

import org.springframework.stereotype.Component;

/** The wording of each reminder in the member's language; amounts are whole base units. */
@Component
public class NotificationMessages {

    private static final Locale ARABIC = Locale.forLanguageTag("ar-IQ");

    public PushMessage billDue(String locale, String name, long amount, String currency, int daysAway) {
        String money = money(locale, amount, currency);
        return arabic(locale)
                ? new PushMessage("BILL_DUE", "فاتورة مستحقة", daysAway == 0 ? "%s (%s) تستحق اليوم".formatted(name, money)
                        : "%s (%s) تستحق خلال %d أيام".formatted(name, money, daysAway), "/bills")
                : new PushMessage("BILL_DUE", "Bill due", daysAway == 0 ? "%s (%s) is due today".formatted(name, money)
                        : "%s (%s) is due in %d days".formatted(name, money, daysAway), "/bills");
    }

    public PushMessage payDay(String locale, String source, long amount, String currency) {
        String money = money(locale, amount, currency);
        return arabic(locale)
                ? new PushMessage("PAY_DAY", "يوم الراتب", "%s (%s) متوقع اليوم. علّمه كمستلم لتوزيعه.".formatted(source, money), "/income")
                : new PushMessage("PAY_DAY", "Pay day", "%s (%s) is expected today. Mark it received to allocate it.".formatted(source, money), "/income");
    }

    public PushMessage overspend(String locale, String bucket, int percent) {
        return arabic(locale)
                ? new PushMessage("OVERSPEND", "اقتربت من الحد", "أنفقت %d%% من %s هذا الشهر.".formatted(percent, bucket), "/plan")
                : new PushMessage("OVERSPEND", "Close to the limit", "You have spent %d%% of %s this month.".formatted(percent, bucket), "/plan");
    }

    public PushMessage monthClose(String locale, String monthKey) {
        return arabic(locale)
                ? new PushMessage("MONTH_CLOSE", "ينتهي الشهر غداً", "راجع شهر %s قبل إقفاله.".formatted(monthKey), "/networth")
                : new PushMessage("MONTH_CLOSE", "The month ends tomorrow", "Review %s before it closes.".formatted(monthKey), "/networth");
    }

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

    private static boolean arabic(String locale) {
        return locale != null && locale.startsWith("ar");
    }

    private static String money(String locale, long amount, String currency) {
        NumberFormat format = NumberFormat.getIntegerInstance(arabic(locale) ? ARABIC : Locale.UK);
        long whole = "IQD".equals(currency) ? amount : amount / 100;
        return format.format(whole) + " " + currency;
    }
}
