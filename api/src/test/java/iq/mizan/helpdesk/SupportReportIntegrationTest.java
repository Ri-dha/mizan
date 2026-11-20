package iq.mizan.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.JsonNode;

/** ADR support-1: a person and the operators talk about a report; nobody else can see it. */
class SupportReportIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private S3Client s3;

    private final Map<String, byte[]> stored = new ConcurrentHashMap<>();
    private String reporter;
    private String operator;
    private String stranger;

    @BeforeEach
    void accountsAndStore() throws IOException {
        resetTransactionalData();
        stored.clear();
        doAnswer(call -> {
            try (InputStream in = call.getArgument(1, RequestBody.class).contentStreamProvider().newStream()) {
                stored.put(call.getArgument(0, PutObjectRequest.class).key(), in.readAllBytes());
            }
            return null;
        }).when(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        doAnswer(call -> ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),
                stored.get(call.getArgument(0, GetObjectRequest.class).key()))).when(s3).getObjectAsBytes(any(GetObjectRequest.class));
        doAnswer(call -> {
            call.getArgument(0, DeleteObjectsRequest.class).delete().objects().forEach(o -> stored.remove(o.key()));
            return null;
        }).when(s3).deleteObjects(any(DeleteObjectsRequest.class));

        operator = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        jdbc.update("update app_user set platform_role = 'OPERATOR' where email = ?", accounts.ownerEmail());
        reporter = api.register(accounts.otherEmail(), accounts.otherPassword(), "Noor").accessToken();
        stranger = api.register(accounts.thirdEmail(), accounts.thirdPassword(), "Sara").accessToken();
    }

    private MvcTestResult send(String token, String path, Map<String, String> fields, MockMultipartFile... images) {
        var request = mvc.post().uri(path).multipart().header("Authorization", "Bearer " + token);
        fields.forEach(request::param);
        for (MockMultipartFile image : images) {
            request.file(image);
        }
        return request.exchange();
    }

    private static MockMultipartFile image(String name, byte[] bytes) {
        return new MockMultipartFile("images", name, "image/jpeg", bytes);
    }

    @Test
    void aReportWithImagesReachesOperatorsWithoutTheLocationAndTheyTalk() throws IOException {
        MvcTestResult created = send(reporter, "/api/v1/support/reports",
                Map.of("category", "PROBLEM", "subject", "Sync stuck", "body", "It spins forever", "platform", "web"),
                image("a.jpg", iq.mizan.helpdesk.service.ReportImagesTestAccess.jpegWithLocation()),
                image("b.png", iq.mizan.helpdesk.service.ReportImagesTestAccess.pngWithText()));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        JsonNode report = api.body(created);
        String id = report.get("id").asString();
        JsonNode images = report.get("messages").get(0).get("attachments");
        assertThat(images).hasSize(2);
        assertThat(report.get("reporter").isNull()).isTrue();
        assertThat(stored.values()).allSatisfy(bytes -> assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).doesNotContain("GPS"));

        String imageId = images.get(0).get("id").asString();
        MvcTestResult own = api.get("/api/v1/support/reports/" + id + "/images/" + imageId, reporter);
        assertThat(own).hasStatus(HttpStatus.OK).hasContentType("image/jpeg");
        assertThat(own.getResponse().getHeader("Content-Security-Policy")).contains("sandbox");
        assertThat(api.get("/api/v1/support/reports/" + id, stranger)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.get("/api/v1/support/reports/" + id + "/images/" + imageId, stranger)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.get("/api/v1/admin/support/reports", reporter)).hasStatus(HttpStatus.FORBIDDEN);

        JsonNode queue = api.body(api.get("/api/v1/admin/support/reports", operator));
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).get("unread").asBoolean()).isTrue();
        assertThat(queue.get(0).get("reporter").get("email").asString()).isEqualTo(accounts.otherEmail());
        assertThat(api.get("/api/v1/admin/support/reports/" + id + "/images/" + imageId, operator)).hasStatus(HttpStatus.OK);

        assertThat(send(operator, "/api/v1/admin/support/reports/" + id + "/messages", Map.of("body", "Which version?")))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.post("/api/v1/admin/session", operator, Map.of("authKey", ApiClient.authKey(accounts.ownerPassword()))))
                .hasStatus(HttpStatus.OK);
        JsonNode answered = api.body(send(operator, "/api/v1/admin/support/reports/" + id + "/messages", Map.of("body", "Which version?")));
        assertThat(answered.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(answered.get("messages").get(1).get("authorName").asString()).isEqualTo("Ridha");

        JsonNode mine = api.body(api.get("/api/v1/support/reports", reporter));
        assertThat(mine.get(0).get("unread").asBoolean()).isTrue();
        JsonNode seen = api.body(api.get("/api/v1/support/reports/" + id, reporter));
        assertThat(seen.get("messages").get(1).get("authorName").isNull()).isTrue();
        assertThat(api.body(api.get("/api/v1/support/reports", reporter)).get(0).get("unread").asBoolean()).isFalse();

        assertThat(api.put("/api/v1/admin/support/reports/" + id + "/status", operator, Map.of("status", "RESOLVED"))).hasStatus(HttpStatus.OK);
        JsonNode reopened = api.body(send(reporter, "/api/v1/support/reports/" + id + "/messages", Map.of("body", "Still broken")));
        assertThat(reopened.get("status").asString()).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select count(*) from audit_entry where action like 'SUPPORT_%'", Long.class)).isEqualTo(2);
    }

    @Test
    void badInputIsRefusedAndReportsAreLimitedPerDay() {
        assertThat(send(reporter, "/api/v1/support/reports", Map.of("category", "PROBLEM", "subject", "x", "body", "y"),
                image("evil.jpg", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(send(reporter, "/api/v1/support/reports", Map.of("category", "PROBLEM", "subject", " ", "body", "y")))
                .hasStatus(HttpStatus.BAD_REQUEST);
        for (int i = 0; i < 5; i++) {
            assertThat(send(reporter, "/api/v1/support/reports", Map.of("category", "QUESTION", "subject", "Q" + i, "body", "?")))
                    .hasStatus(HttpStatus.CREATED);
        }
        assertThat(send(reporter, "/api/v1/support/reports", Map.of("category", "QUESTION", "subject", "Q6", "body", "?")))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(send(reporter, "/api/v1/support/reports", Map.of("category", "PROBLEM", "subject", "Big", "body", "y"),
                image("big.jpg", new byte[6 * 1024 * 1024]))).hasStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(send(reporter, "/api/v1/support/reports", Map.of("category", "PROBLEM", "body", "y"))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(stored).isEmpty();
    }
}
