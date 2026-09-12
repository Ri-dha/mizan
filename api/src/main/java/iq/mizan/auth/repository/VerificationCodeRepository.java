package iq.mizan.auth.repository;

import java.util.Optional;
import java.util.UUID;

import iq.mizan.auth.entity.CodePurpose;
import iq.mizan.auth.entity.VerificationCode;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(
            UUID userId, CodePurpose purpose);
}
