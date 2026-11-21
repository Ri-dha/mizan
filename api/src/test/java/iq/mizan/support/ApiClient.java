package iq.mizan.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Drives the API the way a client does, so tests exercise filters, security and JSON too. */
@Component
public class ApiClient {

    private final MockMvcTester mvc;
    private final JsonMapper json;

    public ApiClient(MockMvcTester mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public record Session(String accessToken, String refreshToken) {
    }

    /**
     * Stands in for the device's HKDF output (ADR e2e-1): deterministic per password, the same
     * shape, and opaque to the server, which is all the server can ever check.
     */
    public static String authKey(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("test-auth|" + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Wrapped key material as a device would upload it; the server stores it without opening it. */
    public static Map<String, String> accountKeys(String owner) {
        return Map.of("wrappedByPassword", "wrapped-by-password-" + owner, "wrappedByRecovery", "wrapped-by-recovery-" + owner,
                "publicKey", "public-key-" + owner, "encryptedPrivateKey", "encrypted-private-key-" + owner);
    }

    /** Test ciphertext under the household key: the right shape, unreadable to the server either way. */
    public static String sealed(Object value) {
        return seal("h", value);
    }

    /** Test ciphertext under the owner's user key, as private records carry. */
    public static String sealedPrivate(Object value) {
        return seal("u", value);
    }

    private static String seal(String key, Object value) {
        String padded = ("iv-and-ciphertext|" + value + "|").repeat(2);
        return "e1." + key + ".1." + Base64.getUrlEncoder().withoutPadding().encodeToString(padded.getBytes(StandardCharsets.UTF_8));
    }

    public MvcTestResult login(String identifier, String password) {
        return post("/api/v1/auth/login", null, Map.of("identifier", identifier, "authKey", authKey(password)));
    }

    /** Registers with a country and a household currency, as the current apps do. */
    public MvcTestResult registerIn(String identifier, String password, String displayName, String country, String baseCurrency) {
        java.util.Map<String, Object> body = new java.util.HashMap<>(Map.of("identifier", identifier, "authKey", authKey(password),
                "displayName", displayName, "keys", accountKeys(identifier), "country", country));
        if (baseCurrency != null) {
            body.put("baseCurrency", baseCurrency);
        }
        return post("/api/v1/auth/register", null, body);
    }

    public Session register(String identifier, String password, String displayName) {
        MvcTestResult result = post("/api/v1/auth/register", null, Map.of("identifier", identifier, "authKey", authKey(password),
                "displayName", displayName, "keys", accountKeys(identifier)));
        JsonNode body = body(result);
        return new Session(body.get("accessToken").asString(), body.get("refreshToken").asString());
    }

    public Session refresh(String refreshToken) {
        JsonNode body = body(post("/api/v1/auth/refresh", null, Map.of("refreshToken", refreshToken)));
        return new Session(body.get("accessToken").asString(), body.get("refreshToken").asString());
    }

    public MvcTestResult postWithHeaders(String path, Object body, Map<String, String> headers) {
        var request = mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        for (var header : headers.entrySet()) {
            request = request.header(header.getKey(), header.getValue());
        }
        return request.exchange();
    }

    public MvcTestResult post(String path, String accessToken, Object body) {
        var request = mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request = request.content(json.writeValueAsString(body));
        }
        if (accessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return request.exchange();
    }

    public MvcTestResult put(String path, String accessToken, Object body) {
        return mvc.put().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();
    }

    public MvcTestResult patch(String path, String accessToken, Object body) {
        return mvc.patch().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();
    }

    public MvcTestResult delete(String path, String accessToken, Object body) {
        return mvc.delete().uri(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange();
    }

    public MvcTestResult delete(String path, String accessToken) {
        return mvc.delete().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).exchange();
    }

    public MvcTestResult get(String path, String accessToken) {
        var request = mvc.get().uri(path);
        if (accessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return request.exchange();
    }

    public MvcTestResult get(String path, String accessToken, Map<String, String> headers) {
        var request = mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request = request.header(header.getKey(), header.getValue());
        }
        return request.exchange();
    }

    public JsonNode body(MvcTestResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
