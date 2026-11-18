package iq.mizan.auth.service;

import java.util.UUID;

import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.common.security.PasswordVerifier;

import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AppUserPasswordVerifier implements PasswordVerifier {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matches(UUID userId, String authKey) {
        return userRepository.findById(userId)
                .map(user -> passwordEncoder.matches(authKey, user.getPasswordHash()))
                .orElse(false);
    }
}
