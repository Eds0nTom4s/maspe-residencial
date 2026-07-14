package com.restaurante.repository;

import com.restaurante.model.entity.BusinessProvisioningPreview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface BusinessProvisioningPreviewRepository extends JpaRepository<BusinessProvisioningPreview, Long> {
    Optional<BusinessProvisioningPreview> findByPreviewId(String previewId);
    Optional<BusinessProvisioningPreview> findByBusinessAccountIdAndIdempotencyKey(Long accountId, String key);
    Optional<BusinessProvisioningPreview> findFirstByBusinessAccountIdAndRequestFingerprintAndExpiresAtAfterOrderByIdDesc(
            Long accountId, String fingerprint, LocalDateTime now);
}
