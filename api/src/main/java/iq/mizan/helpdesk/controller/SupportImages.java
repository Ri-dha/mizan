package iq.mizan.helpdesk.controller;

import java.time.Duration;

import iq.mizan.helpdesk.service.SupportReportService;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class SupportImages {

    private SupportImages() {
    }

    /** The bytes were checked on upload; the sandbox keeps a crafted file from ever running as a page. */
    static ResponseEntity<byte[]> serve(SupportReportService.Image image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
