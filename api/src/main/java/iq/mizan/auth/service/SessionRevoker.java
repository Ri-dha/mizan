package iq.mizan.auth.service;

import java.time.Instant;
import java.util.UUID;

import iq.mizan.auth.repository.RefreshTokenRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Its own transaction, so a revocation survives the 401 the caller goes on to throw. */
@Service
@AllArgsConstructor
public class SessionRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllSessions(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }
}
