package iq.mizan.auth.controller;

import iq.mizan.auth.dto.LoginRequest;
import iq.mizan.auth.dto.PasswordResetConfirmRequest;
import iq.mizan.auth.dto.PasswordResetRequest;
import iq.mizan.auth.dto.RefreshRequest;
import iq.mizan.auth.dto.RegisterRequest;
import iq.mizan.auth.dto.TokenResponse;
import iq.mizan.auth.dto.VerifyRequest;
import iq.mizan.auth.service.AuthenticationService;
import iq.mizan.auth.service.PasswordResetService;
import iq.mizan.auth.service.RegistrationService;
import iq.mizan.auth.service.TokenPair;
import iq.mizan.auth.service.VerificationService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@AllArgsConstructor
@Tag(name = "Authentication", description = "Registration, sign-in, token refresh and password reset")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final VerificationService verificationService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenCookie refreshCookie;

    @Operation(summary = "Register with an email or phone number")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        TokenPair pair = registrationService.register(
                request.identifier(), request.password(), request.displayName(),
                request.localeOrDefault(), ClientRequest.userAgent(httpRequest), ClientRequest.ip(httpRequest));
        return withRefreshCookie(httpRequest, pair, HttpStatus.CREATED);
    }

    @Operation(summary = "Sign in")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        TokenPair pair = authenticationService.login(
                request.identifier(), request.password(),
                ClientRequest.userAgent(httpRequest), ClientRequest.ip(httpRequest));
        return withRefreshCookie(httpRequest, pair, HttpStatus.OK);
    }

    @Operation(summary = "Exchange a refresh token for a new pair",
            description = "Replaying an already-rotated token revokes every session for the account.")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest) {
        TokenPair pair = authenticationService.refresh(
                presentedToken(httpRequest, request),
                ClientRequest.userAgent(httpRequest), ClientRequest.ip(httpRequest));
        return withRefreshCookie(httpRequest, pair, HttpStatus.OK);
    }

    @Operation(summary = "Sign out", description = "Revokes the refresh token; always 204.")
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest) {
        refreshCookie.from(httpRequest, request == null ? null : request.refreshToken())
                .ifPresent(authenticationService::logout);
        HttpHeaders headers = new HttpHeaders();
        refreshCookie.clear(headers, refreshCookie.isSecure(httpRequest));
        return ResponseEntity.noContent().headers(headers).build();
    }

    @Operation(summary = "Confirm the email or phone with the code that was sent")
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody VerifyRequest request, HttpServletRequest httpRequest) {
        verificationService.confirmContact(user.userId(), request.code(), ClientRequest.ip(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send a fresh verification code")
    @PostMapping("/verify/resend")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal CurrentUser user) {
        verificationService.resendContactVerification(user.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request a password reset code", description = "Always 204, whether or not the account exists.")
    @SecurityRequirements
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.request(request.identifier());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set a new password with the reset code", description = "Ends every existing session.")
    @SecurityRequirements
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request, HttpServletRequest httpRequest) {
        passwordResetService.confirm(
                request.identifier(), request.code(), request.newPassword(), ClientRequest.ip(httpRequest));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(
            HttpServletRequest httpRequest, TokenPair pair, HttpStatus status) {
        HttpHeaders headers = new HttpHeaders();
        refreshCookie.set(headers, pair.refreshToken(), refreshCookie.isSecure(httpRequest));
        return ResponseEntity.status(status).headers(headers).body(TokenResponse.from(pair));
    }

    private String presentedToken(HttpServletRequest httpRequest, RefreshRequest request) {
        return refreshCookie.from(httpRequest, request == null ? null : request.refreshToken())
                .orElseThrow(() -> ApiException.of(
                        ErrorCode.INVALID_REFRESH_TOKEN, "No refresh token was presented"));
    }
}
