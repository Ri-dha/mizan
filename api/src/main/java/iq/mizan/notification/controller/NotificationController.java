package iq.mizan.notification.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.notification.dto.SubscribeRequest;
import iq.mizan.notification.dto.SubscriptionResponse;
import iq.mizan.notification.dto.UnsubscribeRequest;
import iq.mizan.notification.dto.VapidKeyResponse;
import iq.mizan.notification.push.WebPushSender;
import iq.mizan.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@AllArgsConstructor
@Tag(name = "Notifications", description = "Web Push subscriptions for reminders")
public class NotificationController {

    private final NotificationService notificationService;
    private final Optional<WebPushSender> webPush;

    @Operation(summary = "The server's VAPID public key, needed to subscribe a browser")
    @GetMapping("/vapid-key")
    public ResponseEntity<VapidKeyResponse> vapidKey() {
        return ResponseEntity.ok(new VapidKeyResponse(webPush.map(WebPushSender::publicKey).orElse("")));
    }

    @Operation(summary = "Register this browser's push subscription (FR-NTF-01)")
    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionResponse> subscribe(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SubscribeRequest request,
                                                          @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.subscribe(user, request, userAgent));
    }

    @Operation(summary = "This account's subscribed devices in the current household")
    @GetMapping("/subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> mine(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(notificationService.mine(user));
    }

    @Operation(summary = "Remove a subscription")
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UnsubscribeRequest request) {
        notificationService.unsubscribe(user, request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send a test notification to every subscribed device of the caller")
    @PostMapping("/test")
    public ResponseEntity<Map<String, Integer>> test(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(Map.of("sent", notificationService.sendTest(user)));
    }
}
