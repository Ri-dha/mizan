package iq.mizan.auth.repository;

import java.util.Optional;
import java.util.UUID;

import iq.mizan.auth.entity.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByPhone(String phone);
}
