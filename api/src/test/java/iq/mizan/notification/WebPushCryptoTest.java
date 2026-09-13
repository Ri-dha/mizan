package iq.mizan.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;

import iq.mizan.notification.push.VapidKeys;
import iq.mizan.notification.push.WebPushCrypto;
import iq.mizan.notification.push.WebPushSender;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** RFC 8291 and RFC 8292 without a push service: the subscriber's keys decrypt what the sender encrypts. */
class WebPushCryptoTest {

    @Test
    void aMessageRoundTripsThroughTheSubscribersKeys() throws Exception {
        KeyPair subscriber = p256();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        byte[] plaintext = "{\"title\":\"Bill due\",\"body\":\"Rent is due in 3 days\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(plaintext, (ECPublicKey) subscriber.getPublic(), auth);

        assertThat(body.length).isEqualTo(16 + 4 + 1 + 65 + plaintext.length + 1 + 16);
        byte[] decrypted = decrypt(body, subscriber, auth);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void rawKeysRoundTripAndTheVapidTokenVerifiesWithThePublicKey() {
        VapidKeys.Pair pair = VapidKeys.generate();
        ECPublicKey publicKey = VapidKeys.decodePublic(pair.publicKey());
        assertThat(VapidKeys.encodePublic(publicKey)).isEqualTo(pair.publicKey());
        assertThat(VapidKeys.encodePrivate(VapidKeys.decodePrivate(pair.privateKey()))).isEqualTo(pair.privateKey());

        NotificationProperties properties = new NotificationProperties(true, "webpush", "-", "UTC", Duration.ofHours(24),
                new NotificationProperties.Vapid("mailto:test@mizan.iq", pair.publicKey(), pair.privateKey()),
                new NotificationProperties.Defaults(3, 9000, 200));
        WebPushSender sender = new WebPushSender(RestClient.builder(), JsonMapper.builder().build(), properties, Clock.systemUTC());

        String token = tokenOf(sender, "https://fcm.googleapis.com/fcm/send/abc123");
        Claims claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getAudience()).containsExactly("https://fcm.googleapis.com");
        assertThat(claims.getSubject()).isEqualTo("mailto:test@mizan.iq");
        assertThat(sender.publicKey()).isEqualTo(pair.publicKey());
    }

    private static String tokenOf(WebPushSender sender, String endpoint) {
        try {
            var method = WebPushSender.class.getDeclaredMethod("vapidToken", String.class);
            method.setAccessible(true);
            return (String) method.invoke(sender, endpoint);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] decrypt(byte[] body, KeyPair subscriber, byte[] auth) throws Exception {
        var method = WebPushCrypto.class.getDeclaredMethod("decrypt", byte[].class, ECPrivateKey.class, ECPublicKey.class, byte[].class);
        method.setAccessible(true);
        return (byte[]) method.invoke(null, body, subscriber.getPrivate(), subscriber.getPublic(), auth);
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
}
