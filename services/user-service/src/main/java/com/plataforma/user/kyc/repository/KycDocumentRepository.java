package com.plataforma.user.kyc.repository;

import com.plataforma.user.kyc.model.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {

    List<KycDocument> findByUserIdOrderByUploadedAtDesc(Long userId);
}
