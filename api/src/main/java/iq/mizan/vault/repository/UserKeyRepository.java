package iq.mizan.vault.repository;

import java.util.UUID;

import iq.mizan.vault.entity.UserKey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserKeyRepository extends JpaRepository<UserKey, UUID> {
}
