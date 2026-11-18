package iq.mizan.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import iq.mizan.attachment.dto.AttachmentKind;
import iq.mizan.attachment.job.ReceiptPhotoExpiry;
import iq.mizan.common.jobs.JobLock;
import iq.mizan.common.storage.StorageProperties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

/** Against the store the deployment runs (ADR storage-1); with no identities it accepts any key. */
class ReceiptPhotoExpiryTest {

    private static final String BUCKET = "mizan-attachments";
    private static final int S3_PORT = 8333;

    private static GenericContainer<?> seaweed;
    private static S3Client s3;

    @BeforeAll
    static void start() {
        seaweed = new GenericContainer<>("chrislusf/seaweedfs:4.51")
                .withCommand("server", "-dir=/data", "-s3", "-s3.port.iceberg=0", "-s3.port.lance=0")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forHttp("/healthz").forPort(S3_PORT).withStartupTimeout(Duration.ofMinutes(2)));
        seaweed.start();
        s3 = S3Client.builder()
                .endpointOverride(URI.create("http://" + seaweed.getHost() + ":" + seaweed.getMappedPort(S3_PORT)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true)
                .build();
        s3.createBucket(b -> b.bucket(BUCKET));
    }

    @AfterAll
    static void stop() {
        s3.close();
        seaweed.stop();
    }

    @Test
    void receiptPhotosGoAfterTheRetentionAndBackupsStay() {
        UUID household = UUID.randomUUID();
        String receipt = AttachmentKind.RECEIPT.objectKey(household, UUID.randomUUID());
        String backup = AttachmentKind.BACKUP.objectKey(household, UUID.randomUUID());
        s3.putObject(b -> b.bucket(BUCKET).key(receipt), RequestBody.fromString("sealed"));
        s3.putObject(b -> b.bucket(BUCKET).key(backup), RequestBody.fromString("sealed"));

        assertThat(expiryAt(Instant.now().plus(Duration.ofDays(29))).expire()).isZero();
        assertThat(expiryAt(Instant.now().plus(Duration.ofDays(31))).expire()).isEqualTo(1);

        assertThat(s3.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build()).contents())
                .extracting(S3Object::key).containsExactly(backup);
    }

    private static ReceiptPhotoExpiry expiryAt(Instant now) {
        StorageProperties storage = new StorageProperties(BUCKET, null, null, null, null, null, true, false, Duration.ofMinutes(15), Duration.ofDays(30));
        return new ReceiptPhotoExpiry(s3, storage, Clock.fixed(now, ZoneOffset.UTC), (JobLock) null);
    }
}
