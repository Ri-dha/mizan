package iq.mizan.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.entity.RefreshToken;
import iq.mizan.auth.repository.RefreshTokenRepository;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.common.security.HashingService;
import iq.mizan.common.security.JwtProperties;
import iq.mizan.common.security.JwtService;
import iq.mizan.household.service.HouseholdSummary;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class TokenIssuer {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final HashingService hashingService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final SecureRandom random = new SecureRandom();

    public TokenPair issue(AppUser user, HouseholdSummary household, String userAgent, String ipAddress) {
        CurrentUser principal = new CurrentUser(user.getId(), household.id(), household.permissions());
        String accessToken = jwtService.issueAccessToken(principal);

        String refreshToken = generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                hashingService.hash(refreshToken),
                jwtProperties.refreshTokenTtl(),
                userAgent,
                ipAddress));

        return new TokenPair(accessToken, refreshToken,
                Instant.now().plus(jwtProperties.accessTokenTtl()));
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
