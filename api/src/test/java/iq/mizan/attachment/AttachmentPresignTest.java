package iq.mizan.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

class AttachmentPresignTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @BeforeEach
    void reset() {
        resetTransactionalData();
    }

    @Test
    void aSyncedAttachmentGetsUploadAndDownloadUrlsScopedToItsHousehold() {
        String owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        String device = api.body(api.post("/api/v1/sync/devices", owner, Map.of("name", "Phone"))).get("id").asString();
        UUID attachmentId = UUID.randomUUID();
        String clock = "1700000000000:0000:test";
        MvcTestResult push = api.post("/api/v1/sync/push", owner, Map.of("deviceId", device, "ops", List.of(Map.of(
                "opId", UUID.randomUUID(), "table", "attachment", "rowId", attachmentId,
                "fields", Map.of("ownerType", "TRANSACTION", "ownerRecordId", UUID.randomUUID(),
                        "mimeType", "image/jpeg", "byteSize", 1234, "iv", "abc="),
                "clocks", Map.of("ownerType", clock, "ownerRecordId", clock, "mimeType", clock, "byteSize", clock, "iv", clock)))));
        assertThat(push).hasStatus(HttpStatus.OK);

        JsonNode upload = api.body(api.post("/api/v1/attachments/" + attachmentId + "/upload-url", owner, null));
        assertThat(upload.get("method").asString()).isEqualTo("PUT");
        assertThat(upload.get("url").asString()).contains("/mizan-attachments/").contains(attachmentId.toString());

        JsonNode download = api.body(api.get("/api/v1/attachments/" + attachmentId + "/download-url", owner));
        assertThat(download.get("method").asString()).isEqualTo("GET");

        String other = api.register(accounts.otherEmail(), accounts.otherPassword(), "Neighbour").accessToken();
        assertThat(api.post("/api/v1/attachments/" + attachmentId + "/upload-url", other, null)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.post("/api/v1/attachments/" + UUID.randomUUID() + "/upload-url", owner, null)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
