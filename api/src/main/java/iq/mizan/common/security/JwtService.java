package iq.mizan.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String HOUSEHOLD_CLAIM = "hh";
    private static final String PERMISSIONS_CLAIM = "perms";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "mizan.security.jwt.secret must be at least 32 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccessToken(CurrentUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.userId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .claim(HOUSEHOLD_CLAIM, user.householdId().toString())
                .claim(PERMISSIONS_CLAIM, user.permissions().stream().map(Enum::name).toList())
                .signWith(signingKey)
                .compact();
    }

    /** @throws JwtException when the signature, issuer or expiry is not acceptable */
    public CurrentUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        List<?> raw = claims.get(PERMISSIONS_CLAIM, List.class);
        Set<Permission> permissions = raw == null ? Set.of() : raw.stream()
                .map(String::valueOf)
                .map(JwtService::toPermissionOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());

        return new CurrentUser(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get(HOUSEHOLD_CLAIM, String.class)),
                permissions);
    }

    // A permission removed by a deploy is dropped rather than failing every live token.
    private static Permission toPermissionOrNull(String name) {
        try {
            return Permission.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
