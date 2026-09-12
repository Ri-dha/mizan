package iq.mizan.common.storage;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@Component
@AllArgsConstructor
public class BucketBootstrap {

    private static final Logger log = LoggerFactory.getLogger(BucketBootstrap.class);

    private final S3Client s3Client;
    private final StorageProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketExists() {
        if (!properties.createBucketOnStartup()) {
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            log.info("Created storage bucket {}", properties.bucket());
        } catch (RuntimeException e) {
            // Storage being down must not stop the API: attachments are a "should" feature
            // and everything else works without them (NFR-02).
            log.warn("Object storage unreachable at {}: {}", properties.endpoint(), e.getMessage());
        }
    }
}
