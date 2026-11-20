package iq.mizan.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

class OpenApiDocumentTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    private JsonNode schemas;

    @BeforeEach
    void loadDocument() {
        MvcTestResult result = api.get("/v3/api-docs", null);
        assertThat(result).hasStatus(HttpStatus.OK);
        schemas = api.body(result).path("components").path("schemas");
    }

    @Test
    void responseFieldsTheServerAlwaysSendsAreRequired() {
        assertThat(required("SyncPushResponse")).containsExactlyInAnyOrder("applied", "conflicts", "rejected");
        assertThat(required("RejectedOpResponse")).containsExactlyInAnyOrder("opId", "code", "detail");
        assertThat(required("ConflictResponse")).containsExactlyInAnyOrder(
                "id", "table", "rowId", "field", "clientClock", "serverClock", "detectedAt");
        assertThat(required("DeviceResponse")).containsExactlyInAnyOrder("id", "name", "lastPullSeq", "lastSeenAt");
        assertThat(required("HeldKey")).containsExactlyInAnyOrder("keyKind", "keyVersion");
    }

    @Test
    void nullableResponseFieldsStayOptional() {
        assertThat(required("ConflictResponse")).doesNotContain("clientValue", "serverValue");
        assertThat(required("MyKeysResponse")).containsExactly("grants");
        assertThat(required("CurrentUserResponse")).doesNotContain("email", "phone", "deletionRequestedAt");
    }

    @Test
    void requestFieldsKeepTheirValidationRules() {
        assertThat(required("RegisterDeviceRequest")).containsExactly("name");
        assertThat(required("RefreshRequest")).isEmpty();
    }

    private List<String> required(String schema) {
        List<String> names = new ArrayList<>();
        schemas.path(schema).path("required").forEach(name -> names.add(name.asString()));
        return names;
    }
}
