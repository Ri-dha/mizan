package iq.mizan.support;

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

    public Session register(String identifier, String password, String displayName) {
        MvcTestResult result = post("/api/v1/auth/register", null,
                Map.of("identifier", identifier, "password", password, "displayName", displayName));
        JsonNode body = body(result);
        return new Session(body.get("accessToken").asString(), body.get("refreshToken").asString());
    }

    public Session refresh(String refreshToken) {
        JsonNode body = body(post("/api/v1/auth/refresh", null, Map.of("refreshToken", refreshToken)));
        return new Session(body.get("accessToken").asString(), body.get("refreshToken").asString());
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

    public JsonNode body(MvcTestResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
