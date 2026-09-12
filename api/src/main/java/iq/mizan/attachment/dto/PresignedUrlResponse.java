package iq.mizan.attachment.dto;

import java.time.Instant;

public record PresignedUrlResponse(String url, String method, Instant expiresAt) {
}
