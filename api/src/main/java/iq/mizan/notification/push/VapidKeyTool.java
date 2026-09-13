package iq.mizan.notification.push;

/** Prints a VAPID pair for .env; see docs/RUNBOOK.md. */
public final class VapidKeyTool {

    private VapidKeyTool() {
    }

    public static void main(String[] args) {
        VapidKeys.Pair pair = VapidKeys.generate();
        System.out.println("MIZAN_VAPID_PUBLIC_KEY=" + pair.publicKey());
        System.out.println("MIZAN_VAPID_PRIVATE_KEY=" + pair.privateKey());
    }
}
