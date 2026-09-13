package iq.mizan.notification.push;

/** What a device shows: the service worker reads these fields and opens {@code url} on tap. */
public record PushMessage(String kind, String title, String body, String url) {
}
