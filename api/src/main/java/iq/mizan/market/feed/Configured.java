package iq.mizan.market.feed;

/** An env override left empty registers the bean but must not register the provider; feeds check this first. */
final class Configured {

    private Configured() {
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
