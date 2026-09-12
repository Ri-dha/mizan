package iq.mizan.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserAccessor {

    public Optional<CurrentUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public CurrentUser require() {
        return current().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
