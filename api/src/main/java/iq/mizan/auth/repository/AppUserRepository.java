package iq.mizan.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.entity.PlatformRole;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByPhone(String phone);

    List<AppUser> findByPlatformRoleOrderByDisplayName(PlatformRole role);

    long countByPlatformRole(PlatformRole role);

    List<AppUser> findTop20ByEmailStartingWithIgnoreCaseOrPhoneStartingWithOrDisplayNameContainingIgnoreCaseOrderByCreatedAtDesc(
            String email, String phone, String displayName);
}
