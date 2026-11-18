package iq.mizan.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * ADR storage-4: the API signs links for the public address, Caddy forwards them to storage with
 * the Host header untouched, and SeaweedFS accepts the signature. Storage checks signatures here,
 * so a link signed for the wrong address is refused.
 */
class PublicStorageLinkTest {

    private static final String BUCKET = "mizan-attachments";
    private static final String KEY = "receipts/h/a";
    private static final String ACCESS = "mizan";
    private static final String SECRET = "test-secret";
    private static final int S3_PORT = 8333;
    private static final int PROXY_PORT = 80;
    private static final String IDENTITIES = "{\"identities\":[{\"name\":\"mizan\",\"credentials\":[{\"accessKey\":\"" + ACCESS
            + "\",\"secretKey\":\"" + SECRET + "\"}],\"actions\":[\"Admin\",\"Read\",\"Write\",\"List\"]}]}";

    private static final Network network = Network.newNetwork();
    private static GenericContainer<?> proxy;
    private static GenericContainer<?> seaweed;
    private static StorageProperties storage;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() {
        proxy = new GenericContainer<>("caddy:2-alpine")
                .withNetwork(network)
                .withCommand("caddy", "reverse-proxy", "--from", ":" + PROXY_PORT, "--to", "seaweedfs:" + S3_PORT)
                .withExposedPorts(PROXY_PORT);
        proxy.start();
        String publicEndpoint = "http://" + proxy.getHost() + ":" + proxy.getMappedPort(PROXY_PORT);
        seaweed = new GenericContainer<>("chrislusf/seaweedfs:4.51")
                .withNetwork(network)
                .withNetworkAliases("seaweedfs")
                .withEnv("S3_EXTERNAL_URL", publicEndpoint)
                .withCopyToContainer(Transferable.of(IDENTITIES), "/etc/s3.json")
                .withCommand("server", "-dir=/data", "-s3", "-s3.config=/etc/s3.json", "-s3.port.iceberg=0", "-s3.port.lance=0")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT).withStartupTimeout(Duration.ofMinutes(2)));
        seaweed.start();
        String internalEndpoint = "http://" + seaweed.getHost() + ":" + seaweed.getMappedPort(S3_PORT);
        storage = new StorageProperties(BUCKET, internalEndpoint, publicEndpoint, "us-east-1", ACCESS, SECRET, true, false,
                Duration.ofMinutes(15), Duration.ofDays(30));
        try (S3Client s3 = new S3Config().s3Client(storage)) {
            s3.createBucket(b -> b.bucket(BUCKET));
        }
    }

    @AfterAll
    static void stop() {
        seaweed.stop();
        proxy.stop();
        network.close();
    }

    @Test
    void aLinkForThePublicAddressUploadsAndDownloadsThroughTheProxy() throws Exception {
        try (S3Presigner presigner = new S3Config().s3Presigner(storage); S3Client s3 = new S3Config().s3Client(storage)) {
            URI upload = URI.create(presigner.presignPutObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                    .putObjectRequest(o -> o.bucket(BUCKET).key(KEY).contentType("application/octet-stream"))).url().toString());
            assertThat(upload.toString()).startsWith(storage.publicEndpoint() + "/" + BUCKET + "/");

            HttpResponse<String> put = http.send(HttpRequest.newBuilder(upload).header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofString("sealed")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(put.statusCode()).as(put.body()).isEqualTo(200);
            assertThat(s3.getObjectAsBytes(b -> b.bucket(BUCKET).key(KEY)).asUtf8String()).isEqualTo("sealed");

            URI download = URI.create(presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                    .getObjectRequest(o -> o.bucket(BUCKET).key(KEY))).url().toString());
            HttpResponse<String> get = http.send(HttpRequest.newBuilder(download).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(get.statusCode()).isEqualTo(200);
            assertThat(get.body()).isEqualTo("sealed");
        }
    }

    @Test
    void aLinkWithItsSignatureAlteredIsRefused() throws Exception {
        try (S3Presigner presigner = new S3Config().s3Presigner(storage)) {
            String signed = presigner.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(5))
                    .getObjectRequest(o -> o.bucket(BUCKET).key(KEY))).url().toString();
            URI tampered = URI.create(signed.replaceAll("X-Amz-Signature=[0-9a-f]{64}", "X-Amz-Signature=" + "0".repeat(64)));
            HttpResponse<String> get = http.send(HttpRequest.newBuilder(tampered).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(get.statusCode()).isEqualTo(403);
        }
    }

    @Test
    void withoutAPublicAddressLinksUseTheInternalOne() {
        StorageProperties local = new StorageProperties(BUCKET, "http://localhost:8333", "", "us-east-1", ACCESS, SECRET, true, false,
                Duration.ofMinutes(15), Duration.ofDays(30));
        assertThat(local.linkEndpoint()).isEqualTo("http://localhost:8333");
        assertThat(storage.linkEndpoint()).isEqualTo(storage.publicEndpoint());
    }
}
