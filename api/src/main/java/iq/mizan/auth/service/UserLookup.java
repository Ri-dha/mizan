package iq.mizan.auth.service;

import java.util.Optional;

import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.repository.AppUserRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UserLookup {

    private final AppUserRepository userRepository;

    public Optional<AppUser> byIdentifier(Identifier identifier) {
        return userRepository.findByEmailIgnoreCase(identifier.email());
    }
}
